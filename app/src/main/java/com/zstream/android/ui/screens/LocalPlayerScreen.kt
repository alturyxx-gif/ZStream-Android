package com.zstream.android.ui.screens

import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.zstream.android.data.LocalMediaRepository
import com.zstream.android.data.LocalFileProgressRepository
import com.zstream.android.data.buildLocalFileProgressId
import com.zstream.android.data.local.dao.CachedEpisodeDao
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.download.DownloadStorage
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant
import com.zstream.android.ui.navigation.rememberSafeNavigateBack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class LocalPlaybackSource {
    data class Ready(
        val title: String,
        // Bare show/movie title with no "SxxEyy" suffix -- unlike [title] (which is for on-screen
        // display), this is what gets persisted to progress so Continue Watching on the home
        // screen shows the same title it would for online playback of the same show.
        val showTitle: String,
        val episodeLabel: String?,
        val videoUri: android.net.Uri,
        val subtitles: List<Pair<String, android.net.Uri>>,
        val fileName: String,
        val relativePath: String?,
        val size: Long?,
        val durationMs: Long?,
        val matchSource: String?,
        val tmdbId: String?,
        val tmdbType: String?,
        val posterPath: String?,
        val thumbnailPath: String?,
        val season: Int?,
        val episode: Int?,
        val localFileId: String?,
    ) : LocalPlaybackSource()
    object NotFound : LocalPlaybackSource()
}

@HiltViewModel
class LocalPlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val downloadDao: DownloadDao,
    private val storage: DownloadStorage,
    private val localMediaRepository: LocalMediaRepository,
    private val settingsPrefs: SettingsPreferences,
    private val progressRepository: com.zstream.android.data.ProgressRepository,
    private val localFileProgressRepository: LocalFileProgressRepository,
    private val skipSegmentRepository: com.zstream.android.data.SkipSegmentRepository,
    private val cachedEpisodeDao: CachedEpisodeDao,
) : ViewModel() {
    private val downloadId: Long? = savedState.get<String>("downloadId")?.toLongOrNull()
    private val localMediaId: Long? = savedState.get<String>("localMediaId")?.toLongOrNull()

    private val _source = MutableStateFlow<LocalPlaybackSource?>(null)
    val source: StateFlow<LocalPlaybackSource?> = _source.asStateFlow()
    private val _resumeWatchedSec = MutableStateFlow<Long?>(null)
    val resumeWatchedSec: StateFlow<Long?> = _resumeWatchedSec.asStateFlow()
    private val _skipSegments = MutableStateFlow<List<SkipSegment>>(emptyList())
    val skipSegments: StateFlow<List<SkipSegment>> = _skipSegments.asStateFlow()
    private var skipSegmentsCacheKey: String? = null
    val settings = settingsPrefs.settings

    /** Offline episode picker for the in-player Episodes/Seasons menu, built from locally cached
     * TMDB metadata (same cache DetailViewModel warms) rather than a live network call — the whole
     * point of this screen is playing content that's already on disk with no connectivity assumed. */
    private var offlineShowTmdbId: String? = null
    private val _tvDetail = MutableStateFlow<com.zstream.android.data.model.TvDetail?>(null)
    val tvDetail: StateFlow<com.zstream.android.data.model.TvDetail?> = _tvDetail.asStateFlow()
    private val _currentSeasonDetail = MutableStateFlow<com.zstream.android.data.model.Season?>(null)
    val currentSeasonDetail: StateFlow<com.zstream.android.data.model.Season?> = _currentSeasonDetail.asStateFlow()
    private val _downloadedEpisodes = MutableStateFlow<Map<String, DownloadEntity>>(emptyMap())
    val downloadedEpisodes: StateFlow<Map<String, DownloadEntity>> = _downloadedEpisodes.asStateFlow()

    init {
        viewModelScope.launch {
            val (resumeSec, loaded) = withContext(Dispatchers.IO) {
                val l = loadDownload() ?: loadLocalMedia() ?: LocalPlaybackSource.NotFound
                val r = (l as? LocalPlaybackSource.Ready)?.let { loadExistingProgress(it) }
                r to l
            }
            _resumeWatchedSec.value = resumeSec
            _source.value = loaded
            val ready = loaded as? LocalPlaybackSource.Ready
            if (ready?.tmdbId != null && ready.tmdbType == "show") {
                observeDownloadedEpisodes(ready.tmdbId)
                loadOfflineShowMetadata(ready.tmdbId, ready.showTitle, ready.posterPath)
                loadSeason(ready.season ?: 1)
            }
        }
    }

    private fun observeDownloadedEpisodes(tmdbId: String) {
        viewModelScope.launch {
            downloadDao.observeCompleted()
                .map { list -> list.filter { it.tmdbId == tmdbId }.associateBy { "${it.season}|${it.episode}" } }
                .collect { _downloadedEpisodes.value = it }
        }
    }

    private suspend fun loadOfflineShowMetadata(tmdbId: String, showTitle: String, posterPath: String?) {
        offlineShowTmdbId = tmdbId
        val seasonNumbers = withContext(Dispatchers.IO) { cachedEpisodeDao.getAvailableSeasonsSync(tmdbId) }
        if (seasonNumbers.isEmpty()) return
        val seasons = seasonNumbers.sorted().map { num ->
            com.zstream.android.data.model.Season(id = 0, seasonNumber = num, name = "Season $num", episodeCount = null, posterPath = null, episodes = null)
        }
        _tvDetail.value = com.zstream.android.data.model.TvDetail(
            id = tmdbId.toIntOrNull() ?: 0, name = showTitle, overview = null, posterPath = posterPath, backdropPath = null,
            firstAirDate = null, voteAverage = null, numberOfSeasons = seasons.size, seasons = seasons, genres = null, credits = null,
        )
    }

    fun loadSeason(number: Int) {
        val tmdbId = offlineShowTmdbId ?: return
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { cachedEpisodeDao.getSeasonSync(tmdbId, number) }
            _currentSeasonDetail.value = if (cached.isEmpty()) null else com.zstream.android.data.model.Season(
                id = 0,
                seasonNumber = number,
                name = "Season $number",
                episodeCount = cached.size,
                posterPath = null,
                episodes = cached.map { entry ->
                    com.zstream.android.data.model.Episode(
                        id = entry.episodeId,
                        name = entry.title,
                        episodeNumber = entry.episode,
                        seasonNumber = entry.season,
                        stillPath = entry.stillPath,
                        overview = entry.overview,
                        airDate = entry.airDate,
                    )
                },
            )
        }
    }


    private suspend fun loadExistingProgress(ready: LocalPlaybackSource.Ready): Long? {
        return if (ready.tmdbId != null) {
            val id = com.zstream.android.data.local.entity.ProgressEntity.computeId(ready.tmdbId, ready.season, ready.episode)
            progressRepository.getProgressById(id)?.watched?.toLong()
        } else if (ready.localFileId != null) {
            localFileProgressRepository.get(ready.localFileId)?.watched?.toLong()
        } else {
            null
        }
    }

    fun saveProgress(ready: LocalPlaybackSource.Ready, watchedSec: Long, durationSec: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (ready.tmdbId != null) {
                    progressRepository.updateProgress(
                        tmdbId = ready.tmdbId,
                        title = ready.showTitle,
                        type = ready.tmdbType ?: "movie",
                        watched = watchedSec.toInt(),
                        duration = durationSec.toInt(),
                        posterPath = ready.posterPath,
                        episodeNumber = ready.episode,
                        seasonNumber = ready.season,
                    )
                } else if (ready.localFileId != null) {
                    localFileProgressRepository.update(
                        id = ready.localFileId,
                        title = ready.title,
                        watched = watchedSec.toInt(),
                        duration = durationSec.toInt(),
                        posterPath = ready.posterPath,
                        thumbnailPath = ready.thumbnailPath,
                    )
                }
            }
        }
    }

    fun loadSkipSegments(ready: LocalPlaybackSource.Ready, durationMs: Long) {
        val tmdbId = ready.tmdbId ?: return
        val mediaType = if (ready.tmdbType == "show") "tv" else ready.tmdbType ?: "movie"
        val cacheKey = skipSegmentRepository.buildMediaKey(tmdbId, mediaType, ready.season, ready.episode) ?: return
        if (skipSegmentsCacheKey == cacheKey) return

        viewModelScope.launch {
            val settingsValue = settingsPrefs.settings.first()
            val resolvedSegments = skipSegmentRepository.getSegments(
                tmdbId = tmdbId,
                mediaType = mediaType,
                season = ready.season,
                episode = ready.episode,
                durationMs = durationMs,
                tidbKey = settingsValue.tidbKey,
                febboxKey = settingsValue.febboxKey,
            )
            skipSegmentsCacheKey = cacheKey
            _skipSegments.value = resolvedSegments
        }
    }

    private suspend fun loadDownload(): LocalPlaybackSource.Ready? {
        val id = downloadId ?: return null
        val entity = downloadDao.getById(id)
        val filePath = entity?.filePath
        val videoUri = filePath?.let { storage.resolvePlayableUri(it) } ?: return null
        val subtitles = entity.subtitlePaths.orEmpty().mapNotNull { path ->
            storage.resolvePlayableUri(path)?.let { uri -> path to uri }
        }
        val title = if (entity.type == "show") {
            "${entity.title} S${entity.season.toString().padStart(2, '0')}E${entity.episode.toString().padStart(2, '0')}"
        } else {
            entity.title
        }
        return LocalPlaybackSource.Ready(
            title = title,
            showTitle = entity.title,
            episodeLabel = entity.episodeTitle,
            videoUri = videoUri,
            subtitles = subtitles,
            fileName = filePath.substringAfterLast('/'),
            relativePath = filePath,
            size = null,
            durationMs = null,
            matchSource = "database",
            tmdbId = entity.tmdbId,
            tmdbType = entity.type,
            posterPath = entity.posterPath,
            thumbnailPath = null,
            season = entity.season,
            episode = entity.episode,
            localFileId = null,
        )
    }

    private suspend fun loadLocalMedia(): LocalPlaybackSource.Ready? {
        val media = localMediaRepository.getMedia(localMediaId ?: return null) ?: return null
        val title = if (media.mediaKind == "show") {
            "${media.groupTitle} S${media.season.toString().padStart(2, '0')}E${media.episode.toString().padStart(2, '0')}"
        } else {
            media.groupTitle
        }
        return LocalPlaybackSource.Ready(
            title = title,
            showTitle = media.groupTitle,
            episodeLabel = null,
            videoUri = android.net.Uri.parse(media.documentUri),
            subtitles = localMediaRepository.siblingSubtitles(media),
            fileName = media.displayName,
            relativePath = media.relativePath,
            size = media.size,
            durationMs = media.durationMs,
            matchSource = media.matchSource,
            tmdbId = media.tmdbId,
            tmdbType = media.tmdbType,
            posterPath = media.posterPath,
            thumbnailPath = media.thumbnailPath,
            season = media.season,
            episode = media.episode,
            localFileId = if (media.tmdbId == null) {
                buildLocalFileProgressId(media.fingerprint, media.folderId, media.documentUri)
            } else null,
        )
    }

    fun updateSettings(settings: com.zstream.android.data.local.entity.SettingsEntity) {
        viewModelScope.launch { settingsPrefs.updateSettings(settings) }
    }
}

private fun subtitleMimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "vtt" -> MimeTypes.TEXT_VTT
    else -> MimeTypes.APPLICATION_SUBRIP
}

/** Best-effort language tag guess from "...{lang}.srt" style filenames our own downloader writes. */
private fun subtitleLanguageTag(path: String): String {
    val name = path.substringAfterLast('/').substringBeforeLast('.')
    return name.substringAfterLast('.', "").ifBlank { "und" }
}

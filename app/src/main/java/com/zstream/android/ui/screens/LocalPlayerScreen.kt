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
import com.zstream.android.data.local.dao.DownloadDao
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

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val loaded = loadDownload() ?: loadLocalMedia() ?: LocalPlaybackSource.NotFound
                val resumeSec = (loaded as? LocalPlaybackSource.Ready)?.let { loadExistingProgress(it) }
                _resumeWatchedSec.value = resumeSec
                _source.value = loaded
            }
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

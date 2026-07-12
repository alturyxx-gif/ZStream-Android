package com.zstream.android.ui.screens

import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import com.zstream.android.data.BookmarkRepository
import com.zstream.android.data.TmdbRepository
import com.zstream.android.plugin.PluginManager
import com.zstream.android.plugin.SourceInfo
import com.zstream.android.plugin.SourceOrderStore
import com.zstream.android.plugin.Caption
import com.zstream.android.plugin.MediaRequest as PluginMediaRequest
import com.zstream.android.plugin.StreamResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.UUID
import javax.inject.Inject
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.data.local.entity.SettingsEntity
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.dao.LocalLibraryDao
import com.zstream.android.data.local.entity.DownloadStatus
import com.zstream.android.data.model.airedEpisodes
import com.google.gson.Gson
import com.zstream.android.data.WatchPartyManager
import com.zstream.android.data.WatchPartyAction
import com.zstream.android.data.remote.WatchPartyContentDto
import com.zstream.android.data.remote.WatchPartyPlayerDto
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job

enum class SourceStatus { IDLE, TRYING, SUCCESS, FAILED }
data class SourceResult(val id: String, val status: SourceStatus, val codec: String = "")
data class ResolvedSourceCandidate(
    val streamUrl: String,
    val streamType: String,
    val headers: Map<String, String>,
    val subtitles: List<SubtitleTrack>,
    val sourceId: String,
    val variants: List<StreamVariant> = emptyList(),
)
data class PlaybackFailure(
    val message: String,
    val details: String,
    val title: String = "Source error",
)

private fun playbackFailureDetails(message: String): String = Throwable(message).stackTraceToString()

private fun clearTryingStatuses(sources: List<SourceResult>): List<SourceResult> =
    sources.map { if (it.status == SourceStatus.TRYING) it.copy(status = SourceStatus.IDLE) else it }

data class SkipSegment(val type: String, val startMs: Long?, val endMs: Long?)
data class SkipSegmentSubmission(
    val tmdbId: Int,
    val type: String,
    val segment: String,
    val season: Int? = null,
    val episode: Int? = null,
    val startSec: Double? = null,
    val endSec: Double? = null,
    val videoDurationMs: Long? = null,
)

sealed class PlayerState {
    object Idle : PlayerState()
    data class LocalChoice(val route: String) : PlayerState()
    data class Scraping(val sources: List<SourceResult>) : PlayerState()
    data class ManualSourceSelection(
        val sources: List<SourceResult>,
        val candidates: Map<String, ResolvedSourceCandidate> = emptyMap(),
        val message: String? = null,
    ) : PlayerState()
    data class Ready(
        val streamUrl: String,
        val streamType: String,
        val headers: Map<String, String>,
        val subtitles: List<SubtitleTrack>,
        val sources: List<SourceResult>,
        val sourceId: String? = null,
        val variants: List<StreamVariant> = emptyList(),
        val failedVariantUrls: Set<String> = emptySet(),
        val candidates: Map<String, ResolvedSourceCandidate> = emptyMap(),
        val playbackFailure: PlaybackFailure? = null,
    ) : PlayerState()
    data class Error(val message: String, val sources: List<SourceResult>) : PlayerState()
}

data class SubtitleTrack(
    val label: String,
    val url: String,
    val language: String,
    val type: String = "vtt",
    val id: String = url,
    val source: String? = null,
    val hearingImpaired: Boolean = false,
    val external: Boolean = false,
)

data class StreamVariant(
    val id: String,
    val name: String,
    val quality: String,
    val codec: String,
    val tag: String,
    val streamUrl: String,
    val streamType: String = "hls",
    val headers: Map<String, String> = emptyMap(),
    val requiresRefreshOnSwitch: Boolean = false,
) {
    /** Label shown in the variant picker: e.g. "4K · HEVC · HDR", falls back to API name */
    fun displayLabel(): String {
        val parts = mutableListOf<String>()
        if (quality.isNotBlank()) parts += quality
        when (codec.lowercase()) {
            "hevc", "h265" -> parts += "HEVC"
            "h264", "avc"  -> parts += "H.264"
            else -> if (codec.isNotBlank()) parts += codec
        }
        when (tag.lowercase()) {
            "hdr"   -> parts += "HDR"
            "dv"    -> parts += "Dolby Vision"
            "remux" -> parts += "REMUX"
            "bw"    -> parts += "B&W"
        }
        val derived = parts.joinToString(" · ")
        // Fall back to the raw name if we can't derive a meaningful label
        return derived.ifBlank { name.ifBlank { id } }
    }
}

/** One resolution choice discovered inside an HLS master playlist, for the download quality picker. */
data class DownloadQualityOption(
    val label: String,
    val streamUrl: String,
    val bandwidth: Long,
    val audioOptions: List<com.zstream.android.download.HlsAudioRendition> = emptyList(),
)

internal fun preferredInitialVariantUrl(
    sourceId: String,
    defaultUrl: String,
    variants: List<StreamVariant>,
): String = defaultUrl

internal fun nextUnfailedVariantUrl(
    currentUrl: String,
    variants: List<StreamVariant>,
    failedUrls: Set<String>,
): String? = variants.firstOrNull { it.streamUrl != currentUrl && it.streamUrl !in failedUrls }?.streamUrl

private fun logVariantSelection(defaultUrl: String, selectedUrl: String, variants: List<StreamVariant>) {
    Log.d("VariantDebug", buildString {
        appendLine("defaultVariantMatched=${variants.any { it.streamUrl == defaultUrl }}")
        appendLine("selectedVariantMatched=${variants.any { it.streamUrl == selectedUrl }}")
        variants.forEach {
            appendLine("variant id=${it.id} name=${it.name} quality=${it.quality} codec=${it.codec} tag=${it.tag}")
        }
    })
}

private data class WyzieSubtitleEntry(
    val url: String? = null,
    val language: String? = null,
    val display: String? = null,
    val format: String? = null,
    val id: String? = null,
    val source: String? = null,
    val isHearingImpaired: Boolean? = null,
)

internal fun parseWyzieSubtitles(body: String): List<SubtitleTrack> {
    return Gson().fromJson(body, Array<WyzieSubtitleEntry>::class.java).mapNotNull { entry ->
        val url = entry.url?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val rawLanguage = entry.language?.takeIf(String::isNotBlank) ?: "Unknown"
        val language = normalizeLanguageCode(rawLanguage)
        SubtitleTrack(
            label = entry.display?.takeIf(String::isNotBlank) ?: rawLanguage,
            url = url,
            language = language,
            type = entry.format?.takeIf(String::isNotBlank) ?: "srt",
            id = entry.id?.takeIf(String::isNotBlank) ?: url,
            source = "wyzie${entry.source?.takeIf(String::isNotBlank)?.let { " ${if (it == "opensubtitles") "opensubs" else it}" }.orEmpty()}",
            hearingImpaired = entry.isHearingImpaired == true,
            external = true,
        )
    }
}

internal fun languageCodeFromLabel(label: String): String? {
    val name = label.replace(Regex("\\s*Hi\\d*$", RegexOption.IGNORE_CASE), "").replace(Regex("\\d+$"), "").trim()
    return java.util.Locale.getISOLanguages().firstOrNull {
        java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.ENGLISH).equals(name, ignoreCase = true)
    }
}

internal fun normalizeLanguageCode(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.length == 2 && java.util.Locale.getISOLanguages().contains(trimmed)) return trimmed
    return languageCodeFromLabel(trimmed) ?: trimmed
}

internal fun subtitleCandidates(tracks: List<SubtitleTrack>, language: String): List<SubtitleTrack> =
    tracks.filter { it.language == language }

data class PauseMetadata(
    val title: String,
    val overview: String?,
    val year: String?,
    val rating: String?,
    val runtime: String?,
    val logoUrl: String?,
    val posterUrl: String?,
    val type: String, // movie/tv
    val mediaLabel: String?,
    val genres: List<String> = emptyList(),
)

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

internal fun vttCueBlocks(body: String): List<String> =
    body.replace(Regex("(\n)(?=\\d{1,2}:\\d{2}(?::\\d{2})?\\.\\d{1,3}\\s*-->)"), "\n\n")
        .split(Regex("\n\\s*\n"))

data class AutoplayEpisodeTarget(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val seasonId: String,
    val episodeId: String,
)

@HiltViewModel
class PlayerViewModel @OptIn(UnstableApi::class)
@Inject constructor(
    private val pluginManager: PluginManager,
    private val sourceOrderStore: SourceOrderStore,
    private val settingsPrefs: SettingsPreferences,
    private val auroraKeyManager: com.zstream.android.data.AuroraKeyManager,
    private val bookmarkRepo: BookmarkRepository,
    private val traktRepository: com.zstream.android.data.TraktRepository,
    private val tmdbRepo: TmdbRepository,
    private val httpClient: OkHttpClient,
    val playerCache: SimpleCache,
    val watchPartyManager: WatchPartyManager,
    private val downloadRepository: com.zstream.android.download.DownloadRepository,
    private val downloadDao: DownloadDao,
    private val localLibraryDao: LocalLibraryDao,
    private val skipSegmentRepository: com.zstream.android.data.SkipSegmentRepository,
    private val tvSyncRepository: com.zstream.android.data.TvSyncRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    savedState: SavedStateHandle,
) : ViewModel() {
    val settings = settingsPrefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsEntity())

    private val id = savedState.get<Int>("id") ?: 0
    val mediaType = savedState.get<String>("mediaType") ?: "movie"
    var season = savedState.get<Int>("season").takeIf { it != -1 }
        private set
    var episode = savedState.get<Int>("episode").takeIf { it != -1 }
        private set
    val title = savedState.get<String>("title")?.decodeRouteParam().orEmpty()
    val year = savedState.get<Int>("year") ?: 0
    val poster = savedState.get<String>("poster")?.decodeRouteParam()?.takeIf { it.isNotBlank() }
    val tmdbId = id.toString()
    var seasonId = savedState.get<String>("seasonId")
        private set
    var episodeId = savedState.get<String>("episodeId")
        private set
    val isAutoplay = savedState.get<Boolean>("autoplay") ?: false

    // Populated only when this player was opened via an incoming TV cast command (see
    // TvCastGlobalEffect / NavGraph) -- tells load() to resolve this exact source/variant directly
    // instead of trying every source in order, so a cast TV starts playing immediately.
    private val castSourceId = savedState.get<String>("castSourceId")?.takeIf { it.isNotBlank() }
    private val castVariantId = savedState.get<String>("castVariantId")?.takeIf { it.isNotBlank() }
    /** Progress (seconds) to resume at, from the casting phone. Non-null only for a cast launch. */
    val castResumeSec = savedState.get<Long>("castProgressSec")?.takeIf { castSourceId != null && it > 0 }

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state = _state.asStateFlow()

    private val _subtitleCues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    val subtitleCues = _subtitleCues.asStateFlow()

    // Written from Dispatchers.IO by potentially concurrent parse coroutines (multiple
    // downloadAndParseSubtitles() calls can be in flight at once) -- must be thread-safe.
    private val subtitleCache = java.util.concurrent.ConcurrentHashMap<String, List<SubtitleCue>>()

    private val _selectedSubtitleLang = MutableStateFlow<String?>(null)
    val selectedSubtitleLang = _selectedSubtitleLang.asStateFlow()
    private val _selectedSubtitleId = MutableStateFlow<String?>(null)
    val selectedSubtitleId = _selectedSubtitleId.asStateFlow()
    private val _subtitleDelay = MutableStateFlow(0f)
    val subtitleDelay = _subtitleDelay.asStateFlow()
    private val _overrideCasing = MutableStateFlow(false)
    val overrideCasing = _overrideCasing.asStateFlow()

    private val _skipSegments = MutableStateFlow<List<SkipSegment>>(emptyList())
    val skipSegments = _skipSegments.asStateFlow()

    val isBookmarked = bookmarkRepo.observeBookmark(tmdbId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _tvDetail = MutableStateFlow<com.zstream.android.data.model.TvDetail?>(null)
    val tvDetail = _tvDetail.asStateFlow()

    private val _currentSeasonDetail = MutableStateFlow<com.zstream.android.data.model.Season?>(null)
    val currentSeasonDetail = _currentSeasonDetail.asStateFlow()
    private val _movieDetail = MutableStateFlow<com.zstream.android.data.model.MovieDetail?>(null)
    val movieDetail = _movieDetail.asStateFlow()

    val pauseMetadata = combine(
        _movieDetail,
        _tvDetail,
        settings
    ) { movie, tv, s ->
        if (!s.enablePauseOverlay) return@combine null

        if (mediaType == "movie") {
            PauseMetadata(
                title = movie?.title ?: title,
                overview = movie?.overview,
                year = movie?.releaseDate?.take(4) ?: year.toString(),
                rating = movie?.voteAverage?.let { "%.1f".format(it) },
                runtime = movie?.runtime?.let { "$it min" },
                logoUrl = movie?.logoUrl(),
                posterUrl = movie?.posterUrl(),
                type = "movie",
                mediaLabel = null,
                genres = movie?.genres.orEmpty().map { it.name },
            )
        } else {
            PauseMetadata(
                title = tv?.name ?: title,
                overview = tv?.overview,
                year = tv?.firstAirDate?.take(4) ?: year.toString(),
                rating = tv?.voteAverage?.let { "%.1f".format(it) },
                runtime = null, // TV detail doesn't directly expose average episode runtime in a simple way
                logoUrl = tv?.logoUrl(),
                posterUrl = tv?.posterUrl(),
                type = "tv",
                mediaLabel = buildList<String> {
                    season?.let { add("S$it") }
                    episode?.let { add("E$it") }
                }.takeIf { it.isNotEmpty() }?.joinToString(" • "),
                genres = tv?.genres.orEmpty().map { it.name },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val sources = mutableListOf<SourceResult>()
    private val probeJobs = mutableMapOf<String, Job>()
    private var cachedDisplaySources: List<SourceInfo> = emptyList()
    private val failedVariantUrls = mutableSetOf<String>()
    private val failedPlaybackSourceIds = mutableSetOf<String>()
    private var skipSegmentsCacheKey: String? = null
    // Desired variant name to restore after a 403-triggered re-resolve
    private var desiredVariantName: String? = null

    // --- Watch Party Actions ---
    private val _watchPartyEvent = MutableSharedFlow<WatchPartyAction>(replay = 0)
    val watchPartyEvent = _watchPartyEvent.asSharedFlow()
    private val _recoveryNotice = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val recoveryNotice = _recoveryNotice.asSharedFlow()
    private var awaitingRecoveryPlayback = false
    private val _bandwidthNotice = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val bandwidthNotice = _bandwidthNotice.asSharedFlow()
    private val _bandwidthAlert = MutableStateFlow<String?>(null)
    val bandwidthAlert: StateFlow<String?> = _bandwidthAlert.asStateFlow()
    private var bandwidthWatchJob: Job? = null
    private var bandwidthWatchKey: String? = null
    private val notifiedBandwidthThresholds = mutableSetOf<Int>()
    private var exhaustionHandledForKey: String? = null
    private val localStateOwner = UUID.randomUUID().toString()
    private var lastPlaybackPosition = 0L
    private var lastPlaybackDuration = 0L
    private var subtitleSearchLanguage = "en"

    private var pendingDownloadVariant: StreamVariant? = null
    private val _downloadQualityOptions = MutableStateFlow<List<DownloadQualityOption>>(emptyList())
    val downloadQualityOptions = _downloadQualityOptions.asStateFlow()
    private val _downloadQualityLoading = MutableStateFlow(false)
    val downloadQualityLoading = _downloadQualityLoading.asStateFlow()

    private var pendingDownloadQuality: DownloadQualityOption? = null
    private val _downloadAudioOptions = MutableStateFlow<List<com.zstream.android.download.HlsAudioRendition>>(emptyList())
    val downloadAudioOptions = _downloadAudioOptions.asStateFlow()

    init {
        checkLocalPlayback()
        observeWatchParty()
        reportLoadingState()
        if (mediaType == "tv") {
            loadTvDetail()
        } else if (mediaType == "movie") {
            loadMovieDetail()
        }
        viewModelScope.launch {
            state.collect { s ->
                if ((s as? PlayerState.Ready)?.sourceId == "aurora") startBandwidthWatch() else stopBandwidthWatch()
            }
        }
    }

    /**
     * Aurora/Febbox throttles rather than hard-fails once a key's daily bandwidth runs out so
     * there's no playback error to react to. Instead, poll the active key's quota periodically
     * while playing from Aurora and surface a heads-up before it degrades into buffering, at
     * 50/25/10% remaining. Only runs while the current source is aurora, so idle/other-source
     * playback never pings this endpoint.
     */
    private fun startBandwidthWatch() {
        if (bandwidthWatchJob?.isActive == true) return
        bandwidthWatchJob = viewModelScope.launch {
            // Check once right away — if the key is already low (e.g. used up elsewhere before
            // this session started), we shouldn't wait 5 minutes to say so — then every 5 min.
            while (true) {
                val current = _state.value as? PlayerState.Ready ?: break
                if (current.sourceId != "aurora") break
                val key = settingsPrefs.settings.first().febboxKey?.takeIf(String::isNotBlank) ?: break
                if (key != bandwidthWatchKey) {
                    bandwidthWatchKey = key
                    notifiedBandwidthThresholds.clear()
                    exhaustionHandledForKey = null
                    _bandwidthAlert.value = null
                }
                val info = auroraKeyManager.checkKey(key)
                val percent = info.percentRemaining
                if (percent != null) {
                    if (info.exhausted) {
                        if (exhaustionHandledForKey != key) {
                            exhaustionHandledForKey = key
                            handleKeyExhausted(key)
                        }
                    } else {
                        // Claim every threshold this check crosses at once, not just the first —
                        // otherwise a key that's already at e.g. 9% trickles out one notice per
                        // poll (50%, then 25%, then 10%, each 5 min apart) instead of a single one.
                        val crossed = listOf(50, 25, 10).filter { percent <= it && it !in notifiedBandwidthThresholds }
                        if (crossed.isNotEmpty()) {
                            notifiedBandwidthThresholds += crossed
                            _bandwidthNotice.emit("Aurora key: $percent% bandwidth left today")
                        }
                    }
                }
                delay(5 * 60_000L)
            }
        }
    }

    /**
     * Called once per key the first time it's seen fully exhausted. If another key with
     * bandwidth exists, shows a persistent (non-auto-dismissing) countdown and force-switches
     * onto it after 15s — a deliberate brief rebuffer/source-switch instead of staying on a
     * throttled key indefinitely. If no other key is available, shows an explanation for 10s
     * instead (nothing to switch to, so no countdown — just a heads-up that clears itself).
     */
    private suspend fun handleKeyExhausted(exhaustedKey: String) {
        val settingsNow = settingsPrefs.settings.first()
        val freshKey = auroraKeyManager.findFreshKey(settingsNow.febboxKeys, excluding = exhaustedKey)
        if (freshKey == null) {
            _bandwidthAlert.value = "No more Aurora keys available to switch to. Either switch to " +
                "another source, or this source will start buffering or lower quality."
            delay(10_000)
            if (_bandwidthAlert.value != null) _bandwidthAlert.value = null
            return
        }
        for (secondsLeft in 15 downTo 1) {
            val current = _state.value as? PlayerState.Ready ?: return
            if (current.sourceId != "aurora") {
                _bandwidthAlert.value = null
                return
            }
            _bandwidthAlert.value = "Switching to a different Aurora key in ${secondsLeft}s…"
            delay(1000)
        }
        _bandwidthAlert.value = null
        settingsPrefs.updateSettings(settingsNow.copy(febboxKey = freshKey), syncToRemote = false)
        _recoveryNotice.emit("Auto retrying")
        loadInternal(automaticRecovery = true, prioritizedSourceId = "aurora")
    }

    private fun stopBandwidthWatch() {
        bandwidthWatchJob?.cancel()
        bandwidthWatchJob = null
        _bandwidthAlert.value = null
    }

    private fun checkLocalPlayback() {
        if (isAutoplay || castSourceId != null) {
            load()
            return
        }
        viewModelScope.launch {
            val route = withContext(Dispatchers.IO) {
                val download = downloadDao.getAllSync().firstOrNull {
                    it.status == DownloadStatus.DONE &&
                        it.tmdbId == tmdbId &&
                        it.type == (if (mediaType == "movie") "movie" else "show") &&
                        (mediaType == "movie" || (it.season == season && it.episode == episode))
                }
                download?.let { "localPlayer/${it.id}" }
                    ?: localLibraryDao.findPlayableMedia(tmdbId, mediaType, season, episode)
                        ?.let { "localFilePlayer/${it.id}" }
            }
            if (route == null) load() else _state.value = PlayerState.LocalChoice(route)
        }
    }

    fun playOnline() = load()

    private fun loadMovieDetail() {
        viewModelScope.launch {
            runCatching {
                val detail = tmdbRepo.movieDetail(id)
                _movieDetail.value = detail
            }.onFailure {
                Log.e("PlayerVM", "Failed to load movie detail", it)
            }
        }
    }

    private fun loadTvDetail() {
        viewModelScope.launch {
            var detail: com.zstream.android.data.model.TvDetail? = null
            repeat(3) { attempt ->
                detail = runCatching { tmdbRepo.tvDetail(id) }.getOrNull()
                if (detail != null) return@repeat
                Log.e("PlayerVM", "Failed to load TV detail (attempt ${attempt + 1}/3)")
                if (attempt < 2) kotlinx.coroutines.delay(800L * (attempt + 1))
            }
            val d = detail ?: return@launch
            _tvDetail.value = d
            val sNum = season ?: d.seasons?.firstOrNull()?.seasonNumber ?: 1
            loadSeason(sNum)
        }
    }

    /** Transient TMDB failures (rate limiting, network blips) shouldn't permanently show "no episodes found". */
    private suspend fun fetchSeasonWithRetry(number: Int, attempts: Int = 3): com.zstream.android.data.model.Season? {
        repeat(attempts) { attempt ->
            val sDetail = runCatching { tmdbRepo.season(id, number) }.getOrNull()
            if (sDetail != null) return sDetail
            Log.e("PlayerVM", "Failed to load season $number (attempt ${attempt + 1}/$attempts)")
            if (attempt < attempts - 1) kotlinx.coroutines.delay(800L * (attempt + 1))
        }
        return null
    }

    fun loadSeason(number: Int) {
        viewModelScope.launch {
            fetchSeasonWithRetry(number)?.let { _currentSeasonDetail.value = it }
        }
    }

    fun switchEpisode(seasonNumber: Int, episodeNumber: Int) {
        if (mediaType != "tv") return
        season = seasonNumber
        episode = episodeNumber

        viewModelScope.launch {
            val sDetail = if (_currentSeasonDetail.value?.seasonNumber != seasonNumber) {
                fetchSeasonWithRetry(seasonNumber)?.also { _currentSeasonDetail.value = it }
            } else _currentSeasonDetail.value

            val ep = sDetail?.episodes?.find { it.episodeNumber == episodeNumber }
            if (ep != null) {
                episodeId = ep.id.toString()
                seasonId = sDetail?.id?.toString() ?: ""
            }

            _state.value = PlayerState.Idle
            checkLocalPlayback()
        }
    }

    private fun observeWatchParty() {
        viewModelScope.launch {
            watchPartyManager.actions.collect { action ->
                if (action !is WatchPartyAction.Navigate) {
                    _watchPartyEvent.emit(action)
                }
            }
        }
    }

    fun reportLoadingState() {
        val content = WatchPartyContentDto(
            title = title,
            type = if (mediaType == "movie") "Movie" else "TV Show",
            tmdbId = id.toString(),
            seasonId = seasonId,
            episodeId = episodeId,
            seasonNumber = season,
            episodeNumber = episode,
            year = year,
            poster = poster
        )

        val player = WatchPartyPlayerDto(
            isPlaying = false,
            isPaused = false,
            isLoading = true,
            hasPlayedOnce = false,
            time = 0.0,
            duration = 0.0,
            playbackRate = 1.0,
            buffered = 0.0
        )

        watchPartyManager.updateLocalState(localStateOwner, content, player)
    }

    fun reportPlayerState(
        isPlaying: Boolean,
        isPaused: Boolean,
        isLoading: Boolean,
        hasPlayedOnce: Boolean,
        timeMs: Long,
        durationMs: Long,
        playbackRate: Float,
        bufferedMs: Long,
        isHost: Boolean
    ) {
        val content = WatchPartyContentDto(
            title = title,
            type = if (mediaType == "movie") "Movie" else "TV Show",
            tmdbId = id.toString(),
            seasonId = seasonId,
            episodeId = episodeId,
            seasonNumber = season,
            episodeNumber = episode,
            year = year,
            poster = poster
        )

        val player = WatchPartyPlayerDto(
            isPlaying = isPlaying,
            isPaused = isPaused,
            isLoading = isLoading,
            hasPlayedOnce = hasPlayedOnce,
            time = timeMs.coerceAtLeast(0) / 1000.0,
            duration = durationMs.coerceAtLeast(0) / 1000.0,
            playbackRate = playbackRate.toDouble(),
            buffered = bufferedMs.coerceAtLeast(0) / 1000.0
        )

        // Ensure manager's internal host state is in sync with UI state
        // and update local state for reporting
        watchPartyManager.updateLocalState(localStateOwner, content, player)
    }

    fun reportTraktPlayback(isPlaying: Boolean, timeMs: Long, durationMs: Long) {
        lastPlaybackPosition = timeMs
        lastPlaybackDuration = durationMs
        traktRepository.reportPlayback(mediaType, tmdbId, season, episode, isPlaying, timeMs, durationMs)
    }

    override fun onCleared() {
        traktRepository.stopPlayback(mediaType, tmdbId, season, episode, lastPlaybackPosition, lastPlaybackDuration)
        super.onCleared()
        watchPartyManager.clearLocalState(localStateOwner)
        stopBandwidthWatch()
    }

    fun reorderSources(newOrder: List<String>) {
        viewModelScope.launch { sourceOrderStore.saveOrder(newOrder) }
    }

    private suspend fun buildPluginMediaRequest(preferredVariantId: String? = null): PluginMediaRequest {
        // Rotate off an exhausted/invalid Aurora key before scraping, so a fresh key with
        // bandwidth left is used automatically instead of failing the whole source.
        auroraKeyManager.ensureActiveKey()
        val current = settingsPrefs.settings.first()
        return PluginMediaRequest(
            type = if (mediaType == "tv") PluginMediaRequest.Type.SHOW else PluginMediaRequest.Type.MOVIE,
            tmdbId = id.toString(),
            season = season,
            episode = episode,
            preferredVariantId = preferredVariantId,
            title = title,
            year = year.takeIf { it > 0 },
            febboxKey = current.febboxKey,
            artemisVipKey = current.artemisVipKey,
        )
    }

    fun loadSkipSegments(durationMs: Long) {
        val cacheKey = buildSkipSegmentsCacheKey() ?: return
        if (skipSegmentsCacheKey == cacheKey) return

        viewModelScope.launch {
            val settingsValue = settingsPrefs.settings.first()
            val resolvedSegments = skipSegmentRepository.getSegments(
                tmdbId = tmdbId,
                mediaType = mediaType,
                season = season,
                episode = episode,
                durationMs = durationMs,
                tidbKey = settingsValue.tidbKey,
                febboxKey = settingsValue.febboxKey,
            )
            skipSegmentsCacheKey = cacheKey
            _skipSegments.value = resolvedSegments
        }
    }

    suspend fun submitSkipSegment(submission: SkipSegmentSubmission): Result<Unit> = withContext(Dispatchers.IO) {
        val tidbKey = settingsPrefs.settings.first().tidbKey
        if (tidbKey.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("TIDB API key is not set"))
        }

        val body = JSONObject().apply {
            put("tmdb_id", submission.tmdbId)
            put("type", submission.type)
            put("segment", submission.segment)
            submission.season?.let { put("season", it) }
            submission.episode?.let { put("episode", it) }
            if (submission.startSec != null) put("start_sec", submission.startSec) else put("start_sec", JSONObject.NULL)
            if (submission.endSec != null) put("end_sec", submission.endSec) else put("end_sec", JSONObject.NULL)
            submission.videoDurationMs?.let { put("video_duration_ms", it) }
        }

        runCatching {
            val request = Request.Builder()
                .url("https://api.theintrodb.org/v3/submit")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $tidbKey")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    val message = runCatching { JSONObject(errorBody).optString("error") }.getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: response.message
                    throw IllegalStateException(message.ifBlank { "Failed to submit segment" })
                }
            }
        }.onSuccess {
            val settingsValue = settingsPrefs.settings.first()
            skipSegmentsCacheKey = null
            _skipSegments.value = skipSegmentRepository.refreshAfterSubmit(
                tmdbId = tmdbId,
                mediaType = mediaType,
                season = season,
                episode = episode,
                durationMs = submission.videoDurationMs ?: 0L,
                tidbKey = tidbKey,
                febboxKey = settingsValue.febboxKey,
            )
            skipSegmentsCacheKey = buildSkipSegmentsCacheKey()
        }
    }

    fun load() = loadInternal(selectedSourceId = castSourceId, preferredVariantId = castVariantId)

    private fun loadInternal(
        selectedSourceId: String? = null,
        automaticRecovery: Boolean = false,
        deprioritizedSourceId: String? = null,
        preferredVariantId: String? = null,
        // One-off override: boost this source to the very front of the try order for just this
        // resolve, regardless of the user's saved source order/last-successful-source/dedupe
        // logic below. Used by the Aurora bandwidth-switch countdown so the fresh key gets
        // tried first even if Aurora isn't the user's preferred source — still falls back to
        // the rest of the order if it fails, unlike selectedSourceId which excludes everything else.
        prioritizedSourceId: String? = null,
    ) {
        sources.clear()
        failedVariantUrls.clear()
        if (!automaticRecovery) failedPlaybackSourceIds.clear()
        viewModelScope.launch {
            val previousReady = _state.value as? PlayerState.Ready
            runCatching {
                val settingsValue = settingsPrefs.settings.first()
                val displaySources = sourceOrderStore.getOrderedSources(
                    hasArtemisVipKey = !settingsValue.artemisVipKey.isNullOrBlank(),
                    hasAuroraKey = !settingsValue.febboxKey.isNullOrBlank(),
                )
                cachedDisplaySources = displaySources

                if (selectedSourceId == null && settingsValue.manualSourceSelection && !automaticRecovery) {
                    val availableSources = displaySources.map { SourceResult(it.id, SourceStatus.IDLE) }
                    sources.clear()
                    sources.addAll(availableSources)
                    _state.value = PlayerState.ManualSourceSelection(availableSources)
                    return@runCatching
                }

                val automaticSources = displaySources.filter { source ->
                    when (source.id) {
                        "artemis" -> !settingsValue.artemisVipKey.isNullOrBlank()
                        "aurora" -> !settingsValue.febboxKey.isNullOrBlank()
                        else -> true
                    }
                }
                val orderedSources = if (selectedSourceId != null) {
                    displaySources.filter { it.id == selectedSourceId }
                } else {
                    var ordered = automaticSources
                    // Boost last successful source to front if setting enabled
                    if (settingsValue.enableLastSuccessfulSource) {
                        val lastId = settingsValue.lastSuccessfulSource
                        if (lastId != null) {
                            val boosted = ordered.filter { it.id == lastId }
                            val rest = ordered.filter { it.id != lastId }
                            ordered = boosted + rest
                        }
                    }
                    if (deprioritizedSourceId != null) {
                        val deprioritizedIds = failedPlaybackSourceIds + deprioritizedSourceId
                        ordered = ordered.filter { it.id !in deprioritizedIds } +
                            ordered.filter { it.id in deprioritizedIds }
                    }
                    if (prioritizedSourceId != null) {
                        val boosted = ordered.filter { it.id == prioritizedSourceId }
                        val rest = ordered.filter { it.id != prioritizedSourceId }
                        ordered = boosted + rest
                    }
                    ordered
                }

                if (orderedSources.isEmpty()) {
                    _state.value = previousReady?.copy(
                        playbackFailure = PlaybackFailure(
                            message = "Plugin has no sources available",
                            details = playbackFailureDetails("Plugin has no sources available"),
                        )
                    ) ?: PlayerState.Error("Plugin has no sources available", emptyList())
                    return@runCatching
                }

                val initialSources = (if (selectedSourceId == null) automaticSources else displaySources)
                    .map { SourceResult(it.id, SourceStatus.IDLE) }
                sources.clear()
                sources.addAll(initialSources)
                _state.value = PlayerState.Scraping(sources.toList())

                val media = buildPluginMediaRequest(preferredVariantId)

                for (source in orderedSources) {
                    val updated = sources.map {
                        if (it.id == source.id) it.copy(status = SourceStatus.TRYING) else it
                    }
                    sources.clear()
                    sources.addAll(updated)
                    _state.value = PlayerState.Scraping(sources.toList())

                    val result = pluginManager.resolve(media, source.id)

                    when (result) {
                        is StreamResult.Success -> {
                            // For HLS streams, probe one segment to confirm the CDN URL is live.
                            if (result.streamType == "hls" && !result.skipProbe) {
                                val probeOk = com.zstream.android.download.probeHlsSegment(httpClient, result.streamUrl, result.headers)
                                if (!probeOk) {
                                    val failed = sources.map {
                                        if (it.id == source.id) it.copy(status = SourceStatus.FAILED) else it
                                    }
                                    sources.clear()
                                    sources.addAll(failed)
                                    _state.value = PlayerState.Scraping(sources.toList())
                                    // ponytail: continue to next source
                                    continue
                                }
                            }

                            val success = sources.map {
                                if (it.id == source.id) it.copy(status = SourceStatus.SUCCESS, codec = result.codec) else it
                            }
                            sources.clear()
                            sources.addAll(success)

                            // Save last successful source. Uses the single-key setter (not a
                            // full-entity updateSettings from a stale `settings.value` snapshot)
                            // so it can't race a concurrent settings write and clobber it.
                            viewModelScope.launch {
                                settingsPrefs.setLastSuccessfulSource(source.id)
                            }

                            subtitleSearchLanguage = settingsValue.defaultSubtitleLanguage ?: "en"
                            val subtitles = result.captions.map { it.toSubtitleTrack() }.toMutableList()
                            fetchExternalSubtitles(subtitles)

                            val variants = result.variants.map { v ->
                                StreamVariant(id = v.id, name = v.name, quality = v.quality, codec = v.codec, tag = v.tag, streamUrl = v.streamUrl, streamType = v.streamType, headers = v.headers, requiresRefreshOnSwitch = v.requiresRefreshOnSwitch)
                            }
                            // If the user had requested a specific variant,
                            // pick it from the freshly-resolved URLs rather than defaulting to the first one.
                            val wantedVariant = desiredVariantName?.let { name ->
                                variants.firstOrNull { it.name.equals(name, ignoreCase = true) }
                            }
                            desiredVariantName = null
                            val initialUrl = wantedVariant?.streamUrl
                                ?: preferredInitialVariantUrl(source.id, result.streamUrl, variants)
                            val initialVariant = variants.firstOrNull { it.streamUrl == initialUrl }
                            logVariantSelection(result.streamUrl, initialUrl, variants)
                            _state.value = PlayerState.Ready(
                                streamUrl  = initialUrl,
                                streamType = wantedVariant?.streamType ?: initialVariant?.streamType ?: result.streamType,
                                headers    = wantedVariant?.headers?.takeIf { it.isNotEmpty() } ?: initialVariant?.headers?.takeIf { it.isNotEmpty() } ?: result.headers,
                                subtitles  = subtitles,
                                sources    = success,
                                sourceId   = source.id,
                                variants   = variants,
                            )

                            if (settingsValue.subtitlesEnabled && subtitles.isNotEmpty()) {
                                downloadAndParseSubtitles(subtitles)
                            }
                            return@runCatching
                        }
                        is StreamResult.NotFound, is StreamResult.Error -> {
                            val failed = sources.map {
                                if (it.id == source.id) it.copy(status = SourceStatus.FAILED) else it
                            }
                            sources.clear()
                            sources.addAll(failed)
                            _state.value = PlayerState.Scraping(sources.toList())
                        }
                    }
                }

                _state.value = previousReady?.copy(
                    sources = sources.toList(),
                    playbackFailure = PlaybackFailure(
                        message = "No playable stream found",
                        details = playbackFailureDetails("No playable stream found"),
                    )
                ) ?: PlayerState.Error("No playable stream found", sources.toList())

            }.onFailure {
                Log.e("PlayerVM", "load error: ${it.message}", it)
                _state.value = previousReady?.copy(
                    sources = sources.toList(),
                    playbackFailure = PlaybackFailure(
                        message = it.message ?: "Unknown error",
                        details = it.stackTraceToString(),
                    )
                ) ?: PlayerState.Error(it.message ?: "Unknown error", sources.toList())
            }
        }
    }

    fun selectSource(sourceId: String) {
        if (settings.value.manualSourceSelection && _state.value !is PlayerState.Ready) {
            probeSource(sourceId)
        } else {
            loadInternal(selectedSourceId = sourceId)
        }
    }

    fun probeSource(sourceId: String) {
        val pluginSources = cachedDisplaySources.ifEmpty { pluginManager.availableSources() }
        val currentSources = if (sources.isEmpty()) {
            pluginSources.map { SourceResult(it.id, SourceStatus.IDLE) }.also {
                sources.clear()
                sources.addAll(it)
            }
        } else sources.toList()

        val updated = currentSources.map {
            if (it.id == sourceId) it.copy(status = SourceStatus.TRYING) else it
        }
        sources.clear()
        sources.addAll(updated)
        val current = _state.value as? PlayerState.ManualSourceSelection
        _state.value = PlayerState.ManualSourceSelection(
            updated,
            candidates = current?.candidates.orEmpty() - sourceId,
        )

        probeJobs.remove(sourceId)?.cancel()
        probeJobs[sourceId] = viewModelScope.launch {
            resolveSourceCandidate(sourceId).onSuccess { candidate ->
                val state = _state.value as? PlayerState.ManualSourceSelection ?: return@launch
                val success = state.sources.map {
                    if (it.id == sourceId) it.copy(status = SourceStatus.SUCCESS, codec = candidate.variants.firstOrNull()?.codec.orEmpty()) else it
                }
                sources.clear(); sources.addAll(success)
                _state.value = state.copy(sources = success, candidates = state.candidates + (sourceId to candidate), message = null)
            }.onFailure {
                val state = _state.value as? PlayerState.ManualSourceSelection ?: return@onFailure
                val failed = state.sources.map { if (it.id == sourceId) it.copy(status = SourceStatus.FAILED) else it }
                sources.clear(); sources.addAll(failed)
                _state.value = state.copy(sources = failed, candidates = state.candidates - sourceId, message = it.message ?: "Failed to probe source")
            }.also {
                probeJobs.remove(sourceId)
            }
        }
    }

    fun probeSourceWhileReady(sourceId: String) {
        val current = _state.value as? PlayerState.Ready ?: return
        val updated = current.sources.map {
            if (it.id == sourceId) it.copy(status = SourceStatus.TRYING) else it
        }
        sources.clear()
        sources.addAll(updated)
        _state.value = current.copy(sources = updated, candidates = current.candidates - sourceId)

        probeJobs.remove(sourceId)?.cancel()
        probeJobs[sourceId] = viewModelScope.launch {
            resolveSourceCandidate(sourceId).onSuccess { candidate ->
                val state = _state.value as? PlayerState.Ready ?: return@launch
                val success = state.sources.map {
                    if (it.id == sourceId) it.copy(status = SourceStatus.SUCCESS, codec = candidate.variants.firstOrNull()?.codec.orEmpty()) else it
                }
                sources.clear(); sources.addAll(success)
                _state.value = state.copy(sources = success, candidates = state.candidates + (sourceId to candidate))
            }.onFailure {
                val state = _state.value as? PlayerState.Ready ?: return@onFailure
                val failed = state.sources.map { if (it.id == sourceId) it.copy(status = SourceStatus.FAILED) else it }
                sources.clear(); sources.addAll(failed)
                _state.value = state.copy(sources = failed, candidates = state.candidates - sourceId)
            }.also {
                probeJobs.remove(sourceId)
            }
        }
    }

    /** Switches to a different stream variant without re-resolving the source. */
    fun switchVariant(variant: StreamVariant) {
        val current = _state.value as? PlayerState.Ready ?: return
        // If the variant signals its URL expires quickly, re-resolve immediately to get a
        // fresh URL rather than trying a likely-stale cached one.
        if (variant.requiresRefreshOnSwitch) {
            desiredVariantName = variant.name
            loadInternal(
                selectedSourceId = current.sourceId,
                automaticRecovery = true,
                preferredVariantId = variant.id,
            )
            return
        }
        desiredVariantName = variant.name
        _state.value = current.copy(streamUrl = variant.streamUrl, playbackFailure = null)
            .copy(streamType = variant.streamType, headers = if (variant.headers.isNotEmpty()) variant.headers else current.headers)
    }

    fun beginDownload(variant: StreamVariant) {
        val ready = _state.value as? PlayerState.Ready ?: return
        if (variant.streamType != "hls") {
            enqueueDownload(variant, variant.streamUrl, variant.displayLabel(), audioStreamUrl = null, audioLanguage = null)
            return
        }
        pendingDownloadVariant = variant
        _downloadQualityLoading.value = true
        viewModelScope.launch {
            val headers = variant.headers.ifEmpty { ready.headers }
            val options = runCatching {
                withContext(Dispatchers.IO) {
                    com.zstream.android.download.fetchHlsQualityOptions(httpClient, variant.streamUrl, headers)
                }
            }.getOrDefault(emptyList())
            _downloadQualityLoading.value = false
            if (options.size <= 1) {
                val single = options.firstOrNull()
                proceedWithQuality(
                    variant,
                    DownloadQualityOption(
                        label = single?.height?.let { "${it}p" } ?: variant.displayLabel(),
                        streamUrl = single?.uri ?: variant.streamUrl,
                        bandwidth = single?.bandwidth ?: 0L,
                        audioOptions = single?.audioOptions.orEmpty(),
                    ),
                )
            } else {
                _downloadQualityOptions.value = options.map { opt ->
                    DownloadQualityOption(
                        label = opt.height?.let { "${it}p" } ?: "${opt.bandwidth / 1000} kbps",
                        streamUrl = opt.uri,
                        bandwidth = opt.bandwidth,
                        audioOptions = opt.audioOptions,
                    )
                }
            }
        }
    }

    fun downloadAtQuality(option: DownloadQualityOption) {
        val variant = pendingDownloadVariant ?: return
        _downloadQualityOptions.value = emptyList()
        proceedWithQuality(variant, option)
    }

    private fun proceedWithQuality(variant: StreamVariant, option: DownloadQualityOption) {
        if (option.audioOptions.size > 1) {
            pendingDownloadQuality = option
            _downloadAudioOptions.value = option.audioOptions
        } else {
            pendingDownloadVariant = null
            val audio = option.audioOptions.firstOrNull()
            enqueueDownload(variant, option.streamUrl, option.label, audio?.uri, audio?.language?.ifBlank { null })
        }
    }

    fun downloadWithAudio(rendition: com.zstream.android.download.HlsAudioRendition) {
        val variant = pendingDownloadVariant ?: return
        val option = pendingDownloadQuality ?: return
        pendingDownloadVariant = null
        pendingDownloadQuality = null
        _downloadAudioOptions.value = emptyList()
        enqueueDownload(variant, option.streamUrl, option.label, rendition.uri, rendition.language.ifBlank { null })
    }

    fun cancelDownloadAudioPicker() {
        pendingDownloadVariant = null
        pendingDownloadQuality = null
        _downloadAudioOptions.value = emptyList()
    }

    fun cancelDownloadQualityPicker() {
        pendingDownloadVariant = null
        pendingDownloadQuality = null
        _downloadQualityOptions.value = emptyList()
        _downloadQualityLoading.value = false
        _downloadAudioOptions.value = emptyList()
    }

    private fun enqueueDownload(variant: StreamVariant, streamUrl: String, qualityLabel: String, audioStreamUrl: String? = null, audioLanguage: String? = null) {
        val ready = _state.value as? PlayerState.Ready ?: return
        viewModelScope.launch {
            val target = if (mediaType == "tv") {
                com.zstream.android.download.DownloadTarget.Episode(
                    showTitle = title,
                    season = season ?: 1,
                    episode = episode ?: 1,
                )
            } else {
                com.zstream.android.download.DownloadTarget.Movie(
                    title = title,
                    year = year.takeIf { it > 0 },
                )
            }
            // Multiple providers can offer the same language (source/plugin, Wyzie, Natsuki,
            // OpenSubtitles, Granite). Keep source-provided/Wyzie/Natsuki captions for a language
            // when any of those are available; only fall back to a lower-priority provider
            // (OpenSubtitles/Granite) if none of those exist for that language, so the folder
            // isn't cluttered with near-duplicate captions from every provider at once.
            fun subtitlePriority(source: String?): Int = when {
                source.equals("plugin", ignoreCase = true) -> 0
                source?.contains("wyzie", ignoreCase = true) == true -> 0
                source?.contains("natsuki", ignoreCase = true) == true -> 0
                else -> 1
            }
            val captions = ready.subtitles
                .groupBy { it.language }
                .values
                .flatMap { group ->
                    val preferred = group.filter { subtitlePriority(it.source) == 0 }
                    preferred.ifEmpty { listOfNotNull(group.minByOrNull { subtitlePriority(it.source) }) }
                }
                .map { sub ->
                    com.zstream.android.plugin.Caption(url = sub.url, language = sub.language, langIso = sub.language, type = sub.type, source = sub.source ?: "plugin")
                }
            val sourceId = ready.sourceId ?: "unknown"
            val sourceDisplayName = pluginManager.availableSources().firstOrNull { it.id == sourceId }?.displayName ?: sourceId
            val request = com.zstream.android.download.DownloadRequest(
                tmdbId = tmdbId,
                target = target,
                sourceId = sourceId,
                sourceDisplayName = sourceDisplayName,
                variantId = variant.id,
                qualityLabel = qualityLabel,
                streamUrl = streamUrl,
                streamType = variant.streamType,
                headers = variant.headers.ifEmpty { ready.headers },
                captions = captions,
                audioStreamUrl = audioStreamUrl,
                audioLanguage = audioLanguage,
                posterPath = poster,
            )
            val downloadId = downloadRepository.enqueue(request)
            com.zstream.android.download.DownloadService.enqueue(appContext, downloadId, request)
            _recoveryNotice.tryEmit("Download started: $qualityLabel")
        }
    }

    fun onPlaybackError(
        title: String = "Playback error",
        message: String,
        errorCode: Int = 0,
        httpStatus: Int = 0,
        details: String = message,
    ) {
        val current = _state.value as? PlayerState.Ready ?: return
        viewModelScope.launch {
            if (!settingsPrefs.settings.first().enableAutoResumeOnPlaybackError) {
                _state.value = current.copy(
                    playbackFailure = PlaybackFailure(
                        message = "Playback failed: $message",
                        details = details,
                        title = title,
                    )
                )
                return@launch
            }

            if (errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                when (httpStatus) {
                    403 -> {
                        // Signed URL expired — re-resolve immediately to get fresh URLs.
                        // Pass the currently-playing variant as a hint so the source
                        // can prioritise refreshing that URL.
                        awaitingRecoveryPlayback = true
                        _recoveryNotice.emit("Auto retrying")
                        val playingVariantId = current.variants
                            .firstOrNull { it.streamUrl == current.streamUrl }?.id
                        Log.w("PlaybackRecovery", "403 on source ${current.sourceId}; re-resolving (URL expired, variant=$playingVariantId)")
                        loadInternal(automaticRecovery = true, preferredVariantId = playingVariantId)
                        return@launch
                    }
                    429 -> {
                        // Rate-limited — this variant's URL is being throttled. Wait it out and
                        // re-resolve rather than cycling variants (a rate limit tends to apply
                        // to the whole source, not just this URL).
                        awaitingRecoveryPlayback = true
                        _recoveryNotice.emit("Auto retrying")
                        Log.w("PlaybackRecovery", "429 on source ${current.sourceId}; waiting 5s before re-resolve")
                        failedVariantUrls += current.streamUrl
                        kotlinx.coroutines.delay(5_000)
                        failedVariantUrls.clear()
                        loadInternal(automaticRecovery = true)
                        return@launch
                    }
                    else -> {
                        // Unknown HTTP error — fall through to the plain variant-failure path below.
                    }
                }
            }

            failedVariantUrls += current.streamUrl

            // Mark this URL failed and surface the same diagnosis used by the error overlay.
            val variantLabel = current.variants.find { it.streamUrl == current.streamUrl }?.displayLabel()
            Log.w("PlaybackRecovery", "variant failed; sourceId=${current.sourceId} variant=$variantLabel")
            awaitingRecoveryPlayback = false
            _recoveryNotice.tryEmit("$title: $message")
            _state.value = current.copy(
                failedVariantUrls = failedVariantUrls.toSet(),
                playbackFailure = PlaybackFailure(
                    message = message,
                    details = details,
                    title = title,
                ),
            )
        }
    }

    fun onPlaybackStarted() {
        if (!awaitingRecoveryPlayback) return
        val current = _state.value as? PlayerState.Ready ?: return
        awaitingRecoveryPlayback = false
        val source = current.sourceId.orEmpty().replaceFirstChar { it.uppercase() }
        val variant = current.variants.find { it.streamUrl == current.streamUrl }?.displayLabel()
        _recoveryNotice.tryEmit("Use source: $source${variant?.let { " · $it" }.orEmpty()}")
    }

    private fun Caption.toSubtitleTrack() = SubtitleTrack(
        label    = language,
        url      = url,
        language = langIso,
        type     = type,
        id       = url,
        source   = "plugin",
        external = false,
    )

    /** Fetches Wyzie/OpenSubtitles/Granite/Natsuki external subtitles and merges into [tracks]. */
    private suspend fun fetchExternalSubtitles(tracks: MutableList<SubtitleTrack>) = coroutineScope {
        val language = settings.value.defaultSubtitleLanguage ?: "en"
        val external = listOf(
            async { fetchWyzieSubtitles(language) },
            async { fetchOpenSubtitles(language) },
            async { fetchGraniteSubtitles(language) },
            async { fetchNatsukiSubtitles(language) },
        ).flatMap { it.await() }.distinctBy { it.id }
        tracks.addAll(external)
    }

    private suspend fun refreshExternalSubtitles(language: String) {
        val current = _state.value as? PlayerState.Ready ?: return
        val external = coroutineScope {
            listOf(
                async { fetchWyzieSubtitles(language) },
                async { fetchOpenSubtitles(language) },
                async { fetchGraniteSubtitles(language) },
                async { fetchNatsukiSubtitles(language) },
            ).flatMap { it.await() }
        }.distinctBy { it.id }
        _state.value = current.copy(subtitles = (current.subtitles.filterNot { it.external } + external).distinctBy { it.id })
    }

    fun confirmManualSourceSelection(sourceId: String) {
        val current = _state.value as? PlayerState.ManualSourceSelection ?: return
        val candidate = current.candidates[sourceId] ?: return
        cancelOtherProbeJobs(sourceId)
        val sourceStates = clearTryingStatuses(current.sources)
        sources.clear()
        sources.addAll(sourceStates)
        _state.value = readyStateFromCandidate(candidate, sourceStates, current.candidates)
        if (settings.value.subtitlesEnabled && candidate.subtitles.isNotEmpty()) {
            downloadAndParseSubtitles(candidate.subtitles)
        }
    }

    fun applyProbedSource(sourceId: String) {
        val current = _state.value as? PlayerState.Ready ?: return
        val candidate = current.candidates[sourceId] ?: return
        cancelOtherProbeJobs(sourceId)
        val sourceStates = clearTryingStatuses(current.sources)
        sources.clear()
        sources.addAll(sourceStates)
        _state.value = readyStateFromCandidate(candidate, sourceStates, current.candidates)
        if (settings.value.subtitlesEnabled && candidate.subtitles.isNotEmpty()) {
            downloadAndParseSubtitles(candidate.subtitles)
        }
    }

    private fun cancelOtherProbeJobs(selectedSourceId: String) {
        probeJobs
            .filterKeys { it != selectedSourceId }
            .forEach { (id, job) ->
                job.cancel()
                probeJobs.remove(id)
            }
    }

    fun retryNextSourceAfterError() {
        val current = _state.value as? PlayerState.Ready ?: return
        val sourceId = current.sourceId ?: return
        failedPlaybackSourceIds += sourceId
        awaitingRecoveryPlayback = true
        _recoveryNotice.tryEmit("Auto retrying")
        loadInternal(automaticRecovery = true, deprioritizedSourceId = sourceId)
    }

    fun reloadCurrentSource() {
        val current = _state.value as? PlayerState.Ready ?: return
        awaitingRecoveryPlayback = false
        loadInternal(selectedSourceId = current.sourceId, automaticRecovery = true)
    }

    private fun readyStateFromCandidate(
        candidate: ResolvedSourceCandidate,
        sourceStates: List<SourceResult>,
        candidates: Map<String, ResolvedSourceCandidate>,
    ) = PlayerState.Ready(
        streamUrl = candidate.streamUrl,
        streamType = candidate.streamType,
        headers = candidate.headers,
        subtitles = candidate.subtitles,
        sources = sourceStates,
        sourceId = candidate.sourceId,
        variants = candidate.variants,
        candidates = candidates,
    )

    private suspend fun resolveSourceCandidate(sourceId: String): Result<ResolvedSourceCandidate> = runCatching {
        val media = buildPluginMediaRequest()
        when (val result = pluginManager.resolve(media, sourceId)) {
            is StreamResult.NotFound -> error("Not available")
            is StreamResult.Error -> error(result.message.ifBlank { "Failed to probe source" })
            is StreamResult.Success -> {
                // Probe HLS segment before accepting as valid.
                if (result.streamType == "hls" && !result.skipProbe) {
                    val probeOk = com.zstream.android.download.probeHlsSegment(httpClient, result.streamUrl, result.headers)
                    if (!probeOk) {
                        error("Source stream is not reachable")
                    }
                }
                val subtitles = result.captions.map { it.toSubtitleTrack() }.toMutableList()
                fetchExternalSubtitles(subtitles)
                val variants = result.variants.map { v ->
                    StreamVariant(id = v.id, name = v.name, quality = v.quality, codec = v.codec, tag = v.tag, streamUrl = v.streamUrl, streamType = v.streamType, headers = v.headers, requiresRefreshOnSwitch = v.requiresRefreshOnSwitch)
                }
                val initialUrl = preferredInitialVariantUrl(sourceId, result.streamUrl, variants)
                val initialVariant = variants.firstOrNull { it.streamUrl == initialUrl }
                logVariantSelection(result.streamUrl, initialUrl, variants)
                ResolvedSourceCandidate(
                    streamUrl = initialUrl,
                    streamType = initialVariant?.streamType ?: result.streamType,
                    headers = initialVariant?.headers?.takeIf { it.isNotEmpty() } ?: result.headers,
                    subtitles = subtitles,
                    sourceId = sourceId,
                    variants = variants,
                )
            }
        }
    }

    fun selectSubtitle(id: String) {
        viewModelScope.launch {
            val state = _state.value as? PlayerState.Ready ?: return@launch
            val track = state.subtitles.find { it.id == id } ?: return@launch
            settingsPrefs.setSubtitlesEnabled(true)
            val languageChanged = subtitleSearchLanguage != track.language
            subtitleSearchLanguage = track.language
            _selectedSubtitleId.value = track.id
            _selectedSubtitleLang.value = track.language
            downloadAndParseSubtitles(listOf(track))
            if (languageChanged) refreshExternalSubtitles(track.language)
        }
    }

    fun autoSelectSubtitle() {
        val tracks = (_state.value as? PlayerState.Ready)?.subtitles.orEmpty()
        val candidates = subtitleCandidates(tracks, subtitleSearchLanguage)
        val pick = candidates.firstOrNull { it.source?.contains("wyzie", true) == true }
            ?: candidates.firstOrNull { it.source?.contains("natsuki", true) == true }
            ?: candidates.firstOrNull { it.source?.contains("opensubs", true) == true }
            ?: candidates.firstOrNull { it.source?.contains("granite", true) == true }
            ?: candidates.firstOrNull()
        pick?.let { selectSubtitle(it.id) }
    }

    fun disableSubtitles() {
        viewModelScope.launch {
            settingsPrefs.setSubtitlesEnabled(false)
            _selectedSubtitleLang.value = null
            _selectedSubtitleId.value = null
            _subtitleCues.value = emptyList()
        }
    }

    fun enableSubtitles() {
        viewModelScope.launch {
            settingsPrefs.setSubtitlesEnabled(true)
            val state = _state.value
            if (state is PlayerState.Ready && state.subtitles.isNotEmpty()) {
                downloadAndParseSubtitles(state.subtitles)
            }
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            if (isBookmarked.value != null) {
                bookmarkRepo.removeBookmark(tmdbId)
            } else {
                bookmarkRepo.addBookmark(
                    tmdbId = tmdbId,
                    title = title,
                    type = mediaType,
                    year = year.takeIf { it > 0 },
                    posterPath = poster,
                )
            }
        }
    }

    fun setEnableAutoplay(enabled: Boolean) {
        viewModelScope.launch { settingsPrefs.setEnableAutoplay(enabled) }
    }

    /** True if a TV is currently paired (in-memory session), so casting is possible right now. */
    fun hasPairedTv(): Boolean = tvSyncRepository.activeReceiver.value != null

    /**
     * Sends the currently-playing source/variant/progress to the paired TV so it can start playing
     * immediately, without having to search through sources itself.
     */
    fun castToTv(currentPositionSec: Long, onResult: (Result<Unit>) -> Unit) {
        val ready = _state.value as? PlayerState.Ready
        val sourceId = ready?.sourceId
        val receiver = tvSyncRepository.activeReceiver.value
        if (receiver == null || sourceId == null) {
            onResult(Result.failure(IllegalStateException("No TV paired")))
            return
        }
        val variantId = ready.variants.find { it.streamUrl == ready.streamUrl }?.id
        val request = com.zstream.android.data.CastPlaybackRequest(
            tmdbId = id,
            mediaType = mediaType,
            title = title,
            year = year,
            poster = poster,
            season = season,
            episode = episode,
            seasonId = seasonId,
            episodeId = episodeId,
            sourceId = sourceId,
            variantId = variantId,
            progressSec = currentPositionSec,
        )
        viewModelScope.launch {
            val result = runCatching { tvSyncRepository.sendCast(receiver.host, receiver.port, request) }
            onResult(result)
        }
    }

    fun setVideoBrightness(value: Int) {
        viewModelScope.launch { settingsPrefs.setVideoBrightness(value.coerceIn(10, 200)) }
    }

    fun setVolumeBoost(value: Int) {
        viewModelScope.launch { settingsPrefs.setVolumeBoost(value.coerceIn(100, 300)) }
    }

    fun setVideoScaleMode(value: String) {
        val normalized = value.lowercase()
        if (normalized !in setOf("fit", "fill", "stretch")) return
        viewModelScope.launch { settingsPrefs.setVideoScaleMode(normalized) }
    }

    fun setTvPipPosition(value: String) {
        viewModelScope.launch { settingsPrefs.setTvPipPosition(value) }
    }

    fun updatePlayerSettings(settings: SettingsEntity) {
        viewModelScope.launch { settingsPrefs.updateSettings(settings) }
    }

    fun setSubtitleDelay(delay: Float) {
        _subtitleDelay.value = delay.coerceIn(-40f, 40f)
    }

    fun setOverrideCasing(enabled: Boolean) {
        _overrideCasing.value = enabled
    }

    suspend fun getAutoplayEpisodeTarget(): AutoplayEpisodeTarget? = withContext(Dispatchers.IO) {
        if (mediaType != "tv") return@withContext null

        val currentSeason = season ?: return@withContext null
        val currentEpisode = episode ?: return@withContext null

        runCatching {
            val currentSeasonDetail = tmdbRepo.season(id, currentSeason)
            val nextEpisode = currentSeasonDetail.episodes
                .orEmpty()
                .airedEpisodes()
                .firstOrNull { it.episodeNumber == currentEpisode + 1 }
            if (nextEpisode != null) {
                return@withContext AutoplayEpisodeTarget(
                    seasonNumber = currentSeason,
                    episodeNumber = nextEpisode.episodeNumber,
                    seasonId = currentSeasonDetail.id.toString(),
                    episodeId = nextEpisode.id.toString(),
                )
            }

            val showDetail = tmdbRepo.tvDetail(id)
            val nextSeason = showDetail.seasons
                .orEmpty()
                .firstOrNull { it.seasonNumber == currentSeason + 1 }
                ?: return@withContext null

            val nextSeasonDetail = tmdbRepo.season(id, nextSeason.seasonNumber)
            val firstAiredEpisode = nextSeasonDetail.episodes
                .orEmpty()
                .airedEpisodes()
                .firstOrNull()
                ?: return@withContext null

            AutoplayEpisodeTarget(
                seasonNumber = nextSeason.seasonNumber,
                episodeNumber = firstAiredEpisode.episodeNumber,
                seasonId = nextSeasonDetail.id.toString(),
                episodeId = firstAiredEpisode.id.toString(),
            )
        }.getOrElse {
            Log.e("PlayerVM", "Failed to resolve autoplay episode target", it)
            null
        }
    }

    private fun downloadAndParseSubtitles(tracks: List<SubtitleTrack>) {
        viewModelScope.launch {
            val preferredLang = subtitleSearchLanguage
            val track = if (!preferredLang.isNullOrBlank()) {
                tracks.find { it.language == preferredLang || it.label == preferredLang }
            } else null
            val selected = track ?: tracks.singleOrNull()
            if (selected == null) {
                Log.d("PlayerVM", "downloadSubtitles: no tracks available")
                return@launch
            }

            _selectedSubtitleLang.value = selected.language
            _selectedSubtitleId.value = selected.id
            Log.d("PlayerVM", "downloadSubtitles: selected lang=${selected.language} id=${selected.id}")

            val cacheKey = "${selected.id}:${selected.url}"
            val cached = subtitleCache[cacheKey]
            if (cached != null) {
                Log.d("PlayerVM", "downloadSubtitles: using cached ${cached.size} cues")
                _subtitleCues.value = cached
                return@launch
            }

            val cues = withContext(Dispatchers.IO) {
                try {
                    val raw = downloadSubtitleText(selected.url)
                    Log.d("PlayerVM", "downloadSubtitles: downloaded ${raw.length} chars")
                    val parsed = parseSubtitleText(raw)
                    Log.d("PlayerVM", "downloadSubtitles: parsed ${parsed.size} cues")
                    subtitleCache[cacheKey] = parsed
                    parsed.take(5).forEach { c ->
                        Log.d("PlayerVM", "  cue: ${c.startMs}->${c.endMs} '${c.text.take(50)}'")
                    }
                    parsed
                } catch (e: Exception) {
                    Log.e("PlayerVM", "download/parse subtitle failed: ${e.message}", e)
                    emptyList()
                }
            }
            _subtitleCues.value = cues
            Log.d("PlayerVM", "loaded ${cues.size} subtitle cues for ${selected.language}")
        }
    }

    private suspend fun downloadSubtitleText(url: String): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
        // Natsuki's file-serving endpoint gates on Origin/Referer the same way its search
        // endpoint does (see fetchNatsukiSubtitles) -- without these the .srt/.vtt download
        // 403s with a short "forbidden" body that silently parses to zero cues instead of
        // erroring, which is exactly what looked like "subtitle selected but nothing shows".
        if (url.contains("fontaine.lol", ignoreCase = true)) {
            builder.header("Origin", "https://zstream.mov")
            builder.header("Referer", "https://zstream.mov/")
        }
        val response = httpClient.newCall(builder.build()).execute()
        if (!response.isSuccessful) throw Exception("Subtitle download failed: HTTP ${response.code}")
        response.body?.string() ?: throw Exception("Empty subtitle response")
    }

    /** Parse SRT or VTT subtitle text into timed cues */
    internal fun parseSubtitleText(text: String): List<SubtitleCue> {
        val trimmed = text.trim()
        val isVtt = trimmed.startsWith("WEBVTT")
        return if (isVtt) parseVtt(trimmed) else parseSrt(trimmed)
    }

    private fun parseSrt(text: String): List<SubtitleCue> {
        val blocks = text.split(Regex("\n\\s*\n"))
        val cues = mutableListOf<SubtitleCue>()
        val timeRegex = Regex(
            """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"""
        )
        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.size < 2) continue
            val timeMatch = timeRegex.find(lines[1] ?: continue) ?: continue
            val startMs = timeToMs(
                timeMatch.groupValues[1].toInt(),
                timeMatch.groupValues[2].toInt(),
                timeMatch.groupValues[3].toInt(),
                timeMatch.groupValues[4].toInt()
            )
            val endMs = timeToMs(
                timeMatch.groupValues[5].toInt(),
                timeMatch.groupValues[6].toInt(),
                timeMatch.groupValues[7].toInt(),
                timeMatch.groupValues[8].toInt()
            )
            val text = lines.drop(2).joinToString("\n").trim()
            if (text.isNotEmpty()) {
                cues.add(SubtitleCue(startMs, endMs, text))
            }
        }
        return cues
    }

    private fun parseVtt(text: String): List<SubtitleCue> {
        val body = text.substringAfter("WEBVTT")
            .substringAfter("\n")
            .trim()
        // Granite's VTT files omit blank lines between cues. Split on each timestamp as well
        // so all captions do not become one long cue.
        val blocks = vttCueBlocks(body)
        val cues = mutableListOf<SubtitleCue>()
        val timeRegex = Regex(
            """(\d{1,2}):(\d{2}):(\d{2})[.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[.](\d{1,3})"""
        )
        val timeRegexMin = Regex(
            """(\d{1,2}):(\d{2})[.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2})[.](\d{1,3})"""
        )
        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.size < 2) continue
            val timeLine = lines.first { it.contains("-->") }
            val timeMatch = timeRegex.find(timeLine) ?: timeRegexMin.find(timeLine) ?: continue
            val startMs = if (timeMatch.groupValues.size >= 9) {
                timeToMs(
                    timeMatch.groupValues[1].toInt(),
                    timeMatch.groupValues[2].toInt(),
                    timeMatch.groupValues[3].toInt(),
                    timeMatch.groupValues[4].toInt()
                )
            } else {
                timeToMs(0, timeMatch.groupValues[1].toInt(), timeMatch.groupValues[2].toInt(), timeMatch.groupValues[3].toInt())
            }
            val endMs = if (timeMatch.groupValues.size >= 9) {
                timeToMs(
                    timeMatch.groupValues[5].toInt(),
                    timeMatch.groupValues[6].toInt(),
                    timeMatch.groupValues[7].toInt(),
                    timeMatch.groupValues[8].toInt()
                )
            } else {
                timeToMs(0, timeMatch.groupValues[4].toInt(), timeMatch.groupValues[5].toInt(), timeMatch.groupValues[6].toInt())
            }
            // Skip cue settings (lines starting with "align:" etc) and WebVTT metadata
            val textLines = lines.dropWhile { it.contains("-->") || it.contains(":") || it.startsWith("NOTE") }
            val text = textLines.joinToString("\n").trim()
            if (text.isNotEmpty()) {
                cues.add(SubtitleCue(startMs, endMs, text))
            }
        }
        return cues
    }

    private fun timeToMs(h: Int, m: Int, s: Int, ms: Int): Long {
        return h.toLong() * 3600000 + m.toLong() * 60000 + s.toLong() * 1000 + ms.toLong()
    }

    private fun buildSkipSegmentsCacheKey(): String? {
        return when {
            mediaType == "movie" -> "skip-movie-$tmdbId"
            mediaType == "tv" && season != null && episode != null -> "skip-tv-$tmdbId-$season-$episode"
            else -> null
        }
    }

    private fun String.decodeRouteParam(): String {
        return runCatching { URLDecoder.decode(this, "UTF-8") }.getOrDefault(this)
    }

    private suspend fun fetchWyzieSubtitles(language: String): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        val key = settings.value.wyzieKey ?: return@withContext emptyList()
        val url = "https://sub.wyzie.io/search".toHttpUrl().newBuilder()
            .addQueryParameter("id", tmdbId)
            .addQueryParameter("key", key)
            .addQueryParameter("language", language)
            .addQueryParameter("encoding", "utf-8")
            .addQueryParameter("source", "all")
            .apply {
                if (mediaType == "tv" && season != null && episode != null) {
                    addQueryParameter("season", season.toString())
                    addQueryParameter("episode", episode.toString())
                }
            }
            .build()
        runCatching {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                parseWyzieSubtitles(response.body?.string().orEmpty())
            }
        }.onFailure { Log.w("PlayerVM", "Wyzie subtitle lookup failed", it) }.getOrDefault(emptyList())
    }

    private suspend fun fetchOpenSubtitles(language: String): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        val imdbId = runCatching {
            if (mediaType == "tv") {
                tmdbRepo.tvDetail(id).let { it.imdbId ?: it.externalIds?.imdbId }
            } else {
                tmdbRepo.movieDetail(id).imdbId
            }
        }.getOrNull()?.removePrefix("tt") ?: return@withContext emptyList()
        val path = buildString {
            append("https://rest.opensubtitles.org/search/")
            if (season != null && episode != null) append("episode-$episode/")
            append("imdbid-$imdbId")
            if (season != null && episode != null) append("/season-$season")
        }
        runCatching {
            httpClient.newCall(Request.Builder().url(path).header("X-User-Agent", "VLSub 0.10.2").build()).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val entries = JSONArray(response.body?.string().orEmpty())
                (0 until entries.length()).mapNotNull { index ->
                    val entry = entries.optJSONObject(index) ?: return@mapNotNull null
                    val lang = entry.optString("ISO639").lowercase().takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val url = entry.optString("SubDownloadLink").replace(".gz", "").replace("download/", "download/subencoding-utf8/")
                        .takeIf(String::isNotBlank) ?: return@mapNotNull null
                    SubtitleTrack(entry.optString("LanguageName", lang), url, lang, entry.optString("SubFormat", "srt"), source = "opensubs", external = true)
                }
            }
        }.onFailure { Log.w("PlayerVM", "OpenSubtitles lookup failed", it) }.getOrDefault(emptyList())
    }

    private suspend fun fetchGraniteSubtitles(language: String): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        val url = if (mediaType == "tv" && season != null && episode != null) {
            "https://sub.vdrk.site/v1/tv/$tmdbId/$season/$episode"
        } else {
            "https://sub.vdrk.site/v1/movie/$tmdbId"
        }
        runCatching {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val entries = JSONArray(response.body?.string().orEmpty())
                (0 until entries.length()).mapNotNull { index ->
                    val entry = entries.optJSONObject(index) ?: return@mapNotNull null
                    val label = entry.optString("label")
                    val lang = languageCodeFromLabel(label) ?: return@mapNotNull null
                    val file = entry.optString("file").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    SubtitleTrack(label, file, lang, "vtt", source = "granite", hearingImpaired = label.contains("Hi", true), external = true)
                }
            }
        }.onFailure { Log.w("PlayerVM", "Granite subtitle lookup failed", it) }.getOrDefault(emptyList())
    }

    /**
     * Natsuki's real contract (website source: p-stream/src/utils/externalSubtitles/natsuki.ts)
     * differs from the standalone snippet this was first built against: base path is `/subs` (not
     * `/search`), the query key is `tmdbId`/`imdbId` (not a single `id`), the response is a
     * `{subtitles: [...]}` object (not a bare array) with `sid`/`langCode`/`fileName` fields (not
     * `id`/`language`/`display`), and — the actual reason plain requests 403 — the origin server
     * gates on an `Origin`/`Referer` matching the real site, not on a User-Agent string.
     * Tries by tmdbId first, then imdbId, same fallback order the website uses.
     */
    private suspend fun fetchNatsukiSubtitles(language: String): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        val imdbId = runCatching {
            if (mediaType == "tv") {
                tmdbRepo.tvDetail(id).let { it.imdbId ?: it.externalIds?.imdbId }
            } else {
                tmdbRepo.movieDetail(id).imdbId
            }
        }.getOrNull()?.takeIf(String::isNotBlank)

        val attempts = buildList {
            if (tmdbId.isNotBlank()) add("tmdbId" to tmdbId)
            if (imdbId != null) add("imdbId" to imdbId)
        }

        for ((key, value) in attempts) {
            val url = "https://natsuki.fontaine.lol/subs".toHttpUrl().newBuilder()
                .addQueryParameter(key, value)
                .apply {
                    if (mediaType == "tv" && season != null && episode != null) {
                        addQueryParameter("season", season.toString())
                        addQueryParameter("episode", episode.toString())
                    }
                }
                .build()
            val result = runCatching {
                httpClient.newCall(
                    Request.Builder().url(url)
                        .header("Origin", "https://zstream.mov")
                        .header("Referer", "https://zstream.mov/")
                        .build()
                ).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val root = org.json.JSONObject(response.body?.string().orEmpty())
                    val entries = root.optJSONArray("subtitles") ?: return@use emptyList()
                    (0 until entries.length()).mapNotNull { index ->
                        val entry = entries.optJSONObject(index) ?: return@mapNotNull null
                        val subUrl = entry.optString("url").takeIf(String::isNotBlank) ?: return@mapNotNull null
                        val fileName = entry.optString("fileName").takeIf(String::isNotBlank)
                        val lang = entry.optString("langCode").takeIf(String::isNotBlank)
                            ?: entry.optString("language").takeIf(String::isNotBlank)?.let(::normalizeLanguageCode)
                            ?: "unknown"
                        val fmt = if ((fileName ?: subUrl).contains(".vtt", ignoreCase = true)) "vtt" else "srt"
                        SubtitleTrack(
                            label = fileName ?: entry.optString("language").ifBlank { lang },
                            url = subUrl,
                            language = lang,
                            type = fmt,
                            id = entry.optString("sid").takeIf(String::isNotBlank) ?: subUrl,
                            source = "natsuki",
                            hearingImpaired = entry.optBoolean("hearingImpaired", false),
                            external = true,
                        )
                    }
                }
            }.onFailure { Log.w("PlayerVM", "Natsuki subtitle lookup failed ($key)", it) }.getOrDefault(emptyList())
            if (result.isNotEmpty()) return@withContext result
        }
        emptyList()
    }

}

package com.zstream.android.ui.screens

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.BookmarkRepository
import com.zstream.android.data.TmdbRepository
import com.zstream.android.provider.ProviderEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.data.local.entity.SettingsEntity
import com.zstream.android.data.model.airedEpisodes
import com.zstream.android.data.WatchPartyManager
import com.zstream.android.data.WatchPartyAction
import com.zstream.android.data.remote.WatchPartyContentDto
import com.zstream.android.data.remote.WatchPartyPlayerDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect

enum class SourceStatus { IDLE, TRYING, SUCCESS, FAILED }
data class SourceResult(val id: String, val status: SourceStatus)
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
    data class Scraping(val sources: List<SourceResult>) : PlayerState()
    data class ManualSourceSelection(
        val sources: List<SourceResult>,
        val selectedSourceId: String? = null,
        val candidate: Ready? = null,
        val message: String? = null,
    ) : PlayerState()
    data class Ready(
        val streamUrl: String,
        val headers: Map<String, String>,
        val subtitles: List<SubtitleTrack>,
        val sources: List<SourceResult>,
        val sourceId: String? = null,
        val embedId: String? = null,
    ) : PlayerState()
    data class Error(val message: String, val sources: List<SourceResult>) : PlayerState()
}

data class SubtitleTrack(val label: String, val url: String, val language: String, val type: String = "vtt")

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

data class AutoplayEpisodeTarget(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val seasonId: String,
    val episodeId: String,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val engine: ProviderEngine,
    private val settingsPrefs: SettingsPreferences,
    private val bookmarkRepo: BookmarkRepository,
    private val tmdbRepo: TmdbRepository,
    val watchPartyManager: WatchPartyManager,
    savedState: SavedStateHandle,
) : ViewModel() {
    val settings = settingsPrefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsEntity())

    private val id = savedState.get<Int>("id") ?: 0
    val mediaType = savedState.get<String>("mediaType") ?: "movie"
    val season = savedState.get<Int>("season").takeIf { it != -1 }
    val episode = savedState.get<Int>("episode").takeIf { it != -1 }
    val title = savedState.get<String>("title")?.decodeRouteParam().orEmpty()
    val year = savedState.get<Int>("year") ?: 0
    val poster = savedState.get<String>("poster")?.decodeRouteParam()?.takeIf { it.isNotBlank() }
    val tmdbId = id.toString()
    val seasonId = savedState.get<String>("seasonId")
    val episodeId = savedState.get<String>("episodeId")
    val isAutoplay = savedState.get<Boolean>("autoplay") ?: false

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state = _state.asStateFlow()

    private val _subtitleCues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    val subtitleCues = _subtitleCues.asStateFlow()

    private val _selectedSubtitleLang = MutableStateFlow<String?>(null)
    val selectedSubtitleLang = _selectedSubtitleLang.asStateFlow()

    private val _skipSegments = MutableStateFlow<List<SkipSegment>>(emptyList())
    val skipSegments = _skipSegments.asStateFlow()

    val isBookmarked = bookmarkRepo.observeBookmark(tmdbId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val sources = mutableListOf<SourceResult>()
    private var skipSegmentsCacheKey: String? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // --- Watch Party Actions ---
    private val _watchPartyEvent = MutableSharedFlow<WatchPartyAction>(replay = 0)
    val watchPartyEvent = _watchPartyEvent.asSharedFlow()
    private val localStateOwner = UUID.randomUUID().toString()

    init {
        load()
        observeWatchParty()
        reportLoadingState()
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

    override fun onCleared() {
        super.onCleared()
        watchPartyManager.clearLocalState(localStateOwner)
    }

    fun getProxyPort() = engine.proxy.port

    fun loadSkipSegments(durationMs: Long) {
        val cacheKey = buildSkipSegmentsCacheKey() ?: return
        if (skipSegmentsCacheKey == cacheKey) return

        viewModelScope.launch {
            val settingsValue = settingsPrefs.settings.first()
            val resolvedSegments = fetchTheIntroDbSegments(durationMs, settingsValue.tidbKey)
                ?: fetchFallbackSkipSegments(settingsValue)
                ?: emptyList()
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
        }
    }

    fun load(selectedSourceId: String? = null) {
        sources.clear()
        viewModelScope.launch {
            runCatching {
                val settingsValue = settingsPrefs.settings.first()
                if (selectedSourceId == null && settingsValue.manualSourceSelection) {
                    val availableSources = engine.sourceIds().map { SourceResult(it, SourceStatus.IDLE) }
                    sources.clear()
                    sources.addAll(availableSources)
                    _state.value = PlayerState.ManualSourceSelection(availableSources)
                    return@runCatching
                }

                _state.value = PlayerState.Scraping(emptyList())

                val mediaInput = buildMap<String, Any> {
                    put("type", if (mediaType == "tv") "show" else "movie")
                    put("tmdbId", id.toString())
                    put("title", title)
                    put("releaseYear", year)
                    if (mediaType == "tv" && season != null && episode != null) {
                        put("season", mapOf("number" to season, "tmdbId" to "", "title" to "Season $season"))
                        put("episode", mapOf("number" to episode, "tmdbId" to "", "title" to "Episode $episode"))
                    }
                }

                val result = if (selectedSourceId != null) {
                    engine.runSelected(mediaInput, selectedSourceId) { evt ->
                        handleEvent(evt)
                    }
                } else {
                    engine.runAll(mediaInput) { evt ->
                        handleEvent(evt)
                    }
                }
                Log.d("PlayerVM", "runAll result: ${result.toString().take(500)}")

                if (!result.optBoolean("ok", false)) {
                    _state.value = PlayerState.Error(result.optString("error", "No sources found"), sources.toList())
                    return@runCatching
                }

                val data = result.optJSONObject("data")
                // RunOutput: { sourceId, embedId?, stream: Stream }
                val stream = data?.optJSONObject("stream")
                val streamUrl = stream?.let { findStreamUrl(it) }
                Log.d("PlayerVM", "stream type=${stream?.optString("type")} url=$streamUrl")
                Log.d("PlayerVM", "stream headers=${stream?.optJSONObject("headers")}")

                if (streamUrl == null) {
                    _state.value = PlayerState.Error("No playable stream found", sources.toList())
                    return@runCatching
                }

                val readyState = buildReadyState(data, streamUrl)
                Log.d("PlayerVM", "parsed headers: ${readyState.headers}, subtitles: ${readyState.subtitles.size}")
                _state.value = readyState

                // Mirror web behavior: subtitle on/off is a local persisted preference.
                if (settings.value.subtitlesEnabled && readyState.subtitles.isNotEmpty()) {
                    downloadAndParseSubtitles(readyState.subtitles)
                }
            }.onFailure {
                Log.e("PlayerVM", "error: ${it.message}", it)
                _state.value = PlayerState.Error(it.message ?: "Unknown error", sources.toList())
            }
        }
    }

    fun selectSource(sourceId: String) {
        if (settings.value.manualSourceSelection && _state.value !is PlayerState.Ready) {
            probeSource(sourceId)
        } else {
            load(selectedSourceId = sourceId)
        }
    }

    fun probeSource(sourceId: String) {
        val currentSources = if (sources.isEmpty()) {
            engine.sourceIds().map { SourceResult(it, SourceStatus.IDLE) }.also {
                sources.clear()
                sources.addAll(it)
            }
        } else sources.toList()

        val updated = currentSources.map {
            if (it.id == sourceId) it.copy(status = SourceStatus.TRYING) else it
        }
        sources.clear()
        sources.addAll(updated)
        _state.value = PlayerState.ManualSourceSelection(updated, selectedSourceId = sourceId, candidate = null, message = null)

        viewModelScope.launch {
            runCatching {
                val mediaInput = buildMap<String, Any> {
                    put("type", if (mediaType == "tv") "show" else "movie")
                    put("tmdbId", id.toString())
                    put("title", title)
                    put("releaseYear", year)
                    if (mediaType == "tv" && season != null && episode != null) {
                        put("season", mapOf("number" to season, "tmdbId" to "", "title" to "Season $season"))
                        put("episode", mapOf("number" to episode, "tmdbId" to "", "title" to "Episode $episode"))
                    }
                }
                val result = engine.runSelected(mediaInput, sourceId) { }
                if (!result.optBoolean("ok", false)) {
                    val failed = updated.map { if (it.id == sourceId) it.copy(status = SourceStatus.FAILED) else it }
                    sources.clear()
                    sources.addAll(failed)
                    _state.value = PlayerState.ManualSourceSelection(
                        failed,
                        selectedSourceId = sourceId,
                        candidate = null,
                        message = result.optString("error", "Source did not return a playable stream")
                    )
                    return@runCatching
                }
                val data = result.optJSONObject("data")
                val stream = data?.optJSONObject("stream")
                val streamUrl = stream?.let { findStreamUrl(it) }
                if (streamUrl == null) {
                    val failed = updated.map { if (it.id == sourceId) it.copy(status = SourceStatus.FAILED) else it }
                    sources.clear()
                    sources.addAll(failed)
                    _state.value = PlayerState.ManualSourceSelection(
                        failed,
                        selectedSourceId = sourceId,
                        candidate = null,
                        message = "No playable stream found"
                    )
                    return@runCatching
                }
                val success = updated.map { if (it.id == sourceId) it.copy(status = SourceStatus.SUCCESS) else it }
                val candidate = buildReadyState(data, streamUrl, success)
                sources.clear()
                sources.addAll(success)
                _state.value = PlayerState.ManualSourceSelection(success, selectedSourceId = sourceId, candidate = candidate, message = null)
            }.onFailure {
                val failed = updated.map { if (it.id == sourceId) it.copy(status = SourceStatus.FAILED) else it }
                sources.clear()
                sources.addAll(failed)
                _state.value = PlayerState.ManualSourceSelection(
                    failed,
                    selectedSourceId = sourceId,
                    candidate = null,
                    message = it.message ?: "Failed to probe source"
                )
            }
        }
    }

    fun confirmManualSourceSelection() {
        val current = _state.value as? PlayerState.ManualSourceSelection ?: return
        val candidate = current.candidate ?: return
        _state.value = candidate
        if (settings.value.subtitlesEnabled && candidate.subtitles.isNotEmpty()) {
            downloadAndParseSubtitles(candidate.subtitles)
        }
    }

    fun selectSubtitle(language: String) {
        viewModelScope.launch {
            settingsPrefs.setSubtitlesEnabled(true)
            settingsPrefs.setDefaultSubtitleLanguage(language)
            _selectedSubtitleLang.value = language
            val state = _state.value
            if (state is PlayerState.Ready) {
                val track = state.subtitles.find { it.language == language || it.label == language }
                if (track != null) {
                    downloadAndParseSubtitles(listOf(track))
                }
            }
        }
    }

    fun disableSubtitles() {
        viewModelScope.launch {
            settingsPrefs.setSubtitlesEnabled(false)
            _selectedSubtitleLang.value = null
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

    fun setVideoBrightness(value: Int) {
        viewModelScope.launch { settingsPrefs.setVideoBrightness(value.coerceIn(10, 200)) }
    }

    fun setVideoContrast(value: Int) {
        viewModelScope.launch { settingsPrefs.setVideoContrast(value.coerceIn(50, 200)) }
    }

    fun setVideoSaturation(value: Int) {
        viewModelScope.launch { settingsPrefs.setVideoSaturation(value.coerceIn(0, 200)) }
    }

    fun setVideoHueRotate(value: Int) {
        viewModelScope.launch { settingsPrefs.setVideoHueRotate(value.coerceIn(-180, 180)) }
    }

    fun resetAdvancedColor() {
        viewModelScope.launch {
            settingsPrefs.setVideoBrightness(100)
            settingsPrefs.setVideoContrast(100)
            settingsPrefs.setVideoSaturation(100)
            settingsPrefs.setVideoHueRotate(0)
        }
    }

    fun setVolumeBoost(value: Int) {
        viewModelScope.launch { settingsPrefs.setVolumeBoost(value.coerceIn(100, 300)) }
    }

    fun setVideoScaleMode(value: String) {
        val normalized = value.lowercase()
        if (normalized !in setOf("fit", "fill", "stretch")) return
        viewModelScope.launch { settingsPrefs.setVideoScaleMode(normalized) }
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
            val settingsVal = settings.value
            val preferredLang = settingsVal.defaultSubtitleLanguage
            val track = if (!preferredLang.isNullOrBlank()) {
                tracks.find { it.language == preferredLang || it.label == preferredLang }
            } else null
            val selected = track ?: tracks.firstOrNull()
            if (selected == null) {
                Log.d("PlayerVM", "downloadSubtitles: no tracks available")
                return@launch
            }

            _selectedSubtitleLang.value = selected.language
            Log.d("PlayerVM", "downloadSubtitles: selected lang=${selected.language} url=${selected.url}")

            val cues = withContext(Dispatchers.IO) {
                try {
                    val raw = downloadSubtitleText(selected.url)
                    Log.d("PlayerVM", "downloadSubtitles: downloaded ${raw.length} chars")
                    val parsed = parseSubtitleText(raw)
                    Log.d("PlayerVM", "downloadSubtitles: parsed ${parsed.size} cues")
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
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
            .build()
        val response = httpClient.newCall(request).execute()
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
        val blocks = body.split(Regex("\n\\s*\n"))
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
            val endMs = if (timeMatch.groupValues.size >= 17) {
                timeToMs(
                    timeMatch.groupValues[9].toInt(),
                    timeMatch.groupValues[10].toInt(),
                    timeMatch.groupValues[11].toInt(),
                    timeMatch.groupValues[12].toInt()
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

    private suspend fun fetchTheIntroDbSegments(durationMs: Long, tidbKey: String?): List<SkipSegment>? = withContext(Dispatchers.IO) {
        val apiUrl = buildString {
            append("https://api.theintrodb.org/v3/media?tmdb_id=$tmdbId")
            if (mediaType == "tv" && season != null && episode != null) {
                append("&season=$season&episode=$episode")
            }
            if (durationMs > 0) {
                append("&duration_ms=$durationMs")
            }
        }

        val request = Request.Builder()
            .url(apiUrl)
            .apply {
                if (!tidbKey.isNullOrBlank()) {
                    header("Authorization", "Bearer $tidbKey")
                }
            }
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext null
                if (!response.isSuccessful) {
                    Log.w("PlayerVM", "TIDB skip segments failed with ${response.code}")
                    return@withContext emptyList()
                }
                parseSkipSegmentsFromTidb(JSONObject(response.body?.string().orEmpty()))
            }
        }.getOrElse {
            Log.e("PlayerVM", "Failed to fetch TIDB skip segments", it)
            emptyList()
        }
    }

    private suspend fun fetchFallbackSkipSegments(settings: SettingsEntity): List<SkipSegment>? {
        if (mediaType != "tv" || season == null || episode == null) return null

        val imdbId = runCatching { tmdbRepo.tvDetail(id).imdbId }.getOrNull()
        if (imdbId.isNullOrBlank()) return null

        fetchIntroDbTime(imdbId)?.let { introEndMs ->
            return listOf(SkipSegment(type = "intro", startMs = 0L, endMs = introEndMs))
        }

        if (!settings.febboxKey.isNullOrBlank()) {
            fetchFedSkipsTime(imdbId)?.let { introEndMs ->
                return listOf(SkipSegment(type = "intro", startMs = 0L, endMs = introEndMs))
            }
        }

        return null
    }

    private suspend fun fetchIntroDbTime(imdbId: String): Long? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.introdb.app/intro?imdb_id=$imdbId&season=$season&episode=$episode")
            .build()
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                JSONObject(response.body?.string().orEmpty()).optLong("end_ms").takeIf { it > 0 }
            }
        }.getOrNull()
    }

    private suspend fun fetchFedSkipsTime(imdbId: String): Long? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://fed-skips.pstream.mov/$imdbId/$season/$episode")
            .build()
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val raw = JSONObject(response.body?.string().orEmpty()).optString("introSkipTime")
                raw.removeSuffix("s").toLongOrNull()?.times(1000)
            }
        }.getOrNull()
    }

    private fun parseSkipSegmentsFromTidb(json: JSONObject): List<SkipSegment> {
        val types = listOf("intro", "recap", "credits", "preview")
        return buildList {
            types.forEach { type ->
                val items = json.optJSONArray(type) ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    add(
                        SkipSegment(
                            type = type,
                            startMs = item.optNullableLong("start_ms"),
                            endMs = item.optNullableLong("end_ms"),
                        )
                    )
                }
            }
        }
    }

    private fun buildSkipSegmentsCacheKey(): String? {
        return when {
            mediaType == "movie" -> "skip-movie-$tmdbId"
            mediaType == "tv" && season != null && episode != null -> "skip-tv-$tmdbId-$season-$episode"
            else -> null
        }
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        return if (isNull(key)) null else optLong(key)
    }

    private fun String.decodeRouteParam(): String {
        return runCatching { URLDecoder.decode(this, "UTF-8") }.getOrDefault(this)
    }

    private fun handleEvent(evt: JSONObject) {
        when (evt.optString("event")) {
            "init" -> {
                val ids = evt.optJSONArray("sourceIds") ?: return
                sources.clear()
                for (i in 0 until ids.length()) sources.add(SourceResult(ids.getString(i), SourceStatus.TRYING))
                _state.value = PlayerState.Scraping(sources.toList())
            }
            "start" -> {
                val id = evt.optString("id")
                updateSource(id, SourceStatus.TRYING)
            }
            "update" -> {
                val id = evt.optString("id")
                val status = when (evt.optString("status")) {
                    "success" -> SourceStatus.SUCCESS
                    "failure", "notfound" -> SourceStatus.FAILED
                    else -> SourceStatus.TRYING
                }
                updateSource(id, status)
            }
        }
    }

    private fun updateSource(id: String, status: SourceStatus) {
        val idx = sources.indexOfFirst { it.id == id }
        if (idx >= 0) sources[idx] = SourceResult(id, status)
        else sources.add(SourceResult(id, status))
        if (_state.value is PlayerState.Scraping) {
            _state.value = PlayerState.Scraping(sources.toList())
        }
    }

    private fun buildReadyState(
        data: JSONObject?,
        streamUrl: String,
        sourceList: List<SourceResult> = sources.toList()
    ): PlayerState.Ready {
        val stream = data?.optJSONObject("stream")
        val headers = parseStreamHeaders(streamUrl)
        val subtitleTracks = parseSubtitles(stream)
        val sourceId = data?.optString("sourceId")?.takeIf { it.isNotBlank() }
        val embedId = data?.optString("embedId")?.takeIf { it.isNotBlank() }
        return PlayerState.Ready(streamUrl, headers, subtitleTracks, sourceList, sourceId, embedId)
    }

    private fun findStreamUrl(stream: JSONObject): String? {
        return when (stream.optString("type")) {
            "hls" -> stream.optString("playlist").takeIf { it.isNotEmpty() }
            "file" -> {
                val qualities = stream.optJSONObject("qualities") ?: return null
                // prefer highest quality
                for (q in listOf("4k", "1080", "720", "480", "360", "unknown")) {
                    val url = qualities.optJSONObject(q)?.optString("url")
                    if (!url.isNullOrEmpty()) return url
                }
                null
            }
            else -> null
        }
    }

    private fun parseStreamHeaders(url: String): Map<String, String> {
        return try {
            val encodedHeaders = android.net.Uri.parse(url).getQueryParameter("headers") ?: return emptyMap()
            val obj = org.json.JSONObject(encodedHeaders)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (e: Exception) { emptyMap() }
    }

    private fun parseSubtitles(stream: JSONObject?): List<SubtitleTrack> {
        val arr = stream?.optJSONArray("captions") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = obj.optString("url").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val label = obj.optString("language", "Unknown")
            val language = obj.optString("langIso").takeIf { it.isNotBlank() } ?: label
            val type = obj.optString("type", "vtt")
            SubtitleTrack(label, url, language, type)
        }
    }
}

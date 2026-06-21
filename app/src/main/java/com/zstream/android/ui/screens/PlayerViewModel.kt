package com.zstream.android.ui.screens

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.provider.ProviderEngine
import com.zstream.android.data.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.data.local.entity.SettingsEntity

enum class SourceStatus { TRYING, SUCCESS, FAILED }
data class SourceResult(val id: String, val status: SourceStatus)

sealed class PlayerState {
    object Idle : PlayerState()
    data class Scraping(val sources: List<SourceResult>) : PlayerState()
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

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val engine: ProviderEngine,
    private val settingsPrefs: SettingsPreferences,
    private val bookmarkRepo: BookmarkRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val settings = settingsPrefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsEntity())

    private val id = savedState.get<Int>("id") ?: 0
    val mediaType = savedState.get<String>("mediaType") ?: "movie"
    val season = savedState.get<Int>("season").takeIf { it != -1 }
    val episode = savedState.get<Int>("episode").takeIf { it != -1 }
    val title = savedState.get<String>("title") ?: ""
    val year = savedState.get<Int>("year") ?: 0
    val poster = savedState.get<String>("poster")?.takeIf { it.isNotBlank() }
    val tmdbId = id.toString()
    val seasonId = season?.toString()
    val episodeId = episode?.toString()

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state = _state.asStateFlow()

    private val _subtitleCues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    val subtitleCues = _subtitleCues.asStateFlow()

    private val _selectedSubtitleLang = MutableStateFlow<String?>(null)
    val selectedSubtitleLang = _selectedSubtitleLang.asStateFlow()

    val isBookmarked = bookmarkRepo.observeBookmark(tmdbId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val sources = mutableListOf<SourceResult>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    init { load() }

    fun getProxyPort() = engine.proxy.port

    fun load() {
        sources.clear()
        _state.value = PlayerState.Scraping(emptyList())

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

                val result = engine.runAll(mediaInput) { evt ->
                    handleEvent(evt)
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

                val headers = parseStreamHeaders(streamUrl)
                val subtitleTracks = parseSubtitles(stream)
                val sourceId = data?.optString("sourceId")?.takeIf { it.isNotBlank() }
                val embedId = data?.optString("embedId")?.takeIf { it.isNotBlank() }
                Log.d("PlayerVM", "parsed headers: $headers, subtitles: ${subtitleTracks.size}")
                _state.value = PlayerState.Ready(streamUrl, headers, subtitleTracks, sources.toList(), sourceId, embedId)

                // Mirror web behavior: subtitle on/off is a local persisted preference.
                if (settings.value.subtitlesEnabled && subtitleTracks.isNotEmpty()) {
                    downloadAndParseSubtitles(subtitleTracks)
                }
            }.onFailure {
                Log.e("PlayerVM", "error: ${it.message}", it)
                _state.value = PlayerState.Error(it.message ?: "Unknown error", sources.toList())
            }
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
        // Don't overwrite Ready/Error state with Scraping updates
        if (_state.value is PlayerState.Scraping) {
            _state.value = PlayerState.Scraping(sources.toList())
        }
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

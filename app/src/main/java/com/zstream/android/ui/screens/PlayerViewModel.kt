package com.zstream.android.ui.screens

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.provider.ProviderEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

enum class SourceStatus { TRYING, SUCCESS, FAILED }
data class SourceResult(val id: String, val status: SourceStatus)

sealed class PlayerState {
    object Idle : PlayerState()
    data class Scraping(val sources: List<SourceResult>) : PlayerState()
    data class Ready(val streamUrl: String, val headers: Map<String, String>, val subtitles: List<SubtitleTrack>, val sources: List<SourceResult>) : PlayerState()
    data class Error(val message: String, val sources: List<SourceResult>) : PlayerState()
}

data class SubtitleTrack(val label: String, val url: String, val language: String)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val engine: ProviderEngine,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val id = savedState.get<Int>("id") ?: 0
    private val mediaType = savedState.get<String>("mediaType") ?: "movie"
    private val season = savedState.get<Int>("season").takeIf { it != -1 }
    private val episode = savedState.get<Int>("episode").takeIf { it != -1 }
    val title = savedState.get<String>("title") ?: ""
    val year = savedState.get<Int>("year") ?: 0

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state = _state.asStateFlow()

    private val sources = mutableListOf<SourceResult>()

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
                Log.d("PlayerVM", "parsed headers: $headers")
                _state.value = PlayerState.Ready(streamUrl, headers, parseSubtitles(stream), sources.toList())
            }.onFailure {
                Log.e("PlayerVM", "error: ${it.message}", it)
                _state.value = PlayerState.Error(it.message ?: "Unknown error", sources.toList())
            }
        }
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
        _state.value = PlayerState.Scraping(sources.toList())
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
            SubtitleTrack(obj.optString("language", "Unknown"), url, obj.optString("langIso", ""))
        }
    }
}

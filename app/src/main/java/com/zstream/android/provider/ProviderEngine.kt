package com.zstream.android.provider

import android.app.Activity
import android.util.Log
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ProviderEngine"

/**
 * Orchestrates stream resolution and native playback.
 *
 * Pipeline:
 *   1. [StreamExtractor] loads a [StreamSource]'s embed page in a headless WebView and
 *      captures the signed stream URL (discovery only).
 *   2. The source's [StreamSource.playbackHeaders] are handed to [proxy] so every upstream
 *      fetch carries the headers the CDN edge requires.
 *   3. The caller plays the URL with ExoPlayer through [WebViewDataSource] → [proxy].
 *
 * Adding a new provider: implement [StreamSource], add it to [sources]. Nothing else changes.
 */
@Singleton
class ProviderEngine @Inject constructor() {

    private lateinit var extractor: StreamExtractor

    /** Started once; reused for every playback. Holds the active source's headers. */
    val proxy = WebViewProxyServer().also { it.start() }

    /** Ordered list of providers to try. Add new [StreamSource]s here. */
    private val sources: List<StreamSource> = listOf(VidlinkSource)

    fun init(activity: Activity) {
        extractor = StreamExtractor(activity)
    }

    /**
     * Resolve a playable stream for [mediaInput], emitting progress [onEvent]s for the UI.
     *
     * Result JSON (kept stable for PlayerViewModel):
     *   {"ok":true,"data":{"sourceId":..,"stream":{"type":"hls","playlist":URL,"captions":[]}}}
     *   {"ok":false,"error":..}
     */
    suspend fun runAll(mediaInput: Map<String, Any>, onEvent: (JSONObject) -> Unit): JSONObject {
        val media = parseMedia(mediaInput)
            ?: return JSONObject("""{"ok":false,"error":"missing tmdbId"}""")

        onEvent(JSONObject(JSONObject().apply {
            put("event", "init")
            put("sourceIds", org.json.JSONArray(sources.map { it.id }))
        }.toString()))

        for (source in sources) {
            onEvent(JSONObject("""{"event":"start","id":"${source.id}"}"""))
            val url = runCatching { extractor.extract(source, media) }
                .onFailure { Log.e(TAG, "[${source.id}] extract failed: ${it.message}") }
                .getOrNull()

            if (url != null) {
                // Configure the proxy with this source's required headers before playback.
                proxy.playbackHeaders = source.playbackHeaders
                onEvent(JSONObject("""{"event":"update","id":"${source.id}","status":"success"}"""))
                val stream = JSONObject().apply {
                    put("type", "hls")
                    put("playlist", url)
                    put("captions", org.json.JSONArray())
                }
                return JSONObject().apply {
                    put("ok", true)
                    put("data", JSONObject().apply {
                        put("sourceId", source.id)
                        put("stream", stream)
                    })
                }
            }
            onEvent(JSONObject("""{"event":"update","id":"${source.id}","status":"notfound"}"""))
        }
        return JSONObject("""{"ok":false,"error":"No playable stream found"}""")
    }

    private fun parseMedia(input: Map<String, Any>): MediaRequest? {
        val tmdbId = input["tmdbId"] as? String ?: return null
        val isShow = (input["type"] as? String) == "show"
        val season = (input["season"] as? Map<*, *>)?.get("number") as? Int
        val episode = (input["episode"] as? Map<*, *>)?.get("number") as? Int
        return MediaRequest(
            type = if (isShow) MediaRequest.Type.SHOW else MediaRequest.Type.MOVIE,
            tmdbId = tmdbId,
            season = season,
            episode = episode,
        )
    }
}

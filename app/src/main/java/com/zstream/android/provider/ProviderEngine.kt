package com.zstream.android.provider

import android.app.Activity
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
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

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun init(activity: Activity) {
        extractor = StreamExtractor(activity)
    }

    /**
     * Resolve a playable stream for [mediaInput], emitting progress [onEvent]s for the UI.
     *
     * Result JSON (kept stable for PlayerViewModel):
     *   {"ok":true,"data":{"sourceId":..,"stream":{"type":"hls","playlist":URL,"captions":[...]}}}
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

                val captions = fetchCaptions()

                val stream = JSONObject().apply {
                    put("type", "hls")
                    put("playlist", url)
                    put("captions", captions)
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

    /**
     * Extract caption metadata from the vidlink API response.
     *
     * The WebView already made this API call during page load (it shows up as
     * a request to vidlink.pro/api/b/…). The URL was captured by [StreamExtractor]
     * and stored in [StreamExtractor.vidlinkApiUrl]. We replay the exact same
     * request here to get the full JSON response body (the WebView's
     * shouldInterceptRequest only sees the URL, not the body).
     */
    private suspend fun fetchCaptions(): JSONArray = withContext(Dispatchers.IO) {
        val apiUrl = extractor.vidlinkApiUrl
        Log.d(TAG, "fetchCaptions: captured API URL=$apiUrl")
        if (apiUrl == null) {
            Log.w(TAG, "fetchCaptions: no vidlink API URL captured by WebView")
            return@withContext JSONArray()
        }

        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                .header("Referer", "https://vidlink.pro/")
                .header("Origin", "https://vidlink.pro")
                .build()
            Log.d(TAG, "fetchCaptions: calling $apiUrl")
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
            Log.d(TAG, "fetchCaptions: response code=${response.code}, body length=${body?.length}")

            val json = body?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (json == null) {
                Log.w(TAG, "fetchCaptions: failed to parse JSON response")
                return@withContext JSONArray()
            }

            val stream = json.optJSONObject("stream")
            if (stream == null) {
                Log.w(TAG, "fetchCaptions: no 'stream' key in API response: ${json.keys().asSequence().toList()}")
                return@withContext JSONArray()
            }

            val captions = stream.optJSONArray("captions")
            if (captions == null || captions.length() == 0) {
                Log.d(TAG, "fetchCaptions: no captions in stream data")
                return@withContext JSONArray()
            }

            Log.d(TAG, "fetchCaptions: found ${captions.length()} caption tracks")
            val mapped = JSONArray()
            for (i in 0 until captions.length()) {
                val c = captions.optJSONObject(i) ?: continue
                val url = c.optString("url").takeIf { it.isNotBlank() } ?: continue
                val lang = c.optString("language", "Unknown")
                val langIso = c.optString("langIso", "")
                val type = c.optString("type", "vtt")
                Log.d(TAG, "fetchCaptions: caption #$i lang=$lang url=$url")
                mapped.put(JSONObject().apply {
                    put("url", url)
                    put("language", lang)
                    put("langIso", langIso)
                    put("type", type)
                })
            }
            mapped
        } catch (e: Exception) {
            Log.e(TAG, "fetchCaptions failed: ${e.message}", e)
            JSONArray()
        }
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

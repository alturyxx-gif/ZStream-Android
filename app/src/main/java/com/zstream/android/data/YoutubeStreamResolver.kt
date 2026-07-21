package com.zstream.android.data

import com.zstream.android.data.model.ShortsStreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private data class InnertubeClient(
    val name: String,
    val version: String,
    val clientNameId: String,
    val extra: Map<String, Any>,
    val userAgent: String,
)

private val CLIENTS = listOf(
    InnertubeClient(
        name = "IOS",
        version = "21.02.3",
        clientNameId = "5",
        extra = mapOf(
            "deviceMake" to "Apple",
            "deviceModel" to "iPhone16,2",
            "osName" to "iPhone",
            "osVersion" to "18.3.2.22D82",
        ),
        userAgent = "com.google.ios.youtube/21.02.3 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
    ),
    InnertubeClient(
        name = "ANDROID_VR",
        version = "1.65.10",
        clientNameId = "28",
        extra = mapOf(
            "deviceMake" to "Oculus",
            "deviceModel" to "Quest 3",
            "androidSdkVersion" to 32,
            "osName" to "Android",
            "osVersion" to "12L",
        ),
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
    ),
)

private const val CLIP_WINDOW_SEC = 5 * 60
private const val CLIP_THRESHOLD_SEC = 6 * 60

@Singleton
class YoutubeStreamResolver @Inject constructor() {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val webUserAgent =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    suspend fun resolve(videoId: String): ShortsStreamResponse = withContext(Dispatchers.IO) {
        val (apiKey, visitorData) = fetchBootstrap(videoId)

        var playerResponse: JSONObject? = null
        for (client in CLIENTS) {
            val response = fetchPlayerResponse(videoId, apiKey, visitorData, client)
            val status = response.optJSONObject("playabilityStatus")?.optString("status")
            if (status == "OK") {
                playerResponse = response
                break
            }
        }
        val response = checkNotNull(playerResponse) { "Video not playable for $videoId (all clients failed)" }
        val usedUserAgent = CLIENTS.first { it.name == response.getUsedClientName() }.userAgent

        val adaptiveFormats = response
            .getJSONObject("streamingData")
            .getJSONArray("adaptiveFormats")

        var bestVideo: JSONObject? = null
        var bestAudio: JSONObject? = null

        for (i in 0 until adaptiveFormats.length()) {
            val format = adaptiveFormats.getJSONObject(i)
            val mimeType = format.optString("mimeType")
            if (format.optString("url").isEmpty()) continue

            if (mimeType.startsWith("video/mp4")) {
                val height = format.optInt("height")
                if (height !in 1..1080) continue
                val isAvc = mimeType.contains("avc1")
                val current = bestVideo
                val currentIsAvc = current?.optString("mimeType")?.contains("avc1") == true
                val better = current == null ||
                    (isAvc && !currentIsAvc) ||
                    (isAvc == currentIsAvc && height > current.optInt("height"))
                if (better) bestVideo = format
            } else if (mimeType.startsWith("audio/mp4")) {
                val bitrate = format.optInt("bitrate")
                if (bestAudio == null || bitrate > bestAudio!!.optInt("bitrate")) {
                    bestAudio = format
                }
            }
        }

        val video = checkNotNull(bestVideo) { "No suitable video format for $videoId" }
        val audio = checkNotNull(bestAudio) { "No suitable audio format for $videoId" }
        val width = video.optInt("width")
        val height = video.optInt("height")

        val lengthSec = response.optJSONObject("videoDetails")?.optString("lengthSeconds")?.toLongOrNull() ?: 0L
        var clipStartMs = 0L
        var clipEndMs: Long? = null
        if (lengthSec > CLIP_THRESHOLD_SEC) {
            val maxStartSec = lengthSec - CLIP_WINDOW_SEC
            val startSec = Random.nextLong(0, maxStartSec.coerceAtLeast(1))
            clipStartMs = startSec * 1000
            clipEndMs = (startSec + CLIP_WINDOW_SEC) * 1000
        }

        ShortsStreamResponse(
            videoUrl = video.getString("url"),
            audioUrl = audio.getString("url"),
            mimeType = "video/mp4",
            width = width,
            height = height,
            isVertical = height > width,
            expiresAtEpochSec = System.currentTimeMillis() / 1000 + 4 * 3600,
            userAgent = usedUserAgent,
            clipStartMs = clipStartMs,
            clipEndMs = clipEndMs,
            videoContentLength = video.optString("contentLength").toLongOrNull() ?: 0L,
            audioContentLength = audio.optString("contentLength").toLongOrNull() ?: 0L,
        )
    }

    private fun fetchBootstrap(videoId: String): Pair<String, String?> {
        val request = Request.Builder()
            .url("https://www.youtube.com/watch?v=$videoId")
            .header("User-Agent", webUserAgent)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val body = runCatching {
            http.newCall(request).execute().use { it.body?.string().orEmpty() }
        }.getOrDefault("")

        val apiKey = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: DEFAULT_API_KEY
        val visitorData = Regex("\"visitorData\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        return apiKey to visitorData
    }

    private fun fetchPlayerResponse(
        videoId: String,
        apiKey: String,
        visitorData: String?,
        client: InnertubeClient,
    ): JSONObject {
        val clientJson = JSONObject().apply {
            put("clientName", client.name)
            put("clientVersion", client.version)
            client.extra.forEach { (k, v) -> put(k, v) }
            put("userAgent", client.userAgent)
            put("hl", "en")
            put("gl", "US")
            if (visitorData != null) put("visitorData", visitorData)
        }
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().apply { put("client", clientJson) })
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?key=$apiKey")
            .header("Content-Type", "application/json")
            .header("User-Agent", client.userAgent)
            .header("X-Youtube-Client-Name", client.clientNameId)
            .header("X-Youtube-Client-Version", client.version)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            json.put("_usedClient", client.name)
            return json
        }
    }

    private fun JSONObject.getUsedClientName(): String = getString("_usedClient")

    companion object {
        private const val DEFAULT_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    }
}

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

@Singleton
class YoutubeStreamResolver @Inject constructor() {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val vrUserAgent =
        "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    private val webUserAgent =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    suspend fun resolve(videoId: String): ShortsStreamResponse = withContext(Dispatchers.IO) {
        val (apiKey, visitorData) = fetchBootstrap(videoId)
        val playerResponse = fetchPlayerResponse(videoId, apiKey, visitorData)

        val status = playerResponse.optJSONObject("playabilityStatus")?.optString("status")
        check(status == "OK") { "Video not playable ($status) for $videoId" }

        val adaptiveFormats = playerResponse
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

        ShortsStreamResponse(
            videoUrl = video.getString("url"),
            audioUrl = audio.getString("url"),
            mimeType = "video/mp4",
            width = width,
            height = height,
            isVertical = height > width,
            expiresAtEpochSec = System.currentTimeMillis() / 1000 + 4 * 3600,
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

    private fun fetchPlayerResponse(videoId: String, apiKey: String, visitorData: String?): JSONObject {
        val client = JSONObject().apply {
            put("clientName", "ANDROID_VR")
            put("clientVersion", "1.65.10")
            put("deviceMake", "Oculus")
            put("deviceModel", "Quest 3")
            put("androidSdkVersion", 32)
            put("userAgent", vrUserAgent)
            put("osName", "Android")
            put("osVersion", "12L")
            put("hl", "en")
            put("gl", "US")
            if (visitorData != null) put("visitorData", visitorData)
        }
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().apply { put("client", client) })
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?key=$apiKey")
            .header("Content-Type", "application/json")
            .header("User-Agent", vrUserAgent)
            .header("X-Youtube-Client-Name", "28")
            .header("X-Youtube-Client-Version", "1.65.10")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    companion object {
        private const val DEFAULT_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    }
}

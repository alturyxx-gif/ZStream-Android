package com.zstream.android.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class RssVideo(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val publishedAtEpochSec: Long,
)

private val interviewKeywords = listOf(
    "interview",
    "press junket",
    "junket",
    "q&a",
    "in conversation with",
    "red carpet",
    "press tour",
    "exclusive chat",
    "sits down with",
)

fun looksLikeInterview(title: String): Boolean {
    val lower = title.lowercase()
    return interviewKeywords.any { lower.contains(it) }
}

@Singleton
class ShortsRssFetcher @Inject constructor() {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    suspend fun fetchChannel(channelId: String): List<RssVideo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
            .header("User-Agent", userAgent)
            .header("Accept", "application/xml")
            .build()

        val body = runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                response.body?.string().orEmpty()
            }
        }.getOrDefault("")
        if (body.isEmpty()) return@withContext emptyList()

        parseFeed(body)
    }

    private fun parseFeed(xml: String): List<RssVideo> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(xml.reader())

        val out = mutableListOf<RssVideo>()
        var videoId: String? = null
        var title: String? = null
        var thumbnailUrl: String? = null
        var published: String? = null
        var inEntry = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> {
                        inEntry = true
                        videoId = null; title = null; thumbnailUrl = null; published = null
                    }
                    "videoId" -> if (inEntry) videoId = parser.nextText()
                    "title" -> if (inEntry) title = parser.nextText()
                    "published" -> if (inEntry) published = parser.nextText()
                    "thumbnail" -> if (inEntry) thumbnailUrl = parser.getAttributeValue(null, "url")
                }
                XmlPullParser.END_TAG -> if (parser.name == "entry") {
                    inEntry = false
                    val id = videoId
                    if (!id.isNullOrEmpty()) {
                        out.add(
                            RssVideo(
                                videoId = id,
                                title = title.orEmpty(),
                                thumbnailUrl = thumbnailUrl ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                                publishedAtEpochSec = parsePublished(published),
                            )
                        )
                    }
                }
            }
            eventType = parser.next()
        }
        return out
    }

    private fun parsePublished(value: String?): Long {
        if (value == null) return 0L
        return runCatching {
            java.time.Instant.parse(value).epochSecond
        }.getOrDefault(0L)
    }
}

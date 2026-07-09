package com.zstream.android.data

import android.util.Log
import com.zstream.android.data.local.dao.SkipSegmentDao
import com.zstream.android.data.local.entity.SkipSegmentEntity
import com.zstream.android.ui.screens.SkipSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SkipSegmentRepository"

/**
 * Owns skip-segment network fetch + Room caching. Used by both PlayerViewModel (live playback)
 * and DownloadRepository (pre-warm cache on download completion for offline use).
 */
@Singleton
class SkipSegmentRepository @Inject constructor(
    private val dao: SkipSegmentDao,
    httpClient: OkHttpClient,
    private val tmdbRepo: TmdbRepository,
) {
    // The app-wide client's 15s timeouts are tuned for API calls, not for a request fired right
    // after a large download finishes (radio/connection pool still busy) — give this one more room.
    private val httpClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun buildMediaKey(tmdbId: String, mediaType: String, season: Int?, episode: Int?): String? = when {
        mediaType == "movie" -> "skip-movie-$tmdbId"
        mediaType == "tv" && season != null && episode != null -> "skip-tv-$tmdbId-$season-$episode"
        else -> null
    }

    /** Reads cache first-class only as a fallback: tries network, replaces cache on success, falls back to cache on failure. */
    suspend fun getSegments(
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        durationMs: Long,
        tidbKey: String?,
        febboxKey: String?,
    ): List<SkipSegment> {
        val mediaKey = buildMediaKey(tmdbId, mediaType, season, episode) ?: return emptyList()

        val fresh = fetchTheIntroDbSegments(tmdbId, mediaType, season, episode, durationMs, tidbKey)
            ?: fetchFallbackSkipSegments(tmdbId, mediaType, season, episode, febboxKey)

        if (fresh != null) {
            dao.replaceForMedia(mediaKey, fresh.map { it.toEntity(mediaKey) })
            return fresh
        }

        return dao.getForMedia(mediaKey).map { it.toDomain() }
    }

    /** Fire-and-forget cache warm; never throws, called after a download completes. */
    suspend fun warmCache(
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        durationMs: Long,
        tidbKey: String?,
        febboxKey: String?,
    ) {
        runCatching {
            getSegments(tmdbId, mediaType, season, episode, durationMs, tidbKey, febboxKey)
        }.onFailure { Log.w(TAG, "Failed to warm skip segment cache for $tmdbId", it) }
    }

    suspend fun refreshAfterSubmit(
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        durationMs: Long,
        tidbKey: String?,
        febboxKey: String?,
    ): List<SkipSegment> = getSegments(tmdbId, mediaType, season, episode, durationMs, tidbKey, febboxKey)

    /** One retry after a short delay — a single timeout right after a download finishes is common while the radio/connection pool settles. */
    private fun executeWithRetry(request: Request): okhttp3.Response {
        return try {
            httpClient.newCall(request).execute()
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "Skip segment request timed out, retrying once: ${request.url}")
            Thread.sleep(2000)
            httpClient.newCall(request).execute()
        }
    }

    private suspend fun fetchTheIntroDbSegments(
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        durationMs: Long,
        tidbKey: String?,
    ): List<SkipSegment>? = withContext(Dispatchers.IO) {
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
            executeWithRetry(request).use { response ->
                if (response.code == 404) return@withContext null
                if (!response.isSuccessful) {
                    Log.w(TAG, "TIDB skip segments failed with ${response.code}")
                    // null (not emptyList) — a transient server error must not overwrite the cache with "no segments".
                    return@withContext null
                }
                parseSkipSegmentsFromTidb(JSONObject(response.body?.string().orEmpty()))
            }
        }.getOrElse {
            Log.e(TAG, "Failed to fetch TIDB skip segments (offline/timeout?) — falling back to cache", it)
            // null (not emptyList) — getSegments() must fall back to the Room cache, not wipe it.
            null
        }
    }

    private suspend fun fetchFallbackSkipSegments(
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        febboxKey: String?,
    ): List<SkipSegment>? {
        if (mediaType != "tv" || season == null || episode == null) return null
        val id = tmdbId.toIntOrNull() ?: return null

        val imdbId = runCatching { tmdbRepo.tvDetail(id).imdbId }.getOrNull()
        if (imdbId.isNullOrBlank()) return null

        fetchIntroDbTime(imdbId, season, episode)?.let { introEndMs ->
            return listOf(SkipSegment(type = "intro", startMs = 0L, endMs = introEndMs))
        }

        if (!febboxKey.isNullOrBlank()) {
            fetchFedSkipsTime(imdbId, season, episode)?.let { introEndMs ->
                return listOf(SkipSegment(type = "intro", startMs = 0L, endMs = introEndMs))
            }
        }

        return null
    }

    private suspend fun fetchIntroDbTime(imdbId: String, season: Int, episode: Int): Long? = withContext(Dispatchers.IO) {
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

    private suspend fun fetchFedSkipsTime(imdbId: String, season: Int, episode: Int): Long? = withContext(Dispatchers.IO) {
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

    private fun JSONObject.optNullableLong(key: String): Long? {
        return if (isNull(key)) null else optLong(key)
    }

    private fun SkipSegment.toEntity(mediaKey: String): SkipSegmentEntity = SkipSegmentEntity(
        id = UUID.randomUUID().toString(),
        mediaKey = mediaKey,
        segmentType = type,
        startMs = startMs,
        endMs = endMs,
        source = "network",
    )

    private fun SkipSegmentEntity.toDomain(): SkipSegment = SkipSegment(
        type = segmentType,
        startMs = startMs,
        endMs = endMs,
    )
}

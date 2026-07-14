package com.zstream.android.data

import com.zstream.android.data.local.dao.CertificationDao
import com.zstream.android.data.local.entity.CertificationEntity
import com.zstream.android.data.model.Media
import com.zstream.android.data.remote.TmdbApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches TMDB certification (age rating) lookups and decides whether a title should be
 * hidden under Kids Mode. Unrated/unknown titles are treated as blocked, per product decision.
 */
@Singleton
class CertificationRepository @Inject constructor(
    private val api: TmdbApi,
    private val dao: CertificationDao,
) {
    companion object {
        private const val RATING_COUNTRY = "US"
        private val MOVIE_BLOCKED = setOf("R", "NC-17")
        private val TV_BLOCKED = setOf("TV-MA")
        private const val CONCURRENCY = 6
    }

    private fun cacheId(tmdbId: Int, mediaType: String) = "${mediaType}_$tmdbId"

    private suspend fun fetchCertification(tmdbId: Int, mediaType: String): String {
        return runCatching {
            if (mediaType == "movie") {
                val entry = api.movieReleaseDates(tmdbId).results
                    .firstOrNull { it.country == RATING_COUNTRY }
                    ?.releaseDates
                    ?.firstOrNull { !it.certification.isNullOrBlank() }
                entry?.certification ?: ""
            } else {
                val entry = api.tvContentRatings(tmdbId).results
                    .firstOrNull { it.country == RATING_COUNTRY }
                entry?.rating ?: ""
            }
        }.getOrDefault("")
    }

    internal suspend fun getCertification(tmdbId: Int, mediaType: String): String {
        val id = cacheId(tmdbId, mediaType)
        dao.get(id)?.let { return it.certification }
        val certification = fetchCertification(tmdbId, mediaType)
        dao.upsert(CertificationEntity(id, certification))
        return certification
    }

    private fun isBlocked(mediaType: String, certification: String): Boolean {
        if (certification.isBlank()) return true
        val blockedSet = if (mediaType == "movie") MOVIE_BLOCKED else TV_BLOCKED
        return certification in blockedSet
    }

    suspend fun isAllowedForKids(tmdbId: Int, mediaType: String): Boolean {
        val certification = getCertification(tmdbId, mediaType)
        return !isBlocked(mediaType, certification)
    }

    /** Filters out mature/unrated titles when [enabled], preserving input order. No-op when disabled. */
    suspend fun filterForKids(items: List<Media>, enabled: Boolean): List<Media> {
        if (!enabled || items.isEmpty()) return items
        val semaphore = Semaphore(CONCURRENCY)
        return coroutineScope {
            items.map { media ->
                async { media to semaphore.withPermit { isAllowedForKids(media.id, media.type) } }
            }.awaitAll().filter { it.second }.map { it.first }
        }
    }
}

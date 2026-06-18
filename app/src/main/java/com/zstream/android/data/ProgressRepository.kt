package com.zstream.android.data

import android.util.Log
import com.zstream.android.Urls
import com.zstream.android.data.local.dao.ProgressDao
import com.zstream.android.data.local.entity.ProgressEntity
import com.zstream.android.data.remote.BackendApi
import com.zstream.android.data.remote.ProgressInput
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ProgressRepository"

@Singleton
class ProgressRepository @Inject constructor(
    private val progressDao: ProgressDao,
    private val accountRepo: AccountRepository,
    private val api: BackendApi,
    private val tmdbRepo: TmdbRepository,
) {
    /**
     * Get progress from local cache first, then sync with backend if available
     */
    fun observeProgress(tmdbId: String): Flow<ProgressEntity?> {
        return progressDao.observeByTmdbId(tmdbId)
    }

    /**
     * Get all progress items (movies watched)
     */
    fun observeAllProgress(): Flow<List<ProgressEntity>> {
        return progressDao.observeAll()
    }

    /**
     * Get progress for movies only
     */
    fun observeMovieProgress(): Flow<List<ProgressEntity>> {
        return progressDao.observeMovies()
    }

    /**
     * Get progress for TV shows only
     */
    fun observeShowProgress(): Flow<List<ProgressEntity>> {
        return progressDao.observeShows()
    }

    /**
     * Update local progress and sync with backend
     */
    suspend fun updateProgress(
        tmdbId: String,
        title: String,
        type: String,
        watched: Int,
        duration: Int,
        year: Int? = null,
        posterPath: String? = null,
        episodeId: String? = null,
        seasonId: String? = null,
        episodeNumber: Int? = null,
        seasonNumber: Int? = null,
    ) {
        // Extract relative path if it's a full URL
        val safePoster = if (posterPath.isNullOrBlank()) null else posterPath
        val relativePoster = if (safePoster != null && safePoster.startsWith("http")) {
            if (safePoster.contains("/t/p/")) safePoster.substringAfterLast("/") else safePoster
        } else {
            safePoster
        }

        // Prevents placeholder images for movie/show posters
        var finalPoster = if (relativePoster != null && !relativePoster.startsWith("/") && !relativePoster.startsWith("http")) {
            "/$relativePoster"
        } else {
            relativePoster
        }

        // Prevents placeholder images for movie/show posters
        if (finalPoster == null) {
            val id = tmdbId.toIntOrNull()
            if (id != null) {
                try {
                    val path = if (type == "movie") {
                        tmdbRepo.movieDetail(id).posterPath
                    } else {
                        tmdbRepo.tvDetail(id).posterPath
                    }
                    if (!path.isNullOrBlank()) {
                        finalPoster = if (path.startsWith("/")) path else "/$path"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch poster for $tmdbId", e)
                }
            }
        }

        val entity = ProgressEntity(
            tmdbId = tmdbId,
            title = title,
            type = type,
            watched = watched,
            duration = duration,
            year = year,
            posterPath = finalPoster,
            episodeId = episodeId,
            seasonId = seasonId,
            episodeNumber = episodeNumber,
            seasonNumber = seasonNumber,
            updatedAt = System.currentTimeMillis(),
        )

        // Update local cache first
        progressDao.insert(entity)

        // Try to sync with backend
        try {
            val session = accountRepo.currentSession ?: run {
                Log.w(TAG, "No active session, skipping backend sync")
                return
            }

            val input = ProgressInput(
                tmdbId = tmdbId,
                meta = com.zstream.android.data.remote.ProgressMeta(
                    title = title,
                    year = year,
                    poster = toFullPosterUrl(finalPoster),
                    type = type,
                ),
                watched = watched,
                duration = duration,
                episodeId = episodeId,
                seasonId = seasonId,
                episodeNumber = episodeNumber,
                seasonNumber = seasonNumber,
            )

            api.setProgress(session.userId, tmdbId, "Bearer ${session.token}", input)
            Log.d(TAG, "Successfully synced progress for $tmdbId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync progress with backend", e)
            // Local cache is already updated, sync will retry later
        }
    }

    /**
     * Remove progress entry
     */
    suspend fun removeProgress(tmdbId: String) {
        progressDao.deleteByTmdbId(tmdbId)

        // Try to remove from backend
        try {
            val session = accountRepo.currentSession ?: return
            api.removeProgress(session.userId, tmdbId, "Bearer ${session.token}")
            Log.d(TAG, "Successfully removed progress for $tmdbId from backend")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove progress from backend", e)
        }
    }

    /**
     * Get all progress items for sync (direct query, not Flow)
     */
    suspend fun getAllProgressForSync(): List<ProgressEntity> {
        return progressDao.getAllSync()
    }

    /**
     * Sync all local progress with backend (for periodic sync)
     */
    suspend fun syncAllProgress() {
        try {
            val session = accountRepo.currentSession ?: run {
                Log.w(TAG, "No active session, skipping sync")
                return
            }

            val localProgress = getAllProgressForSync()
            Log.d(TAG, "Syncing ${localProgress.size} progress entries with backend")

            for (progress in localProgress) {
                try {
                    val input = ProgressInput(
                        tmdbId = progress.tmdbId,
                        meta = com.zstream.android.data.remote.ProgressMeta(
                            title = progress.title,
                            year = progress.year,
                            poster = toFullPosterUrl(progress.posterPath),
                            type = progress.type,
                        ),
                        watched = progress.watched,
                        duration = progress.duration,
                        episodeId = progress.episodeId,
                        seasonId = progress.seasonId,
                        episodeNumber = progress.episodeNumber,
                        seasonNumber = progress.seasonNumber,
                    )
                    api.setProgress(session.userId, progress.tmdbId, "Bearer ${session.token}", input)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync progress for ${progress.tmdbId}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during progress sync", e)
        }
    }

    /**
     * Clear all local progress
     */
    suspend fun clearProgress() {
        progressDao.clear()
    }

    private fun normalizePosterPath(posterPath: String?): String? {
        if (posterPath.isNullOrBlank()) return null
        return if (posterPath.startsWith("http")) {
            if (posterPath.contains("/t/p/")) posterPath.substringAfterLast("/") else posterPath
        } else {
            posterPath
        }.let { path ->
            if (path != null && !path.startsWith("/") && !path.startsWith("http")) "/$path" else path
        }
    }

    private fun toFullPosterUrl(posterPath: String?): String? {
        if (posterPath.isNullOrBlank()) return null
        if (posterPath.startsWith("http")) return posterPath
        val clean = if (posterPath.startsWith("/")) posterPath else "/$posterPath"
        return Urls.TMDB_IMAGE + "w342$clean"
    }

    /**
     * Fetch all progress from remote and update local cache
     */
    suspend fun syncFromRemote() {
        try {
            val session = accountRepo.currentSession ?: run {
                Log.w(TAG, "No active session for remote sync")
                return
            }

            Log.d(TAG, "Fetching remote progress for user ${session.userId}")
            val remoteProgress = api.getProgress(session.userId, "Bearer ${session.token}")
            
            // Group by tmdbId to deduplicate show cards
            val grouped = remoteProgress.groupBy { it.tmdbId }
            
            val entities = grouped.map { (tmdbId, episodes) ->
                // Sort by updatedAt to find the most recent watch
                val sorted = episodes.sortedByDescending { it.updatedAt }
                val latest = sorted.first()
                
                // Find any entry in the group that has a poster (metadata is flaky)
                val posterEntry = sorted.find { it.meta.poster != null }
                
                ProgressEntity(
                    tmdbId = tmdbId,
                    title = latest.meta.title,
                    type = latest.meta.type,
                    watched = latest.watched.toDoubleOrNull()?.toInt() ?: 0,
                    duration = latest.duration.toDoubleOrNull()?.toInt() ?: 0,
                    year = latest.meta.year,
                    posterPath = normalizePosterPath(posterEntry?.meta?.poster ?: latest.meta.poster),
                    episodeId = latest.episode.id,
                    seasonId = latest.season.id,
                    episodeNumber = latest.episode.number,
                    seasonNumber = latest.season.number,
                    updatedAt = try { 
                        java.time.OffsetDateTime.parse(latest.updatedAt).toInstant().toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                )
            }
            
            if (entities.isNotEmpty()) {
                // Before inserting, check if we have existing entities with posters to avoid overwriting with null
                val updatedEntities = entities.map { entity ->
                    var current = entity
                    
                    // 1. Try to recover poster from local DB
                    if (current.posterPath == null) {
                        val existing = progressDao.getByTmdbId(current.tmdbId)
                        if (existing?.posterPath != null) {
                            current = current.copy(posterPath = existing.posterPath)
                        }
                    }
                    
                    // 2. If still null, try to fetch from TMDB
                    if (current.posterPath == null) {
                        try {
                            val id = current.tmdbId.toIntOrNull()
                            if (id != null) {
                                if (current.type == "movie") {
                                    val detail = tmdbRepo.movieDetail(id)
                                    current = current.copy(posterPath = detail.posterPath, title = detail.title)
                                } else {
                                    val detail = tmdbRepo.tvDetail(id)
                                    current = current.copy(posterPath = detail.posterPath, title = detail.name)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to fetch missing TMDB metadata for ${current.tmdbId}")
                        }
                    }
                    
                    current
                }
                
                progressDao.insertAll(updatedEntities)
                Log.d(TAG, "Successfully synced ${updatedEntities.size} progress entries from remote")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync progress from remote", e)
        }
    }
}

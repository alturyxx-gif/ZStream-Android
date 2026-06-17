package com.zstream.android.data

import android.util.Log
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
        val entity = ProgressEntity(
            tmdbId = tmdbId,
            title = title,
            type = type,
            watched = watched,
            duration = duration,
            year = year,
            posterPath = posterPath,
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
                    year = year ?: 0,
                    poster = posterPath,
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
                            year = progress.year ?: 0,
                            poster = progress.posterPath,
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
            
            val entities = remoteProgress.map { v ->
                ProgressEntity(
                    tmdbId = v.tmdbId,
                    title = v.meta.title,
                    type = v.meta.type,
                    watched = v.watched.toDoubleOrNull()?.toInt() ?: 0,
                    duration = v.duration.toDoubleOrNull()?.toInt() ?: 0,
                    year = v.meta.year,
                    posterPath = v.meta.poster,
                    episodeId = v.episode.id,
                    seasonId = v.season.id,
                    episodeNumber = v.episode.number,
                    seasonNumber = v.season.number,
                    updatedAt = try { 
                        java.time.OffsetDateTime.parse(v.updatedAt).toInstant().toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                )
            }
            
            if (entities.isNotEmpty()) {
                progressDao.insertAll(entities)
                Log.d(TAG, "Successfully synced ${entities.size} progress entries from remote")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync progress from remote", e)
        }
    }
}

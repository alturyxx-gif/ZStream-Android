package com.zstream.android.data

import android.util.Log
import com.zstream.android.Urls
import com.zstream.android.data.local.dao.ProgressDao
import com.zstream.android.data.local.entity.ProgressEntity
import com.zstream.android.data.remote.BackendApi
import com.zstream.android.data.remote.ProgressInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    /**
     * Get progress from local cache first, then sync with backend if available
     * Returns the latest episode's progress for shows, or the single entry for movies.
     * "Latest" = highest (seasonNumber, episodeNumber), preferring non-completed entries.
     */
    suspend fun getProgressById(id: String): ProgressEntity? = progressDao.getById(id)

    fun observeProgress(tmdbId: String): Flow<ProgressEntity?> {
        return progressDao.observeAllByTmdbId(tmdbId).map { entries ->
            if (entries.isEmpty()) null
            else findLatestEpisode(entries)
        }
    }

    private fun findLatestEpisode(entries: List<ProgressEntity>): ProgressEntity {
        val nonCompleted = entries.filter { it.duration <= 0 || it.watched < it.duration * 0.95f }
        val candidates = if (nonCompleted.isNotEmpty()) nonCompleted else entries
        return candidates.maxByOrNull {
            (it.seasonNumber ?: 0) * 100000 + (it.episodeNumber ?: 0)
        } ?: candidates.first()
    }

    /**
     * Picks the entry to represent a show's single backend progress row. Unlike
     * findLatestEpisode() (furthest-along episode, used for the detail screen's resume point),
     * this must match toContinueWatchingResult()'s definition of "latest" (most recently
     * *watched*, by updatedAt) -- otherwise a rewatch of an earlier episode never gets pushed as
     * the show's latest, syncFromRemote() later pulls back the stale higher-episode row with a
     * fresh push timestamp, and it silently outranks and hides the real latest watch in Continue
     * Watching.
     */
    private fun findMostRecentlyWatched(entries: List<ProgressEntity>): ProgressEntity {
        val nonCompleted = entries.filter { it.duration <= 0 || it.watched < it.duration * 0.95f }
        val candidates = if (nonCompleted.isNotEmpty()) nonCompleted else entries
        return candidates.maxByOrNull { it.updatedAt } ?: candidates.first()
    }

    /**
     * Get all progress items (movies watched)
     */
    fun observeAllProgress(): Flow<List<ProgressEntity>> {
        return progressDao.observeAll()
    }

    /**
     * Get all progress entries for a specific tmdbId (all episodes of a show).
     */
    fun observeAllProgressForTmdb(tmdbId: String): Flow<List<ProgressEntity>> {
        return progressDao.observeAllByTmdbId(tmdbId)
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
            id = ProgressEntity.computeId(tmdbId, seasonNumber, episodeNumber),
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

    suspend fun removeProgressItem(
        tmdbId: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        seasonId: String? = null,
        episodeId: String? = null,
    ) {
        progressDao.deleteById(ProgressEntity.computeId(tmdbId, seasonNumber, episodeNumber))

        try {
            val session = accountRepo.currentSession ?: return
            if (seasonId != null || episodeId != null) {
                api.removeProgressDetailed(
                    session.userId,
                    tmdbId,
                    "Bearer ${session.token}",
                    com.zstream.android.data.remote.RemoveProgressBody(
                        seasonId = seasonId,
                        episodeId = episodeId,
                    )
                )
            } else {
                api.removeProgress(session.userId, tmdbId, "Bearer ${session.token}")
            }
            Log.d(TAG, "Successfully removed progress item for $tmdbId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove progress item from backend", e)
        }
    }

    /**
     * Get all progress items for sync (direct query, not Flow)
     */
    suspend fun getAllProgressForSync(): List<ProgressEntity> {
        return progressDao.getAllSync()
    }

    /**
     * Sync all local progress with backend (for periodic sync).
     * Groups by tmdbId and pushes only the latest episode per show
     * (backend stores one entry per show).
     */
    suspend fun syncAllProgress() {
        try {
            val session = accountRepo.currentSession ?: run {
                Log.w(TAG, "No active session, skipping sync")
                return
            }

            val localProgress = getAllProgressForSync()
            val grouped = localProgress.groupBy { it.tmdbId }
            Log.d(TAG, "Syncing ${grouped.size} shows/movies with backend")

            for ((tmdbId, entries) in grouped) {
                try {
                    val latest = findMostRecentlyWatched(entries)
                    val input = ProgressInput(
                        tmdbId = latest.tmdbId,
                        meta = com.zstream.android.data.remote.ProgressMeta(
                            title = latest.title,
                            year = latest.year,
                            poster = toFullPosterUrl(latest.posterPath),
                            type = latest.type,
                        ),
                        watched = latest.watched,
                        duration = latest.duration,
                        episodeId = latest.episodeId,
                        seasonId = latest.seasonId,
                        episodeNumber = latest.episodeNumber,
                        seasonNumber = latest.seasonNumber,
                    )
                    api.setProgress(session.userId, tmdbId, "Bearer ${session.token}", input)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync progress for $tmdbId", e)
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
     * Each episode gets its own local entry (per-episode progress tracking).
     */
    suspend fun syncFromRemote() {
        _isSyncing.value = true
        try {
            val session = accountRepo.currentSession ?: run {
                Log.w(TAG, "No active session for remote sync")
                return
            }

            Log.d(TAG, "Fetching remote progress for user ${session.userId}")
            val remoteProgress = api.getProgress(session.userId, "Bearer ${session.token}")

            val entities = remoteProgress.map { rp ->
                val id = ProgressEntity.computeId(rp.tmdbId, rp.season.number, rp.episode.number)

                val baseEntity = ProgressEntity(
                    id = id,
                    tmdbId = rp.tmdbId,
                    title = rp.meta.title,
                    type = rp.meta.type,
                    watched = rp.watched.toDoubleOrNull()?.toInt() ?: 0,
                    duration = rp.duration.toDoubleOrNull()?.toInt() ?: 0,
                    year = rp.meta.year,
                    posterPath = normalizePosterPath(rp.meta.poster),
                    episodeId = rp.episode.id,
                    seasonId = rp.season.id,
                    episodeNumber = rp.episode.number,
                    seasonNumber = rp.season.number,
                    updatedAt = try {
                        java.time.OffsetDateTime.parse(rp.updatedAt).toInstant().toEpochMilli()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }
                )

                var current = baseEntity

                // 1. Try to recover poster from existing local entries for this tmdbId
                val existing = progressDao.getAllByTmdbId(current.tmdbId)
                val posterEntry = existing.firstOrNull { it.posterPath != null }
                if (posterEntry != null) {
                    current = current.copy(posterPath = posterEntry.posterPath)
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
                    } catch (_: Exception) {
                        Log.w(TAG, "Failed to fetch missing TMDB metadata for ${current.tmdbId}")
                    }
                }

                current
            }

            // The backend stores only ONE row per show (the latest-watched episode; see
            // syncAllProgress()'s comment), while locally each episode gets its own row so
            // "Continue Watching" can track a specific episode. A remote snapshot therefore
            // never lists most locally-tracked episode ids -- diffing by exact id (as this used
            // to) meant almost every episode-level entry looked "stale" and got deleted on every
            // sync, and a remote row that hadn't caught up yet (e.g. this device's last
            // updateProgress() backend push failed/raced) could silently erase the real local
            // progress. Diff by tmdbId instead: only wipe a show's local history when the show is
            // *entirely* absent from remote (removed on another device/web), and only let a
            // remote row overwrite a specific local episode entry when it's actually newer.
            val localBefore = progressDao.getAllSync()
            val localById = localBefore.associateBy { it.id }
            val toUpsert = entities.filter { remote ->
                val local = localById[remote.id]
                local == null || remote.updatedAt > local.updatedAt
            }
            if (toUpsert.isNotEmpty()) {
                progressDao.insertAll(toUpsert)
                Log.d(TAG, "Successfully synced ${toUpsert.size} progress entries from remote")
            }

            // An empty remote response is indistinguishable here from "every show was removed
            // elsewhere" vs. a brand-new account, a session hiccup, or the backend legitimately
            // returning an empty 200 -- any of which would otherwise wipe every local entry on
            // this device. Never treat "remote has nothing" as "delete everything local"; only
            // prune shows once we've seen at least one real remote entry to diff against.
            if (entities.isNotEmpty()) {
                val remoteTmdbIds = entities.map { it.tmdbId }.toSet()
                val stale = localBefore.filter { it.tmdbId !in remoteTmdbIds }.map { it.id }
                for (id in stale) progressDao.deleteById(id)
                if (stale.isNotEmpty()) Log.d(TAG, "Removed ${stale.size} progress entries for shows no longer on remote")
            } else {
                Log.d(TAG, "Remote progress list was empty -- leaving local progress untouched")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync progress from remote", e)
        } finally {
            _isSyncing.value = false
        }
    }
}

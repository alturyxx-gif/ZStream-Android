package com.zstream.android.data

import android.util.Log
import com.zstream.android.Urls
import com.zstream.android.data.local.dao.BookmarkDao
import com.zstream.android.data.local.entity.BookmarkEntity
import com.zstream.android.data.remote.BackendApi
import com.zstream.android.data.remote.BookmarkInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BookmarkRepository"

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val accountRepo: AccountRepository,
    private val api: BackendApi,
    private val tmdbRepo: TmdbRepository,
    private val traktRepo: TraktRepository,
    private val settingsPrefs: com.zstream.android.data.local.preferences.SettingsPreferences,
) {
    /**
     * Get bookmark from local cache
     */
    fun observeBookmark(tmdbId: String): Flow<BookmarkEntity?> {
        return bookmarkDao.observeByTmdbId(tmdbId)
    }

    /**
     * Get all bookmarks
     */
    fun observeAllBookmarks(): Flow<List<BookmarkEntity>> {
        return bookmarkDao.observeAll()
    }

    /**
     * Get bookmarked movies only
     */
    fun observeMovieBookmarks(): Flow<List<BookmarkEntity>> {
        return bookmarkDao.observeMovies()
    }

    /**
     * Get bookmarked TV shows only
     */
    fun observeShowBookmarks(): Flow<List<BookmarkEntity>> {
        return bookmarkDao.observeShows()
    }

    /**
     * Check if item is bookmarked
     */
    suspend fun isBookmarked(tmdbId: String): Boolean {
        return bookmarkDao.exists(tmdbId)
    }

    private fun toRelativePoster(posterPath: String?): String? {
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
     * Add bookmark locally and sync with backend
     */
    suspend fun addBookmark(
        tmdbId: String,
        title: String,
        type: String,
        year: Int? = null,
        posterPath: String? = null,
        groups: List<String>? = null,
    ) {
        val relativePoster = toRelativePoster(posterPath)
        val existingGroups = bookmarkDao.getByTmdbId(tmdbId)?.groups.orEmpty()
        val mergedGroups = (existingGroups + groups.orEmpty()).distinct().ifEmpty { null }

        val entity = BookmarkEntity(
            tmdbId = tmdbId,
            title = title,
            type = if (type == "tv") "show" else type,
            year = year,
            posterPath = relativePoster,
            groups = mergedGroups,
            updatedAt = System.currentTimeMillis(),
        )

        // Update local cache first
        bookmarkDao.insert(entity)
        traktRepo.updateWatchlist(entity, add = true)
        val nextGroupOrder = settingsPrefs.appendGroupOrder(mergedGroups.orEmpty())

        // Try to sync with backend
        try {
            val session = accountRepo.currentSession ?: run {
                Log.w(TAG, "No active session, skipping backend sync")
                return
            }

            val input = BookmarkInput(
                tmdbId = tmdbId,
                meta = com.zstream.android.data.remote.BookmarkMeta(
                    title = title,
                    year = year,
                    poster = toFullPosterUrl(relativePoster),
                    type = if (type == "tv") "show" else type,
                ),
                group = mergedGroups,
            )

            api.addBookmark(session.userId, tmdbId, "Bearer ${session.token}", input)
            if (nextGroupOrder.isNotEmpty()) {
                settingsPrefs.syncGroupOrderToRemote(session.userId, "Bearer ${session.token}", api, nextGroupOrder)
            }
            Log.d(TAG, "Successfully synced bookmark for $tmdbId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync bookmark with backend", e)
            // Local cache is already updated, sync will retry later
        }
    }

    suspend fun setBookmarkGroups(tmdbId: String, groups: List<String>) {
        val existing = bookmarkDao.getByTmdbId(tmdbId) ?: return
        val updated = existing.copy(groups = groups.ifEmpty { null }, updatedAt = System.currentTimeMillis())
        bookmarkDao.update(updated)
        val nextGroupOrder = settingsPrefs.appendGroupOrder(groups)
        try {
            val session = accountRepo.currentSession ?: return
            api.addBookmark(
                session.userId,
                tmdbId,
                "Bearer ${session.token}",
                BookmarkInput(
                    tmdbId = tmdbId,
                    meta = com.zstream.android.data.remote.BookmarkMeta(
                        title = updated.title,
                        year = updated.year,
                        poster = toFullPosterUrl(updated.posterPath),
                        type = if (updated.type == "tv") "show" else updated.type,
                    ),
                    group = groups.ifEmpty { null },
                    favoriteEpisodes = updated.favoriteEpisodes,
                ),
            )
            if (nextGroupOrder.isNotEmpty()) {
                settingsPrefs.syncGroupOrderToRemote(session.userId, "Bearer ${session.token}", api, nextGroupOrder)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync bookmark groups", e)
        }
    }

    suspend fun renameGroup(oldGroup: String, newGroup: String) {
        val bookmarks = bookmarkDao.getAllSync()
        val updated = bookmarks.mapNotNull { bookmark ->
            val groups = bookmark.groups ?: return@mapNotNull null
            if (oldGroup !in groups) return@mapNotNull null
            bookmark.copy(
                groups = groups.map { if (it == oldGroup) newGroup else it }.distinct().ifEmpty { null },
                updatedAt = System.currentTimeMillis(),
            )
        }
        if (updated.isEmpty()) return
        bookmarkDao.insertAll(updated)
        val nextGroupOrder = settingsPrefs.appendGroupOrder(listOf(newGroup))
        try {
            val session = accountRepo.currentSession ?: return
            updated.forEach { bookmark ->
                api.addBookmark(
                    session.userId,
                    bookmark.tmdbId,
                    "Bearer ${session.token}",
                    BookmarkInput(
                        tmdbId = bookmark.tmdbId,
                        meta = com.zstream.android.data.remote.BookmarkMeta(
                            title = bookmark.title,
                            year = bookmark.year,
                            poster = toFullPosterUrl(bookmark.posterPath),
                            type = if (bookmark.type == "tv") "show" else bookmark.type,
                        ),
                        group = bookmark.groups,
                        favoriteEpisodes = bookmark.favoriteEpisodes,
                    ),
                )
            }
            if (nextGroupOrder.isNotEmpty()) {
                settingsPrefs.syncGroupOrderToRemote(session.userId, "Bearer ${session.token}", api, nextGroupOrder)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename group", e)
        }
    }

    /**
     * Remove bookmark locally and sync with backend
     */
    suspend fun removeBookmark(tmdbId: String) {
        bookmarkDao.getByTmdbId(tmdbId)?.let { traktRepo.updateWatchlist(it, add = false) }
        bookmarkDao.deleteByTmdbId(tmdbId)

        // Try to remove from backend
        try {
            val session = accountRepo.currentSession ?: return
            api.removeBookmark(session.userId, tmdbId, "Bearer ${session.token}")
            Log.d(TAG, "Successfully removed bookmark for $tmdbId from backend")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove bookmark from backend", e)
        }
    }

    /**
     * Toggle favorite episode (for shows)
     */
    suspend fun toggleFavoriteEpisode(
        showTmdbId: String,
        episodeId: String,
        showTitle: String? = null,
        showYear: Int? = null,
        showPoster: String? = null,
    ) {
        val existing = bookmarkDao.getByTmdbId(showTmdbId)

        val favoriteEpisodes = existing?.favoriteEpisodes?.toMutableList() ?: mutableListOf()
        if (favoriteEpisodes.contains(episodeId)) {
            favoriteEpisodes.remove(episodeId)
        } else {
            favoriteEpisodes.add(episodeId)
        }

        val entity = existing?.copy(
            favoriteEpisodes = favoriteEpisodes,
            updatedAt = System.currentTimeMillis(),
        ) ?: BookmarkEntity(
            tmdbId = showTmdbId,
            title = showTitle ?: "Unknown",
            type = "show",
            year = showYear,
            posterPath = toRelativePoster(showPoster),
            favoriteEpisodes = favoriteEpisodes,
            updatedAt = System.currentTimeMillis(),
        )

        bookmarkDao.update(entity)

        // Try to sync with backend
        try {
            val session = accountRepo.currentSession ?: return
            val input = BookmarkInput(
                tmdbId = showTmdbId,
                meta = com.zstream.android.data.remote.BookmarkMeta(
                    title = entity.title,
                    year = entity.year,
                    poster = toFullPosterUrl(entity.posterPath),
                    type = if (entity.type == "tv") "show" else entity.type,
                ),
                group = entity.groups,
                favoriteEpisodes = favoriteEpisodes.map { it },
            )
            api.addBookmark(session.userId, showTmdbId, "Bearer ${session.token}", input)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync favorite episode change", e)
        }
    }

    /**
     * Get all bookmarks for sync (direct query, not Flow)
     */
    suspend fun getAllBookmarksForSync(): List<BookmarkEntity> {
        return bookmarkDao.getAllSync()
    }

    /**
     * Sync all local bookmarks with backend
     */
    suspend fun syncAllBookmarks() {
        try {
            val session = accountRepo.currentSession ?: run {
                Log.w(TAG, "No active session, skipping sync")
                return
            }

            val localBookmarks = getAllBookmarksForSync()
            Log.d(TAG, "Syncing ${localBookmarks.size} bookmarks with backend")

            for (bookmark in localBookmarks) {
                try {
                    val input = BookmarkInput(
                        tmdbId = bookmark.tmdbId,
                        meta = com.zstream.android.data.remote.BookmarkMeta(
                            title = bookmark.title,
                            year = bookmark.year,
                            poster = toFullPosterUrl(bookmark.posterPath),
                            type = if (bookmark.type == "tv") "show" else bookmark.type,
                        ),
                        group = bookmark.groups,
                        favoriteEpisodes = bookmark.favoriteEpisodes,
                    )
                    api.addBookmark(session.userId, bookmark.tmdbId, "Bearer ${session.token}", input)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync bookmark for ${bookmark.tmdbId}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during bookmark sync", e)
        }
    }

    /**
     * Clear all local bookmarks
     */
    suspend fun clearBookmarks() {
        bookmarkDao.clear()
    }

    /**
     * Fetch all bookmarks from remote and update local cache
     */
    suspend fun syncFromRemote() {
        try {
            val session = accountRepo.currentSession ?: run {
                Log.w(TAG, "No active session for remote sync")
                return
            }

            Log.d(TAG, "Fetching remote bookmarks for user ${session.userId}")
            val remoteBookmarks = api.getBookmarks(session.userId, "Bearer ${session.token}")

            val entities = remoteBookmarks.map { v ->
                BookmarkEntity(
                    tmdbId = v.tmdbId,
                    title = v.meta.title,
                    type = v.meta.type,
                    year = v.meta.year,
                    posterPath = toRelativePoster(v.meta.poster),
                    groups = v.group,
                    favoriteEpisodes = v.favoriteEpisodes,
                    updatedAt = try {
                        java.time.OffsetDateTime.parse(v.updatedAt).toInstant().toEpochMilli()
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
                        val existing = bookmarkDao.getByTmdbId(current.tmdbId)
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

                bookmarkDao.insertAll(updatedEntities)
                Log.d(TAG, "Successfully synced ${updatedEntities.size} bookmarks from remote")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync bookmarks from remote", e)
        }
    }
}

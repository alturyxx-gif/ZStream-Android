package com.zstream.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a bookmarked movie or TV show
 * Based on reference: /p-stream/src/stores/bookmarks/index.ts
 */
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey
    val tmdbId: String,
    val title: String,
    val year: Int? = null,
    val posterPath: String? = null,
    val type: String, // "movie" or "show"
    val updatedAt: Long = System.currentTimeMillis(),
    val groups: List<String>? = null, // Collection/list names
    val favoriteEpisodes: List<String>? = null, // Episode IDs marked as favorite (for shows)
)

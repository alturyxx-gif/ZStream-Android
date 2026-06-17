package com.zstream.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents playback progress for a movie or TV show
 * Based on reference: /p-stream/src/stores/progress/index.ts
 */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey
    val tmdbId: String,
    val title: String,
    val year: Int? = null,
    val posterPath: String? = null,
    val type: String, // "movie" or "show"
    val watched: Int, // seconds watched
    val duration: Int, // total duration in seconds
    val updatedAt: Long = System.currentTimeMillis(),
    // For TV shows - episode tracking
    val episodeId: String? = null,
    val seasonId: String? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
)

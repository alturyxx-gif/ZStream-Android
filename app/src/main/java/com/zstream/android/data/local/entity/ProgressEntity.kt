package com.zstream.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents playback progress for a movie or TV show episode.
 * Each episode has its own row (for shows); movies have one row.
 * Based on reference: /p-stream/src/stores/progress/index.ts
 */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey
    val id: String, // tmdbId for movies, "${tmdbId}_S{s}E{e}" for shows
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
) {
    companion object {
        fun computeId(tmdbId: String, seasonNumber: Int?, episodeNumber: Int?): String {
            return if (seasonNumber != null && episodeNumber != null) {
                "${tmdbId}_S${seasonNumber}E${episodeNumber}"
            } else {
                tmdbId
            }
        }
    }
}

package com.zstream.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A movie or TV episode the user asked to be notified about once it releases/airs.
 * Rows are removed once the release-notify worker fires a notification for them.
 */
@Entity(tableName = "tracked_releases")
data class TrackedReleaseEntity(
    @PrimaryKey
    val key: String, // "movie:{tmdbId}" or "tv:{tmdbId}:{season}:{episode}"
    val tmdbId: Int,
    val mediaType: String, // "movie" or "tv"
    val title: String,
    val posterPath: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

fun trackedMovieKey(tmdbId: Int): String = "movie:$tmdbId"
fun trackedEpisodeKey(tmdbId: Int, season: Int, episode: Int): String = "tv:$tmdbId:$season:$episode"

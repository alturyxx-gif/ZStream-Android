package com.zstream.android.data.local.entity

import androidx.room.Entity

/**
 * Snapshot of one episode's TMDB metadata, cached whenever a season is fetched online so the
 * episode picker can render a show's full episode list (downloaded or not) while offline.
 */
@Entity(tableName = "season_episodes", primaryKeys = ["tmdbId", "season", "episode"])
data class CachedEpisodeEntity(
    val tmdbId: String,
    val season: Int,
    val episode: Int,
    val episodeId: Int,
    val title: String,
    val overview: String?,
    val stillPath: String?,
    val airDate: String?,
)

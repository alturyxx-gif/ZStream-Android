package com.zstream.android.ui.screens

import com.zstream.android.data.local.entity.ProgressEntity
import com.zstream.android.data.model.Media

internal data class ContinueWatchingResult(
    val media: List<Media>,
    val progressMap: Map<String, ProgressEntity>,
)

internal fun List<ProgressEntity>.toContinueWatchingResult(): ContinueWatchingResult {
    val latestByShow = groupBy { it.tmdbId }
        .mapNotNull { (tmdbId, entries) ->
            val latestIncomplete = entries
                .asSequence()
                .filter { it.duration > 1 && it.watched > 0 && it.watched < it.duration * 0.95f }
                .maxByOrNull { it.updatedAt }
                ?: return@mapNotNull null
            tmdbId to latestIncomplete
        }
        .sortedByDescending { (_, progress) -> progress.updatedAt }

    val progressMap = latestByShow.associate { (tmdbId, progress) -> tmdbId to progress }
    val media = latestByShow.map { (_, progress) ->
        Media(
            id = progress.tmdbId.toIntOrNull() ?: 0,
            title = if (progress.type == "movie") progress.title else null,
            name = if (progress.type == "show") progress.title else null,
            overview = null,
            posterPath = progress.posterPath,
            backdropPath = null,
            releaseDate = progress.year?.toString(),
            firstAirDate = progress.year?.toString(),
            voteAverage = null,
            mediaType = if (progress.type == "show") "tv" else progress.type,
            genreIds = null,
        )
    }

    return ContinueWatchingResult(media = media, progressMap = progressMap)
}

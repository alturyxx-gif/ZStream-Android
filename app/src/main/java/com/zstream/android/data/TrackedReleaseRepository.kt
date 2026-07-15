package com.zstream.android.data

import com.zstream.android.data.local.dao.TrackedReleaseDao
import com.zstream.android.data.local.entity.TrackedReleaseEntity
import com.zstream.android.data.local.entity.trackedEpisodeKey
import com.zstream.android.data.local.entity.trackedMovieKey
import com.zstream.android.data.model.Episode
import com.zstream.android.data.model.MovieDetail
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackedReleaseRepository @Inject constructor(
    private val dao: TrackedReleaseDao,
) {
    fun observeTracked(key: String): Flow<Boolean> = dao.observeTracked(key)

    fun observeAll(): Flow<List<TrackedReleaseEntity>> = dao.observeAll()

    suspend fun untrack(key: String) = dao.deleteByKey(key)

    suspend fun toggleMovie(detail: MovieDetail, currentlyTracked: Boolean) {
        val key = trackedMovieKey(detail.id)
        if (currentlyTracked) {
            dao.deleteByKey(key)
        } else {
            dao.upsert(
                TrackedReleaseEntity(
                    key = key,
                    tmdbId = detail.id,
                    mediaType = "movie",
                    title = detail.title,
                    posterPath = detail.posterPath,
                )
            )
        }
    }

    suspend fun toggleEpisode(
        showId: Int,
        showTitle: String,
        posterPath: String?,
        episode: Episode,
        currentlyTracked: Boolean,
    ) {
        val key = trackedEpisodeKey(showId, episode.seasonNumber, episode.episodeNumber)
        if (currentlyTracked) {
            dao.deleteByKey(key)
        } else {
            dao.upsert(
                TrackedReleaseEntity(
                    key = key,
                    tmdbId = showId,
                    mediaType = "tv",
                    title = showTitle,
                    posterPath = posterPath,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    episodeTitle = episode.name,
                )
            )
        }
    }
}

package com.zstream.android.data

import com.zstream.android.data.local.dao.TrackedReleaseDao
import com.zstream.android.data.local.entity.TrackedReleaseEntity
import com.zstream.android.data.model.Episode
import com.zstream.android.data.model.MovieDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TrackedReleaseRepository @Inject constructor(
    private val dao: TrackedReleaseDao,
    private val accountRepository: AccountRepository,
    private val notifyManager: ReleaseNotifyManager,
) {
    fun observeTracked(key: String): Flow<Boolean> = accountRepository.activeReleaseOwner
        .flatMapLatest { ownerId -> dao.observeTracked(ownerId, key) }

    fun observeAll(): Flow<List<TrackedReleaseEntity>> = accountRepository.activeReleaseOwner
        .flatMapLatest(dao::observeAll)

    suspend fun untrack(key: String): Boolean {
        return dao.delete(accountRepository.currentReleaseOwner(), key) > 0
    }

    /**
     * Idempotent explicit subscribe operation for TV-to-phone requests.
     * Returns the newly inserted generation id, or null when it already existed.
     */
    suspend fun subscribe(request: ReleaseSubscriptionRequest): Long? {
        val ownerId = accountRepository.currentReleaseOwner()
        val blockedReason = notifyManager.notificationUnavailableReason()
        val generation = dao.subscribe(
            request.toEntity(ownerId),
            allowInsert = blockedReason == null,
        )
        if (generation < 0) throw NotificationUnavailableException(requireNotNull(blockedReason))
        if (generation > 0) notifyManager.scheduleAfterSubscription()
        return generation.takeIf { it > 0 }
    }

    /** Deletes only the exact row created by the failed callback, never a later re-subscription. */
    suspend fun rollbackSubscription(generation: Long): Boolean {
        // The auto-generated id is globally unique and remains safe if the active profile changes.
        return dao.deleteById(generation) > 0
    }

    suspend fun toggleMovie(detail: MovieDetail): Boolean = toggle(
        ReleaseSubscriptionRequest(
            tmdbId = detail.id,
            mediaType = "movie",
            title = detail.title,
            posterPath = detail.posterPath,
        )
    )

    suspend fun toggleEpisode(
        showId: Int,
        showTitle: String,
        posterPath: String?,
        episode: Episode,
    ): Boolean = toggle(
        ReleaseSubscriptionRequest(
            tmdbId = showId,
            mediaType = "tv",
            title = showTitle,
            posterPath = posterPath,
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.episodeNumber,
            episodeTitle = episode.name,
        )
    )

    private suspend fun toggle(request: ReleaseSubscriptionRequest): Boolean {
        val ownerId = accountRepository.currentReleaseOwner()
        val blockedReason = notifyManager.notificationUnavailableReason()
        val result = dao.toggle(
            request.toEntity(ownerId),
            allowInsert = blockedReason == null,
        )
        if (result < 0) throw NotificationUnavailableException(requireNotNull(blockedReason))
        if (result > 0) notifyManager.scheduleAfterSubscription()
        return result > 0
    }

    private fun ReleaseSubscriptionRequest.toEntity(ownerId: String) = TrackedReleaseEntity(
        ownerId = ownerId,
        key = key,
        tmdbId = tmdbId,
        mediaType = mediaType,
        title = title,
        posterPath = posterPath,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
    )
}

package com.zstream.android.ui.screens

import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.PairedPhone
import com.zstream.android.data.ReleaseSubscriptionRequest
import com.zstream.android.data.ReleaseSubscriptionUserException
import com.zstream.android.data.NotificationUnavailableException
import com.zstream.android.data.TrackedReleaseRepository
import com.zstream.android.data.TvSyncRepository
import com.zstream.android.data.local.entity.trackedEpisodeKey
import com.zstream.android.data.local.entity.trackedMovieKey
import com.zstream.android.data.model.Episode
import com.zstream.android.data.model.MovieDetail
import com.zstream.android.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

sealed interface TrackedReleaseUiEvent {
    data class Success(val message: String) : TrackedReleaseUiEvent
    data class Failure(val message: String) : TrackedReleaseUiEvent
}

@HiltViewModel
class TrackedReleaseViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: TrackedReleaseRepository,
    private val tvSyncRepository: TvSyncRepository,
) : ViewModel() {
    private val _pendingKeys = MutableStateFlow<Set<String>>(emptySet())
    val pendingKeys = _pendingKeys.asStateFlow()

    private val _events = MutableSharedFlow<TrackedReleaseUiEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()
    val pairedPhones = tvSyncRepository.pairedPhones

    fun isMovieTracked(tmdbId: Int): Flow<Boolean> = repo.observeTracked(trackedMovieKey(tmdbId))

    fun isEpisodeTracked(showId: Int, season: Int, episode: Int): Flow<Boolean> =
        repo.observeTracked(trackedEpisodeKey(showId, season, episode))

    fun toggleMovie(detail: MovieDetail) {
        change(
            key = trackedMovieKey(detail.id),
        ) {
            val tracked = repo.toggleMovie(detail)
            if (tracked) {
                context.getString(R.string.release_notification_enabled, detail.title)
            } else {
                context.getString(R.string.release_notification_disabled, detail.title)
            }
        }
    }

    fun toggleEpisode(showId: Int, showTitle: String, posterPath: String?, episode: Episode) {
        val label = context.getString(
            R.string.system_episode_title,
            showTitle,
            episode.seasonNumber,
            episode.episodeNumber,
        )
        change(
            key = trackedEpisodeKey(showId, episode.seasonNumber, episode.episodeNumber),
        ) {
            val tracked = repo.toggleEpisode(showId, showTitle, posterPath, episode)
            if (tracked) {
                context.getString(R.string.release_notification_enabled, label)
            } else {
                context.getString(R.string.release_notification_disabled, label)
            }
        }
    }

    fun reportFailure(message: String) {
        _events.tryEmit(TrackedReleaseUiEvent.Failure(message))
    }

    fun subscribeOnPhone(phone: PairedPhone, request: ReleaseSubscriptionRequest) {
        val label = if (request.mediaType == "movie") {
            request.title
        } else {
            context.getString(
                R.string.system_episode_title,
                request.title,
                request.seasonNumber,
                request.episodeNumber,
            )
        }
        change(
            key = request.key,
            failureMessage = { error ->
                when (error) {
                    is ReleaseSubscriptionUserException -> error.message
                        ?: context.getString(R.string.release_phone_subscription_failed, phone.phoneName)
                    is IOException -> context.getString(R.string.release_phone_unreachable, phone.phoneName)
                    else -> context.getString(R.string.release_phone_subscription_failed, phone.phoneName)
                }
            },
        ) {
            tvSyncRepository.sendReleaseSubscription(phone, request)
            context.getString(R.string.release_phone_subscribed, phone.phoneName, label)
        }
    }

    private fun change(
        key: String,
        failureMessage: (Throwable) -> String = {
            if (it is NotificationUnavailableException) {
                it.message ?: context.getString(R.string.release_notification_update_failed)
            } else {
                context.getString(R.string.release_notification_update_failed)
            }
        },
        block: suspend () -> String,
    ) {
        if (key in _pendingKeys.value) return
        _pendingKeys.update { it + key }
        viewModelScope.launch {
            try {
                _events.emit(TrackedReleaseUiEvent.Success(block()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.emit(TrackedReleaseUiEvent.Failure(failureMessage(error)))
            } finally {
                _pendingKeys.update { it - key }
            }
        }
    }
}

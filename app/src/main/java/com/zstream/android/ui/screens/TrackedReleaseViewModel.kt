package com.zstream.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.PairedPhone
import com.zstream.android.data.ReleaseSubscriptionRequest
import com.zstream.android.data.TrackedReleaseRepository
import com.zstream.android.data.TvSyncRepository
import com.zstream.android.data.local.entity.trackedEpisodeKey
import com.zstream.android.data.local.entity.trackedMovieKey
import com.zstream.android.data.model.Episode
import com.zstream.android.data.model.MovieDetail
import dagger.hilt.android.lifecycle.HiltViewModel
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
                "Release notification enabled for \u201c${detail.title}\u201d"
            } else {
                "Release notification disabled for \u201c${detail.title}\u201d"
            }
        }
    }

    fun toggleEpisode(showId: Int, showTitle: String, posterPath: String?, episode: Episode) {
        val label = "$showTitle S${episode.seasonNumber}E${episode.episodeNumber}"
        change(
            key = trackedEpisodeKey(showId, episode.seasonNumber, episode.episodeNumber),
        ) {
            val tracked = repo.toggleEpisode(showId, showTitle, posterPath, episode)
            if (tracked) {
                "Release notification enabled for \u201c$label\u201d"
            } else {
                "Release notification disabled for \u201c$label\u201d"
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
            "${request.title} S${request.seasonNumber}E${request.episodeNumber}"
        }
        change(
            key = request.key,
            failureMessage = { error ->
                val fallback = "${phone.phoneName} couldn\u2019t be reached. Open ZStream on that phone and try again."
                when (error) {
                    is IOException -> fallback
                    else -> error.message
                        ?.trim()
                        ?.takeIf { it.length <= 180 && !it.startsWith('{') && "http://" !in it && "https://" !in it }
                        ?: fallback
                }
            },
        ) {
            tvSyncRepository.sendReleaseSubscription(phone, request)
                .ifBlank { "Subscribed ${phone.phoneName} for \u201c$label\u201d" }
        }
    }

    private fun change(
        key: String,
        failureMessage: (Throwable) -> String = { it.message ?: "Couldn\u2019t update the release notification." },
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

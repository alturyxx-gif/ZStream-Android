package com.zstream.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.TrackedReleaseRepository
import com.zstream.android.data.local.entity.trackedEpisodeKey
import com.zstream.android.data.local.entity.trackedMovieKey
import com.zstream.android.data.model.Episode
import com.zstream.android.data.model.MovieDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackedReleaseViewModel @Inject constructor(
    private val repo: TrackedReleaseRepository,
) : ViewModel() {
    fun isMovieTracked(tmdbId: Int): Flow<Boolean> = repo.observeTracked(trackedMovieKey(tmdbId))

    fun isEpisodeTracked(showId: Int, season: Int, episode: Int): Flow<Boolean> =
        repo.observeTracked(trackedEpisodeKey(showId, season, episode))

    fun toggleMovie(detail: MovieDetail, currentlyTracked: Boolean) {
        viewModelScope.launch { repo.toggleMovie(detail, currentlyTracked) }
    }

    fun toggleEpisode(showId: Int, showTitle: String, posterPath: String?, episode: Episode, currentlyTracked: Boolean) {
        viewModelScope.launch { repo.toggleEpisode(showId, showTitle, posterPath, episode, currentlyTracked) }
    }
}

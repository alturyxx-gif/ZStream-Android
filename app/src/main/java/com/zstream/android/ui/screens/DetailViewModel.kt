package com.zstream.android.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.TmdbRepository
import com.zstream.android.data.model.MovieDetail
import com.zstream.android.data.model.Season
import com.zstream.android.data.model.TvDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DetailState {
    object Loading : DetailState()
    data class Movie(val detail: MovieDetail) : DetailState()
    data class Tv(val detail: TvDetail, val selectedSeason: Season? = null) : DetailState()
    data class Error(val message: String) : DetailState()
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repo: TmdbRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val id = savedState.get<Int>("id") ?: 0
    private val mediaType = savedState.get<String>("mediaType") ?: "movie"

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = DetailState.Loading
            runCatching {
                if (mediaType == "movie") {
                    _state.value = DetailState.Movie(repo.movieDetail(id))
                } else {
                    val detail = repo.tvDetail(id)
                    val firstSeason = detail.seasons
                        ?.firstOrNull { it.seasonNumber > 0 }
                        ?.let { repo.season(id, it.seasonNumber) }
                    _state.value = DetailState.Tv(detail, firstSeason)
                }
            }.onFailure { _state.value = DetailState.Error(it.message ?: "Failed to load") }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        val current = _state.value as? DetailState.Tv ?: return
        viewModelScope.launch {
            runCatching { repo.season(id, seasonNumber) }
                .onSuccess { _state.value = current.copy(selectedSeason = it) }
        }
    }
}

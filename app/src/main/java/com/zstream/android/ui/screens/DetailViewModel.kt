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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val progressRepo: com.zstream.android.data.ProgressRepository,
    private val bookmarkRepo: com.zstream.android.data.BookmarkRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val id = savedState.get<Int>("id") ?: 0
    private val mediaType = savedState.get<String>("mediaType") ?: "movie"

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state = _state.asStateFlow()
    
    // Add flows for bookmark and progress
    val isBookmarked = bookmarkRepo.observeBookmark(id.toString())
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val progress = progressRepo.observeProgress(id.toString())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allProgress = progressRepo.observeAllProgressForTmdb(id.toString())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var pendingInitialSeasonNumber: Int? = null
    private var initialSeasonResolved = false

    init {
        if (mediaType == "tv") {
            viewModelScope.launch {
                progress.collectLatest { currentProgress ->
                    val seasonNumber = currentProgress?.seasonNumber ?: return@collectLatest
                    pendingInitialSeasonNumber = seasonNumber
                    applyPendingInitialSeason()
                }
            }
        }
        load()
    }

    fun toggleBookmark() {
        val current = _state.value
        val title = when (current) {
            is DetailState.Movie -> current.detail.title
            is DetailState.Tv -> current.detail.name
            else -> return
        }
        val poster = when (current) {
            is DetailState.Movie -> current.detail.posterPath
            is DetailState.Tv -> current.detail.posterPath
            else -> return
        }
        val year = when (current) {
            is DetailState.Movie -> current.detail.releaseDate?.take(4)?.toIntOrNull()
            is DetailState.Tv -> current.detail.firstAirDate?.take(4)?.toIntOrNull()
            else -> return
        }
        
        viewModelScope.launch {
            if (isBookmarked.value) {
                bookmarkRepo.removeBookmark(id.toString())
            } else {
                bookmarkRepo.addBookmark(id.toString(), title, mediaType, year, poster)
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DetailState.Loading
            runCatching {
                if (mediaType == "movie") {
                    _state.value = DetailState.Movie(repo.movieDetail(id))
                } else {
                    val detail = repo.tvDetail(id)
                    val preferredSeasonNumber = pendingInitialSeasonNumber ?: progress.value?.seasonNumber
                    val firstSeason = preferredSeasonNumber?.let { seasonNumber ->
                        detail.seasons?.firstOrNull { it.seasonNumber == seasonNumber }?.let { repo.season(id, seasonNumber) }
                    } ?: detail.seasons
                        ?.firstOrNull { it.seasonNumber > 0 }
                        ?.let { repo.season(id, it.seasonNumber) }
                    _state.value = DetailState.Tv(detail, firstSeason)
                    applyPendingInitialSeason()
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

    private fun applyPendingInitialSeason() {
        if (initialSeasonResolved) return
        val seasonNumber = pendingInitialSeasonNumber ?: return
        val current = _state.value as? DetailState.Tv ?: return
        if (current.selectedSeason?.seasonNumber == seasonNumber) {
            initialSeasonResolved = true
            return
        }
        if (current.detail.seasons.orEmpty().none { it.seasonNumber == seasonNumber }) {
            initialSeasonResolved = true
            return
        }

        initialSeasonResolved = true
        viewModelScope.launch {
            runCatching { repo.season(id, seasonNumber) }
                .onSuccess { _state.value = current.copy(selectedSeason = it) }
        }
    }
}

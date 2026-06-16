package com.zstream.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.TmdbRepository
import com.zstream.android.data.model.Media
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

enum class HomeTab { MOVIES, TV, EDITOR }

data class MediaSection(val title: String, val items: List<Media>)

data class HomeState(
    val movieSections: List<MediaSection> = emptyList(),
    val tvSections: List<MediaSection> = emptyList(),
    val editorSections: List<MediaSection> = emptyList(),
    val activeTab: HomeTab = HomeTab.MOVIES,
    val selectedGenreId: Int? = null,
    val searchQuery: String = "",
    val loading: Boolean = true,
    val error: String? = null,
) {
    val currentSections get() = when (activeTab) {
        HomeTab.MOVIES -> movieSections
        HomeTab.TV -> tvSections
        HomeTab.EDITOR -> editorSections
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(private val repo: TmdbRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = HomeState(loading = true)
            runCatching {
                supervisorScope {
                    val trendMovies = async { repo.trendingMovies() }
                    val popularMovies = async { repo.popularMovies() }
                    val nowPlaying = async { repo.nowPlayingMovies() }
                    val topMovies = async { repo.topRatedMovies() }
                    val trendTv = async { repo.trendingTv() }
                    val popularTv = async { repo.popularTv() }
                    val topTv = async { repo.topRatedTv() }
                    val onAir = async { repo.onAirTv() }

                    _state.value = HomeState(
                        loading = false,
                        movieSections = listOf(
                            MediaSection("Most Popular", popularMovies.await()),
                            MediaSection("In Cinemas", nowPlaying.await()),
                            MediaSection("Top Rated", topMovies.await()),
                            MediaSection("Trending", trendMovies.await()),
                        ),
                        tvSections = listOf(
                            MediaSection("Most Popular", popularTv.await()),
                            MediaSection("On the Air", onAir.await()),
                            MediaSection("Top Rated", topTv.await()),
                            MediaSection("Trending", trendTv.await()),
                        ),
                        editorSections = listOf(
                            MediaSection("Editor Picks — Movies", topMovies.await().take(10)),
                            MediaSection("Editor Picks — Shows", topTv.await().take(10)),
                        ),
                    )
                }
            }.onFailure {
                _state.value = HomeState(loading = false, error = it.message ?: "No connection")
            }
        }
    }

    fun setTab(tab: HomeTab) = _state.update { it.copy(activeTab = tab) }
    fun setGenre(id: Int?) = _state.update { it.copy(selectedGenreId = id) }
    fun setSearch(q: String) = _state.update { it.copy(searchQuery = q) }

    // Live search results from TMDB (separate from carousel sections)
    private val _searchResults = MutableStateFlow<List<Media>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null

    fun onSearchChange(q: String) {
        setSearch(q)
        searchJob?.cancel()
        if (q.isBlank()) { _searchResults.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            runCatching { _searchResults.value = repo.search(q) }
        }
    }
}

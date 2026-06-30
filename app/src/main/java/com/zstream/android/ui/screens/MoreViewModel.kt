package com.zstream.android.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.TmdbRepository
import com.zstream.android.data.model.Media
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoreState(
    val title: String = "",
    val media: List<Media> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val repo: TmdbRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val sourceName = savedStateHandle.get<String>("source").orEmpty()
    private val _state = MutableStateFlow(MoreState(title = sourceName.replace('_', ' ')))
    val state = _state.asStateFlow()
    private var currentPage = 1
    private var loadingRequest = false

    init {
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val page = loadPage(1)
                currentPage = 1
                _state.value = _state.value.copy(
                    media = page.items,
                    loading = false,
                    canLoadMore = page.canLoadMore,
                )
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = it.message ?: "No connection")
            }
        }
    }

    fun loadMore() {
        if (_state.value.loadingMore || !_state.value.canLoadMore || loadingRequest) return
        viewModelScope.launch {
            loadingRequest = true
            _state.value = _state.value.copy(loadingMore = true, error = null)
            runCatching {
                val nextPage = currentPage + 1
                val page = loadPage(nextPage)
                currentPage = nextPage
                _state.value = _state.value.copy(
                    media = _state.value.media + page.items,
                    loadingMore = false,
                    canLoadMore = page.canLoadMore,
                )
            }.onFailure {
                _state.value = _state.value.copy(loadingMore = false, error = it.message ?: "No connection")
            }
            loadingRequest = false
        }
    }

    private data class PageResult(val items: List<Media>, val canLoadMore: Boolean)

    private suspend fun loadPage(page: Int): PageResult {
        return when (sourceName) {
            MediaSectionSource.PopularMovies.name -> repo.popularMoviesPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.NowPlayingMovies.name -> repo.nowPlayingMoviesPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.TopRatedMovies.name -> repo.topRatedMoviesPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.TrendingMovies.name -> repo.trendingMoviesPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.PopularTv.name -> repo.popularTvPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.OnAirTv.name -> repo.onAirTvPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.TopRatedTv.name -> repo.topRatedTvPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.TrendingTv.name -> repo.trendingTvPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.EditorMovies.name -> repo.topRatedMoviesPage(page).let { PageResult(it.items, it.canLoadMore) }
            MediaSectionSource.EditorTv.name -> repo.topRatedTvPage(page).let { PageResult(it.items, it.canLoadMore) }
            else -> PageResult(emptyList(), false)
        }
    }
}

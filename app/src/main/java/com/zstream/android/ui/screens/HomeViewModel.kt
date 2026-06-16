package com.zstream.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.TmdbRepository
import com.zstream.android.data.model.Media
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

data class HomeState(
    val movies: List<Media> = emptyList(),
    val tv: List<Media> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

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
                    val movies = async { repo.trendingMovies() }
                    val tv = async { repo.trendingTv() }
                    _state.value = HomeState(movies = movies.await(), tv = tv.await(), loading = false)
                }
            }.onFailure {
                _state.value = HomeState(loading = false, error = "No connection")
            }
        }
    }
}

package com.zstream.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.TmdbRepository
import com.zstream.android.data.model.Media
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val repo: TmdbRepository) : ViewModel() {
    val query = MutableStateFlow("")
    private val _results = MutableStateFlow<List<Media>>(emptyList())
    val results = _results.asStateFlow()
    val canLoadMore = MutableStateFlow(false)
    val loadingMore = MutableStateFlow(false)
    private var currentPage = 1
    private var lastQuery = ""
    private var requestToken = 0

    private val _popular = MutableStateFlow<List<Media>>(emptyList())
    val popular = _popular.asStateFlow()

    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            runCatching { _popular.value = repo.popularMovies() }
        }

        viewModelScope.launch {
            query.debounce(400).collectLatest { q ->
                lastQuery = q
                requestToken++
                currentPage = 1
                canLoadMore.value = false
                if (q.isBlank()) { _results.value = emptyList(); return@collectLatest }
                loading.value = true
                error.value = null
                val token = requestToken
                val response = runCatching { repo.searchPaged(q, 1) }
                    .getOrElse {
                        error.value = "No connection"
                        loading.value = false
                        return@collectLatest
                    }
                if (token != requestToken) return@collectLatest
                _results.value = response.results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
                canLoadMore.value = response.page < response.totalPages
                loading.value = false
            }
        }
    }

    fun loadMore() {
        val q = lastQuery
        if (q.isBlank() || !canLoadMore.value || loadingMore.value) return
        viewModelScope.launch {
            loadingMore.value = true
            val nextPage = currentPage + 1
            runCatching {
                val response = repo.searchPaged(q, nextPage)
                _results.value = _results.value + response.results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
                currentPage = nextPage
                canLoadMore.value = response.page < response.totalPages
            }.onFailure {
                error.value = "No connection"
            }
            loadingMore.value = false
        }
    }
}

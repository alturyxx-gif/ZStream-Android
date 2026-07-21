package com.zstream.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.ShortsRepository
import com.zstream.android.data.model.ShortItem
import com.zstream.android.data.model.ShortsStreamResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val repository: ShortsRepository,
) : ViewModel() {

    data class UiState(
        val items: List<ShortItem> = emptyList(),
        val cursor: String? = null,
        val endReached: Boolean = false,
        val loading: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Resolved video/audio URLs are signed + short-lived -- cached in memory
    // only for as long as they're actually still valid, never persisted.
    private val streamCache = mutableMapOf<String, ShortsStreamResponse>()

    init {
        loadNextPage()
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.loading || current.endReached) return
        _state.value = current.copy(loading = true)
        viewModelScope.launch {
            val page = runCatching { repository.loadPage(current.cursor) }.getOrNull()
            _state.value = if (page == null) {
                _state.value.copy(loading = false)
            } else {
                _state.value.copy(
                    items = _state.value.items + page.items,
                    cursor = page.nextCursor,
                    endReached = page.nextCursor == null,
                    loading = false,
                )
            }
        }
    }

    suspend fun streamFor(videoId: String): ShortsStreamResponse? {
        streamCache[videoId]?.let { cached ->
            val now = System.currentTimeMillis() / 1000
            if (now < cached.expiresAtEpochSec) return cached
        }
        val resolved = runCatching { repository.resolveStream(videoId) }
            .onFailure { android.util.Log.e("ShortsVM", "resolveStream failed for $videoId", it) }
            .getOrNull() ?: return null
        streamCache[videoId] = resolved
        return resolved
    }
}

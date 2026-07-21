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
        val loading: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val streamCache = mutableMapOf<String, ShortsStreamResponse>()
    private val failedUntilEpochSec = mutableMapOf<String, Long>()
    private val seenVideoIds = mutableSetOf<String>()

    init {
        loadNextPage()
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.loading) return
        _state.value = current.copy(loading = true)
        viewModelScope.launch {
            val excludeIds = seenVideoIds + failedUntilEpochSec.keys
            val page = runCatching { repository.loadPage(excludeIds) }.getOrNull().orEmpty()
            seenVideoIds.addAll(page.map { it.videoId })
            _state.value = _state.value.copy(
                items = _state.value.items + page,
                loading = false,
            )
        }
    }

    fun prefetchStream(videoId: String) {
        if (streamCache.containsKey(videoId)) return
        if (isRecentlyFailed(videoId)) return
        viewModelScope.launch { streamFor(videoId) }
    }

    private fun isRecentlyFailed(videoId: String): Boolean {
        val until = failedUntilEpochSec[videoId] ?: return false
        return System.currentTimeMillis() / 1000 < until
    }

    suspend fun streamFor(videoId: String): ShortsStreamResponse? {
        streamCache[videoId]?.let { cached ->
            val now = System.currentTimeMillis() / 1000
            if (now < cached.expiresAtEpochSec) return cached
        }
        if (isRecentlyFailed(videoId)) return null
        val resolved = runCatching { repository.resolveStream(videoId) }
            .onFailure {
                android.util.Log.e("ShortsVM", "resolveStream failed for $videoId", it)
                failedUntilEpochSec[videoId] = System.currentTimeMillis() / 1000 + FAILURE_RETRY_COOLDOWN_SEC
            }
            .getOrNull() ?: return null
        streamCache[videoId] = resolved
        return resolved
    }

    companion object {
        private const val FAILURE_RETRY_COOLDOWN_SEC = 15 * 60L
    }
}

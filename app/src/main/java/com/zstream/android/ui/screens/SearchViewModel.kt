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
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            query.debounce(400).collectLatest { q ->
                if (q.isBlank()) { _results.value = emptyList(); return@collectLatest }
                loading.value = true
                error.value = null
                runCatching { _results.value = repo.search(q) }
                    .onFailure { error.value = "No connection" }
                loading.value = false
            }
        }
    }
}

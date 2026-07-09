package com.zstream.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.download.DownloadStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadDao: DownloadDao,
    private val storage: DownloadStorage,
) : ViewModel() {
    val downloads: StateFlow<List<DownloadEntity>> = downloadDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Deletes the on-disk file(s) (best-effort) and the DB row. Works regardless of status —
     * for an in-flight download this only removes the row/partial file; the background service's
     * own coroutine isn't cancelled and may still briefly touch the (now-deleted) destination. */
    fun delete(entity: DownloadEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                entity.filePath?.let { runCatching { storage.deleteByDisplayPath(it) } }
                entity.subtitlePaths?.forEach { path -> runCatching { storage.deleteByDisplayPath(path) } }
                downloadDao.delete(entity)
            }
        }
    }
}

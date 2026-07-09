package com.zstream.android.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.entity.DownloadStatus
import com.zstream.android.download.DownloadService
import com.zstream.android.download.DownloadStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** [freeBytes]/[totalBytes] describe the volume backing the Downloads folder. */
data class FreeSpaceInfo(val freeBytes: Long, val totalBytes: Long)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val downloadDao: DownloadDao,
    private val storage: DownloadStorage,
) : ViewModel() {
    val downloads: StateFlow<List<DownloadEntity>> = downloadDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _freeSpace = MutableStateFlow(currentFreeSpace())
    val freeSpace: StateFlow<FreeSpaceInfo> = _freeSpace.asStateFlow()

    private fun currentFreeSpace(): FreeSpaceInfo {
        val (free, total) = storage.freeSpaceInfo()
        return FreeSpaceInfo(free, total)
    }

    private fun refreshFreeSpace() {
        _freeSpace.value = currentFreeSpace()
    }

    /** Deletes the on-disk file(s) (best-effort) and the DB row. For an in-flight download this
     * first asks the service to cancel it (which does its own matching cleanup); calling delete
     * on top is still safe/idempotent since deleteByDisplayPath()/DAO delete are both no-ops if
     * the file/row are already gone. */
    fun delete(entity: DownloadEntity) {
        if (isInFlight(entity)) {
            DownloadService.cancel(appContext, entity.id)
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                entity.filePath?.let { runCatching { storage.deleteByDisplayPath(it); storage.deleteEmptyFolder(it) } }
                entity.subtitlePaths?.forEach { path -> runCatching { storage.deleteByDisplayPath(path) } }
                downloadDao.delete(entity)
            }
            refreshFreeSpace()
        }
    }

    fun pause(entity: DownloadEntity) = DownloadService.pause(appContext, entity.id)

    fun resume(entity: DownloadEntity) = DownloadService.resume(appContext, entity.id)

    fun cancel(entity: DownloadEntity) {
        DownloadService.cancel(appContext, entity.id)
        viewModelScope.launch {
            kotlinx.coroutines.delay(500) // best-effort — let the service's own cleanup land first
            refreshFreeSpace()
        }
    }

    private fun isInFlight(entity: DownloadEntity) = entity.status == DownloadStatus.QUEUED ||
        entity.status == DownloadStatus.DOWNLOADING ||
        entity.status == DownloadStatus.REMUXING

    /** Called after navigating back from the local player or any other action that might have changed free space. */
    fun onResume() = refreshFreeSpace()
}

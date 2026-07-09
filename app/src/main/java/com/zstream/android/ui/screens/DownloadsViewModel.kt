package com.zstream.android.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.LocalMediaRepository
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.entity.DownloadStatus
import com.zstream.android.data.local.entity.LocalLibraryFolderEntity
import com.zstream.android.data.local.entity.LocalMediaEntity
import com.zstream.android.download.DownloadService
import com.zstream.android.download.DownloadStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
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

sealed interface LibraryItem {
    data class DownloadMovie(val entity: DownloadEntity) : LibraryItem
    data class DownloadShow(val key: String, val title: String, val posterPath: String?, val episodes: List<DownloadEntity>) : LibraryItem
    data class LocalGroup(val key: String, val title: String, val mediaKind: String, val posterPath: String?, val thumbnailPath: String?, val items: List<LocalMediaEntity>) : LibraryItem
}

data class DownloadsUiState(
    val items: List<LibraryItem> = emptyList(),
    val folders: List<LocalLibraryFolderEntity> = emptyList(),
    val scanningFolderIds: Set<Long> = emptySet(),
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val downloadDao: DownloadDao,
    private val storage: DownloadStorage,
    private val localMediaRepository: LocalMediaRepository,
) : ViewModel() {
    val downloads: StateFlow<List<DownloadEntity>> = downloadDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<DownloadsUiState> = combine(
        downloads,
        localMediaRepository.folders,
        localMediaRepository.media,
        localMediaRepository.scanningFolderIds,
    ) { downloads, folders, media, scanningFolderIds ->
        DownloadsUiState(
            items = groupDownloads(downloads) + groupLocalMedia(media),
            folders = folders,
            scanningFolderIds = scanningFolderIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadsUiState())

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

    fun addFolder(uri: Uri) {
        viewModelScope.launch { localMediaRepository.addFolder(uri) }
    }

    fun rescan(folders: List<LocalLibraryFolderEntity>) {
        viewModelScope.launch { folders.forEach { localMediaRepository.scanFolder(it) } }
    }

    fun replaceFolder(folder: LocalLibraryFolderEntity, uri: Uri) {
        viewModelScope.launch {
            localMediaRepository.removeFolder(folder)
            localMediaRepository.addFolder(uri)
        }
    }

    fun removeFolder(folder: LocalLibraryFolderEntity) {
        viewModelScope.launch { localMediaRepository.removeFolder(folder) }
    }

    private fun groupDownloads(downloads: List<DownloadEntity>): List<LibraryItem> {
        val movies = downloads.filter { it.type != "show" }.map { LibraryItem.DownloadMovie(it) }
        val shows = downloads.filter { it.type == "show" }
            .groupBy { "${it.tmdbId}:${it.title}" }
            .values
            .map { episodes ->
                LibraryItem.DownloadShow(
                    key = "download:${episodes.first().tmdbId}:${episodes.first().title}",
                    title = episodes.first().title,
                    posterPath = episodes.firstOrNull { it.posterPath != null }?.posterPath,
                    episodes = episodes.sortedWith(compareBy<DownloadEntity> { it.season ?: 0 }.thenBy { it.episode ?: 0 }),
                )
            }
        return (movies + shows).sortedBy { itemTitle(it).lowercase() }
    }

    private fun groupLocalMedia(media: List<LocalMediaEntity>): List<LibraryItem.LocalGroup> =
        media.filterNot { it.matchSource == "database" }
            .groupBy { it.groupKey.ifBlank { "${it.mediaKind}:${it.groupTitle}" } }
            .map { (key, items) ->
                LibraryItem.LocalGroup(
                    key = "local:$key",
                    title = items.first().groupTitle,
                    mediaKind = items.first().mediaKind,
                    posterPath = items.firstOrNull { it.posterPath != null }?.posterPath,
                    thumbnailPath = items.firstOrNull { it.thumbnailPath != null }?.thumbnailPath,
                    items = items.sortedWith(compareBy<LocalMediaEntity> { it.season ?: 0 }.thenBy { it.episode ?: 0 }.thenBy { it.displayName }),
                )
            }
            .sortedBy { it.title.lowercase() }

    private fun itemTitle(item: LibraryItem): String = when (item) {
        is LibraryItem.DownloadMovie -> item.entity.title
        is LibraryItem.DownloadShow -> item.title
        is LibraryItem.LocalGroup -> item.title
    }
}

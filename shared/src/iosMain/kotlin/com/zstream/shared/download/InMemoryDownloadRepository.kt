package com.zstream.shared.download

/**
 * Placeholder DownloadRepository so iOS UI can be built against the real contract before a real
 * download engine exists on iOS. Holds state only for the life of the process.
 */
class InMemoryDownloadRepository : DownloadRepository {
    private val items = mutableMapOf<String, DownloadItem>()

    override suspend fun getAllDownloads(): List<DownloadItem> = items.values.toList()

    override suspend fun getDownloadById(id: String): DownloadItem? = items[id]

    override suspend fun pauseDownload(id: String) {
        items[id]?.let { items[id] = it.copy(status = DownloadStatus.PAUSED) }
    }

    override suspend fun resumeDownload(id: String) {
        items[id]?.let { items[id] = it.copy(status = DownloadStatus.DOWNLOADING) }
    }

    override suspend fun cancelDownload(id: String) {
        items[id]?.let { items[id] = it.copy(status = DownloadStatus.CANCELLED) }
    }

    override suspend fun removeDownload(id: String) {
        items.remove(id)
    }
}

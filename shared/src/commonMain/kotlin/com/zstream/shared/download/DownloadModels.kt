package com.zstream.shared.download

enum class DownloadStatus { QUEUED, DOWNLOADING, REMUXING, PAUSED, DONE, FAILED, CANCELLED }

/**
 * UI-facing view of one download. Deliberately leaner than Android's `DownloadEntity`
 * (app/data/local/entity/DownloadEntity.kt) -- segment/remux counters, SAF storage-tree URIs,
 * and raw stream/header JSON are download-*engine* internals. The engine itself (HLS segment
 * fetching, remuxing, MediaStore/SAF file placement) is not shared logic: Android uses
 * OkHttp/Media3/WorkManager, iOS needs a background URLSession-based engine with no Kotlin
 * equivalent. This model only carries what a download list/detail screen needs to render.
 */
data class DownloadItem(
    val id: String,
    val tmdbId: String,
    val type: String, // "movie" or "show"
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val qualityLabel: String,
    val posterPath: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progressPercent: Int = 0,
    val filePath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = 0L,
    val finishedAt: Long? = null,
    val speedBps: Long? = null,
    val bytesDownloaded: Long? = null,
    val estimatedTotalBytes: Long? = null,
)

/**
 * Contract for listing and managing downloads already known to the platform's download engine.
 * Starting a new download is intentionally not part of this interface -- resolving a source into
 * a downloadable stream and running the actual transfer is engine-specific per platform (see
 * app/download/DownloadResolver.kt + HlsDownloadEngine.kt on Android). This interface is the
 * shared read/manage surface a UI can build against regardless of what's driving the engine.
 */
interface DownloadRepository {
    suspend fun getAllDownloads(): List<DownloadItem>
    suspend fun getDownloadById(id: String): DownloadItem?
    suspend fun pauseDownload(id: String)
    suspend fun resumeDownload(id: String)
    suspend fun cancelDownload(id: String)
    suspend fun removeDownload(id: String)
}

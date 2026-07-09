package com.zstream.android.download

import android.util.Log
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.entity.DownloadStatus
import com.zstream.android.plugin.Caption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DownloadRepository"

/** Extension inferred from a StreamResult-style streamType/URL, for the "file" (non-HLS) case. */
private fun fileExtensionFor(streamUrl: String, streamType: String): String {
    if (streamType != "file") return "mp4"
    val fromUrl = streamUrl.substringBefore('?').substringAfterLast('.', "")
    return fromUrl.takeIf { it.length in 2..4 }?.lowercase() ?: "mp4"
}

private fun captionExtension(caption: com.zstream.android.plugin.Caption): String =
    caption.type.ifBlank { "srt" }.lowercase().removePrefix(".")

/** [bytesDownloaded]/[estimatedTotalBytes] are null during the REMUXING phase (no bytes to report there). */
data class DownloadProgressInfo(
    val status: DownloadStatus,
    val percent: Int,
    val bytesDownloaded: Long?,
    val estimatedTotalBytes: Long?,
)

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context,
    private val client: OkHttpClient,
    private val storage: DownloadStorage,
    private val downloadDao: DownloadDao,
) {
    /**
     * Destination file(s), once created, are cached here so a paused-then-resumed download keeps
     * writing into the exact same MediaStore entry/file instead of creating a second orphaned one
     * each time run() is re-entered. Cleared on any terminal outcome (done/cancelled/failed).
     */
    private val fileCache = ConcurrentHashMap<Long, Pair<DownloadFile, List<Pair<Caption, DownloadFile>>>>()

    /** Creates the DB row up front (status QUEUED) so the UI can show it immediately. */
    suspend fun enqueue(request: DownloadRequest): Long {
        val entity = DownloadEntity(
            tmdbId = request.tmdbId,
            type = if (request.target is DownloadTarget.Movie) "movie" else "show",
            title = when (val t = request.target) {
                is DownloadTarget.Movie -> t.title
                is DownloadTarget.Episode -> t.showTitle
            },
            season = (request.target as? DownloadTarget.Episode)?.season,
            episode = (request.target as? DownloadTarget.Episode)?.episode,
            episodeTitle = (request.target as? DownloadTarget.Episode)?.episodeTitle,
            sourceId = request.sourceId,
            variantId = request.variantId,
            qualityLabel = request.qualityLabel,
            posterPath = request.posterPath,
            status = DownloadStatus.QUEUED,
        )
        return downloadDao.insert(entity)
    }

    /** Runs the actual download for a previously-enqueued row. Safe to call from a foreground service. */
    suspend fun run(downloadId: Long, request: DownloadRequest, onProgress: suspend (DownloadProgressInfo) -> Unit) = withContext(Dispatchers.IO) {
        val entity = downloadDao.getById(downloadId) ?: error("Download $downloadId not found")
        Log.i(TAG, "Download $downloadId starting: source=${request.sourceId} quality=${request.qualityLabel} streamType=${request.streamType}")
        val workDir = File(appContext.cacheDir, "downloads/$downloadId")
        var videoFile: DownloadFile? = null
        var subtitleFiles: List<Pair<Caption, DownloadFile>> = emptyList()
        // Tracks the latest persisted row so a pause (which reuses this, not the stale `entity`
        // captured before any progress happened) reports the percent it actually stopped at.
        var latest = entity
        try {
            latest = entity.copy(status = DownloadStatus.DOWNLOADING)
            update(latest)

            val cached = fileCache[downloadId]
            if (cached != null) {
                videoFile = cached.first
                subtitleFiles = cached.second
                Log.i(TAG, "Download $downloadId resuming with existing destination file: ${videoFile.displayPath}")
            } else {
                val extension = fileExtensionFor(request.streamUrl, request.streamType)
                videoFile = storage.createVideoFile(request.target, extension)
                subtitleFiles = request.captions.map { caption ->
                    val providerLabel = if (caption.source.contains("wyzie", ignoreCase = true)) "Wyzie" else null
                    caption to storage.createSubtitleFile(request.target, caption.langIso.ifBlank { "und" }, captionExtension(caption), providerLabel)
                }
                fileCache[downloadId] = videoFile to subtitleFiles
            }

            when (request.streamType) {
                "file" -> {
                    storage.openOutputStream(videoFile).use { out ->
                        DirectFileDownloader.download(client, request.streamUrl, request.headers, out) { received, total ->
                            val pct = if (total != null && total > 0) ((received * 100) / total).toInt() else 0
                            latest = entity.copy(status = DownloadStatus.DOWNLOADING, progressPercent = pct)
                            update(latest)
                            onProgress(DownloadProgressInfo(DownloadStatus.DOWNLOADING, pct, received, total))
                        }
                    }
                }
                else -> {
                    latest = entity.copy(status = DownloadStatus.DOWNLOADING)
                    update(latest)
                    workDir.mkdirs()
                    val pfd = storage.openParcelFileDescriptorForMuxer(videoFile)
                    try {
                        HlsDownloadEngine(client).download(
                            videoPlaylistUrl = request.streamUrl,
                            audioPlaylistUrl = request.audioStreamUrl,
                            headers = request.headers,
                            workDir = workDir,
                            outputFd = pfd.fileDescriptor,
                            onProgress = { progress ->
                                val pct = if (progress.segmentsTotal > 0) (progress.segmentsDone * 100 / progress.segmentsTotal) else 0
                                latest = entity.copy(status = DownloadStatus.DOWNLOADING, progressPercent = pct)
                                update(latest)
                                onProgress(DownloadProgressInfo(DownloadStatus.DOWNLOADING, pct, progress.bytesReceived, progress.estimatedTotalBytes))
                            },
                            onRemuxProgress = { done, remuxTotal ->
                                val pct = if (remuxTotal > 0) done * 100 / remuxTotal else 0
                                latest = entity.copy(status = DownloadStatus.REMUXING, progressPercent = pct)
                                update(latest)
                                onProgress(DownloadProgressInfo(DownloadStatus.REMUXING, pct, null, null))
                            },
                        )
                    } finally {
                        // workDir's segment cache is deliberately NOT deleted here — if this download
                        // was paused, a CancellationException unwinds through this finally too, and
                        // resume needs those partial segments still on disk (see the catch block
                        // below, and downloadSegment()'s "skip if already exists" check).
                        pfd.close()
                    }
                }
            }

            for ((caption, dest) in subtitleFiles) {
                runCatching {
                    storage.openOutputStream(dest).use { out ->
                        val req = Request.Builder().url(caption.url).headers(downloadHeaders(emptyMap())).build()
                        client.newCall(req).execute().use { resp ->
                            resp.body?.byteStream()?.copyTo(out)
                        }
                    }
                    storage.finalize(dest)
                }.onFailure { Log.w(TAG, "Subtitle download failed for ${caption.langIso}: ${it.message}") }
            }

            storage.finalize(videoFile)
            val finalSize = storage.fileSize(videoFile)
            Log.i(TAG, "Download $downloadId finished: file=${videoFile.displayPath} size=${finalSize?.let { "${it / 1024}KB" } ?: "unknown"}")
            fileCache.remove(downloadId)
            workDir.deleteRecursively()

            update(
                entity.copy(
                    status = DownloadStatus.DONE,
                    progressPercent = 100,
                    filePath = videoFile.displayPath,
                    subtitlePaths = subtitleFiles.map { it.second.displayPath },
                    finishedAt = System.currentTimeMillis(),
                )
            )
        } catch (ce: CancellationException) {
            if (DownloadControl.consumePauseRequested(downloadId)) {
                Log.i(TAG, "Download $downloadId paused at ${latest.progressPercent}% — segment cache and destination file kept for resume")
                update(latest.copy(status = DownloadStatus.PAUSED))
            } else {
                Log.i(TAG, "Download $downloadId cancelled — cleaning up")
                fileCache.remove(downloadId)
                workDir.deleteRecursively()
                videoFile?.let { runCatching { storage.delete(it); storage.deleteEmptyFolder(it.displayPath) } }
                subtitleFiles.forEach { (_, dest) -> runCatching { storage.delete(dest) } }
                downloadDao.delete(entity)
            }
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "Download $downloadId failed: ${t.message}", t)
            // A failed/partial download otherwise leaves a MediaStore row stuck IS_PENDING=1 (or a
            // half-written legacy file) — visible in the Downloads folder as a raw ".pending-..."
            // stub forever, since nothing else ever calls finalize()/delete() for it.
            fileCache.remove(downloadId)
            workDir.deleteRecursively()
            videoFile?.let { runCatching { storage.delete(it); storage.deleteEmptyFolder(it.displayPath) } }
            subtitleFiles.forEach { (_, dest) -> runCatching { storage.delete(dest) } }
            update(latest.copy(status = DownloadStatus.FAILED, errorMessage = t.message ?: "Download failed"))
        }
    }

    private suspend fun update(entity: DownloadEntity) {
        downloadDao.update(entity)
    }
}

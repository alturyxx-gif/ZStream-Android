package com.zstream.android.download

import android.util.Log
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.entity.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
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

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context,
    private val client: OkHttpClient,
    private val storage: DownloadStorage,
    private val downloadDao: DownloadDao,
) {
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
            status = DownloadStatus.QUEUED,
        )
        return downloadDao.insert(entity)
    }

    /** Runs the actual download for a previously-enqueued row. Safe to call from a foreground service. */
    suspend fun run(downloadId: Long, request: DownloadRequest, onProgress: suspend (Int) -> Unit) = withContext(Dispatchers.IO) {
        val entity = downloadDao.getById(downloadId) ?: error("Download $downloadId not found")
        Log.i(TAG, "Download $downloadId starting: source=${request.sourceId} quality=${request.qualityLabel} streamType=${request.streamType}")
        try {
            update(entity.copy(status = DownloadStatus.DOWNLOADING))

            val extension = fileExtensionFor(request.streamUrl, request.streamType)
            val videoFile = storage.createVideoFile(request.target, extension)
            val subtitleFiles = request.captions.map { caption ->
                caption to storage.createSubtitleFile(request.target, caption.langIso.ifBlank { "und" }, captionExtension(caption))
            }

            when (request.streamType) {
                "file" -> {
                    storage.openOutputStream(videoFile).use { out ->
                        DirectFileDownloader.download(client, request.streamUrl, request.headers, out) { received, total ->
                            val pct = if (total != null && total > 0) ((received * 100) / total).toInt() else 0
                            onProgress(pct)
                        }
                    }
                }
                else -> {
                    update(entity.copy(status = DownloadStatus.DOWNLOADING))
                    val workDir = File(appContext.cacheDir, "downloads/$downloadId").apply { mkdirs() }
                    val pfd = storage.openParcelFileDescriptorForMuxer(videoFile)
                    try {
                        HlsDownloadEngine(client).download(
                            videoPlaylistUrl = request.streamUrl,
                            audioPlaylistUrl = null,
                            headers = request.headers,
                            workDir = workDir,
                            outputFd = pfd.fileDescriptor,
                        ) { progress ->
                            val pct = if (progress.segmentsTotal > 0) (progress.segmentsDone * 90 / progress.segmentsTotal) else 0
                            // Reserve the last 10% for the remux step's own duration; segment progress
                            // dominates the total time so this keeps the bar from looking stuck at 100%.
                            onProgress(pct)
                        }
                    } finally {
                        pfd.close()
                        workDir.deleteRecursively()
                    }
                    update(entity.copy(status = DownloadStatus.REMUXING))
                    onProgress(100)
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

            update(
                entity.copy(
                    status = DownloadStatus.DONE,
                    progressPercent = 100,
                    filePath = videoFile.displayPath,
                    subtitlePaths = subtitleFiles.map { it.second.displayPath },
                    finishedAt = System.currentTimeMillis(),
                )
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Download $downloadId failed: ${t.message}", t)
            update(entity.copy(status = DownloadStatus.FAILED, errorMessage = t.message ?: "Download failed"))
        }
    }

    private suspend fun update(entity: DownloadEntity) {
        downloadDao.update(entity)
    }
}

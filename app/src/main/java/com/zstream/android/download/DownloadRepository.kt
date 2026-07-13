package com.zstream.android.download

import android.util.Log
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zstream.android.data.SkipSegmentRepository
import com.zstream.android.data.VideoFingerprint
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.entity.DownloadStatus
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.plugin.Caption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
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

private val requestCodecGson = Gson()
private val headersMapType = object : TypeToken<Map<String, String>>() {}.type
private val captionsListType = object : TypeToken<List<Caption>>() {}.type

fun DownloadEntity.toRequest(): DownloadRequest? {
    val url = streamUrl ?: return null
    val type = streamType ?: return null
    val target = if (this.type == "movie") {
        DownloadTarget.Movie(title = title)
    } else {
        DownloadTarget.Episode(showTitle = title, season = season ?: 1, episode = episode ?: 1, episodeTitle = episodeTitle)
    }
    val headers: Map<String, String> = headersJson?.let {
        runCatching { requestCodecGson.fromJson<Map<String, String>>(it, headersMapType) }.getOrNull()
    }.orEmpty()
    val captions: List<Caption> = captionsJson?.let {
        runCatching { requestCodecGson.fromJson<List<Caption>>(it, captionsListType) }.getOrNull()
    }.orEmpty()
    return DownloadRequest(
        tmdbId = tmdbId,
        target = target,
        sourceId = sourceId,
        sourceDisplayName = sourceId,
        variantId = variantId,
        qualityLabel = qualityLabel,
        streamUrl = url,
        streamType = type,
        headers = headers,
        captions = captions,
        audioStreamUrl = audioStreamUrl,
        audioLanguage = audioLanguage,
        posterPath = posterPath,
        destinationTreeUri = storageTreeUri,
    )
}

/** [bytesDownloaded]/[estimatedTotalBytes] are null during the REMUXING phase (no bytes to report there). */
data class DownloadProgressInfo(
    val status: DownloadStatus,
    val percent: Int,
    val bytesDownloaded: Long?,
    val estimatedTotalBytes: Long?,
    val segDone: Int = 0,
    val segTotal: Int = 0,
    val speedBps: Long? = null,
    val statusMessage: String? = null,
)

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context,
    private val client: OkHttpClient,
    private val storage: DownloadStorage,
    private val downloadDao: DownloadDao,
    private val skipSegmentRepository: SkipSegmentRepository,
    private val settingsPrefs: SettingsPreferences,
) {
    /**
     * Destination file(s), once created, are cached here so a paused-then-resumed download keeps
     * writing into the exact same MediaStore entry/file instead of creating a second orphaned one
     * each time run() is re-entered. Cleared on any terminal outcome (done/cancelled/failed).
     */
    private val fileCache = ConcurrentHashMap<Long, Pair<DownloadFile, List<Pair<Caption, DownloadFile>>>>()

    /** Detached from run()'s coroutine so a fire-and-forget skip-segment cache warm can't be cancelled by download completion. */
    private val skipCacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            streamUrl = request.streamUrl,
            streamType = request.streamType,
            audioStreamUrl = request.audioStreamUrl,
            audioLanguage = request.audioLanguage,
            headersJson = requestCodecGson.toJson(request.headers),
            captionsJson = requestCodecGson.toJson(request.captions),
            storageTreeUri = request.destinationTreeUri,
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
                val treeUri = request.destinationTreeUri?.let(Uri::parse)
                val extension = fileExtensionFor(request.streamUrl, request.streamType)
                videoFile = storage.createVideoFile(request.target, extension, treeUri)
                subtitleFiles = request.captions.map { caption ->
                    val providerLabel = if (caption.source.contains("wyzie", ignoreCase = true)) "Wyzie" else null
                    caption to storage.createSubtitleFile(request.target, caption.langIso.ifBlank { "und" }, captionExtension(caption), providerLabel, treeUri)
                }
                fileCache[downloadId] = videoFile to subtitleFiles
            }

            when (request.streamType) {
                "file" -> {
                    var speedLastAtMs = System.currentTimeMillis()
                    var speedLastBytes = 0L
                    var speedEwma: Long? = null
                    storage.openOutputStream(videoFile).use { out ->
                        DirectFileDownloader.download(client, request.streamUrl, request.headers, out) { received, total ->
                            val pct = if (total != null && total > 0) ((received * 100) / total).toInt() else 0
                            val now = System.currentTimeMillis()
                            val deltaMs = now - speedLastAtMs
                            if (deltaMs > 0) {
                                val instantaneous = (received - speedLastBytes) * 1000 / deltaMs
                                speedEwma = speedEwma?.let { (it * 3 + instantaneous) / 4 } ?: instantaneous
                                speedLastAtMs = now
                                speedLastBytes = received
                            }
                            latest = entity.copy(
                                status = DownloadStatus.DOWNLOADING,
                                progressPercent = pct,
                                speedBps = speedEwma,
                                bytesDownloaded = received,
                                estimatedTotalBytes = total,
                            )
                            update(latest)
                            onProgress(DownloadProgressInfo(DownloadStatus.DOWNLOADING, pct, received, total, speedBps = speedEwma))
                        }
                    }
                }
                else -> {
                    latest = entity.copy(status = DownloadStatus.DOWNLOADING)
                    update(latest)
                    workDir.mkdirs()
                    val pfd = storage.openParcelFileDescriptorForMuxer(videoFile)
                    var speedLastAtMs = System.currentTimeMillis()
                    var speedLastBytes = 0L
                    var speedEwma: Long? = null
                    var downloadsComplete = false
                    val stateMutex = kotlinx.coroutines.sync.Mutex()
                    val parallelDownload = settingsPrefs.settings.first().allowParallelDownload
                    val isMagnolia = request.sourceId.equals("magnolia", ignoreCase = true)
                    val segmentWorkers = when {
                        isMagnolia && parallelDownload -> MAGNOLIA_PARALLEL_MODE_SEGMENT_WORKERS
                        isMagnolia -> MAGNOLIA_SEGMENT_WORKERS
                        parallelDownload -> PARALLEL_MODE_SEGMENT_WORKERS
                        else -> DEFAULT_SEGMENT_WORKERS
                    }
                    val maxSegmentWorkers = when {
                        isMagnolia && parallelDownload -> MAGNOLIA_ADAPTIVE_MAX_WORKERS_PARALLEL
                        isMagnolia -> MAGNOLIA_ADAPTIVE_MAX_WORKERS_SINGLE
                        parallelDownload -> ADAPTIVE_MAX_WORKERS_PARALLEL
                        else -> ADAPTIVE_MAX_WORKERS_SINGLE
                    }
                    try {
                        HlsDownloadEngine(client).download(
                            videoPlaylistUrl = request.streamUrl,
                            audioPlaylistUrl = request.audioStreamUrl,
                            headers = request.headers,
                            workDir = workDir,
                            outputFd = pfd.fileDescriptor,
                            onProgress = { progress ->
                                stateMutex.withLock {
                                    val pct = if (progress.segmentsTotal > 0) (progress.segmentsDone * 100 / progress.segmentsTotal) else 0
                                    val now = System.currentTimeMillis()
                                    val deltaMs = now - speedLastAtMs
                                    if (deltaMs > 0) {
                                        val instantaneous = (progress.bytesReceived - speedLastBytes) * 1000 / deltaMs
                                        speedEwma = speedEwma?.let { (it * 3 + instantaneous) / 4 } ?: instantaneous
                                        speedLastAtMs = now
                                        speedLastBytes = progress.bytesReceived
                                    }
                                    if (progress.segmentsTotal > 0 && progress.segmentsDone >= progress.segmentsTotal) downloadsComplete = true
                                    latest = latest.copy(
                                        status = DownloadStatus.DOWNLOADING,
                                        progressPercent = pct,
                                        segDone = progress.segmentsDone,
                                        segTotal = progress.segmentsTotal,
                                        speedBps = speedEwma,
                                        bytesDownloaded = progress.bytesReceived,
                                        estimatedTotalBytes = progress.estimatedTotalBytes,
                                    )
                                    update(latest)
                                    onProgress(DownloadProgressInfo(DownloadStatus.DOWNLOADING, pct, progress.bytesReceived, progress.estimatedTotalBytes, progress.segmentsDone, progress.segmentsTotal, speedEwma))
                                }
                            },
                            onRemuxProgress = { done, remuxTotal ->
                                stateMutex.withLock {
                                    if (downloadsComplete) {
                                        val pct = if (remuxTotal > 0) done * 100 / remuxTotal else 0
                                        latest = latest.copy(status = DownloadStatus.REMUXING, progressPercent = pct, remuxDone = done, remuxTotal = remuxTotal, statusMessage = null)
                                        update(latest)
                                        onProgress(DownloadProgressInfo(DownloadStatus.REMUXING, pct, null, null))
                                    } else {
                                        latest = latest.copy(remuxDone = done, remuxTotal = remuxTotal)
                                        update(latest)
                                    }
                                }
                            },
                            onStall = { message ->
                                stateMutex.withLock {
                                    latest = latest.copy(statusMessage = message)
                                    update(latest)
                                }
                            },
                            segmentWorkers = segmentWorkers,
                            maxSegmentWorkers = maxSegmentWorkers,
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
            val fingerprint = VideoFingerprint.compute(appContext, videoFile.playableUri(), finalSize, null)
            Log.i(TAG, "Download $downloadId finished: file=${videoFile.displayPath} size=${finalSize?.let { "${it / 1024}KB" } ?: "unknown"}")
            fileCache.remove(downloadId)
            workDir.deleteRecursively()

            update(
                entity.copy(
                    status = DownloadStatus.DONE,
                    progressPercent = 100,
                    filePath = videoFile.displayPath,
                    contentFingerprint = fingerprint,
                    subtitlePaths = subtitleFiles.map { it.second.displayPath },
                    finishedAt = System.currentTimeMillis(),
                )
            )

            skipCacheScope.launch {
                // Let the connection pool/radio settle right after a large download finishes before firing another request.
                kotlinx.coroutines.delay(3000)
                val settingsValue = settingsPrefs.settings.first()
                skipSegmentRepository.warmCache(
                    tmdbId = entity.tmdbId,
                    mediaType = if (entity.type == "show") "tv" else entity.type,
                    season = entity.season,
                    episode = entity.episode,
                    durationMs = 0L,
                    tidbKey = settingsValue.tidbKey,
                    febboxKey = settingsValue.febboxKey,
                )
            }
        } catch (ce: CancellationException) {
            withContext(NonCancellable) {
                if (DownloadControl.consumePauseRequested(downloadId)) {
                    Log.i(TAG, "Download $downloadId paused at ${latest.progressPercent}% — segment cache and destination file kept for resume")
                    update(latest.copy(status = DownloadStatus.PAUSED))
                } else {
                    Log.i(TAG, "Download $downloadId cancelled — cleaning up")
                    fileCache.remove(downloadId)
                    workDir.deleteRecursively()
                    videoFile?.let { runCatching { storage.delete(it); storage.deleteEmptyFolder(it.displayPath, request.destinationTreeUri?.let(Uri::parse)) } }
                    subtitleFiles.forEach { (_, dest) -> runCatching { storage.delete(dest) } }
                    downloadDao.delete(entity)
                }
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

    private fun DownloadFile.playableUri(): Uri = when (this) {
        is DownloadFile.MediaStoreFile -> uri
        is DownloadFile.LegacyFile -> Uri.fromFile(file)
        is DownloadFile.TreeFile -> uri
    }
}

package com.zstream.android.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zstream.android.R
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.entity.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val CHANNEL_ID = "downloads"
private const val NOTIFICATION_ID = 4301
private const val EXTRA_DOWNLOAD_ID = "download_id"

/**
 * Foreground service that runs one download at a time, in the order requests arrive. Each
 * request's actual data comes from DownloadQueue (see its doc comment) — the Intent only carries
 * the DB row id.
 */
@AndroidEntryPoint
class DownloadService : Service() {
    @Inject lateinit var repository: DownloadRepository
    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var storage: DownloadStorage

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val queue = Channel<Long>(Channel.UNLIMITED)
    private var workerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // onStartCommand() always runs right after onCreate() for the initial start, and is the
        // only place startForeground() is guaranteed to be called for every subsequent start too
        // (see its comment) — no need to duplicate the call here.
        scope.launch { cleanupFromPreviousProcess() }
        workerJob = scope.launch {
            for (downloadId in queue) {
                runOne(downloadId)
            }
        }
    }

    /**
     * A download's temp segment cache (cacheDir/downloads/<id>) only gets deleted in a `finally`
     * block once the download finishes or fails — if the process was killed mid-download instead
     * (app force-stopped, OOM-killed), that cleanup never ran and the segments just sit there.
     * DownloadService starting fresh means nothing is legitimately using that directory yet, so
     * it's always safe to wipe it here, and to mark any rows still claiming to be in-flight as
     * failed (they can't have survived the process dying).
     */
    private suspend fun cleanupFromPreviousProcess() {
        File(cacheDir, "downloads").deleteRecursively()
        downloadDao.getInFlight().forEach { stale ->
            downloadDao.update(stale.copy(status = DownloadStatus.FAILED, errorMessage = "Interrupted"))
        }
        val swept = storage.sweepOrphanedPendingFiles()
        if (swept > 0) android.util.Log.i("DownloadService", "swept $swept orphaned pending file(s) from a previous crashed download")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must re-promote to foreground on every start, not just in onCreate() — a previous
        // download can demote this same still-alive service (see updateNotification's
        // stopForeground call), and onCreate() never runs again for an already-running instance.
        // Skipping this on a subsequent startForegroundService() call is exactly what throws
        // ForegroundServiceDidNotStartInTimeException after Android's 5-second grace period.
        startForeground(NOTIFICATION_ID, buildNotification("ZStream Download", "Preparing download…", 0))
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L
        if (downloadId >= 0) {
            scope.launch { queue.send(downloadId) }
        }
        return START_NOT_STICKY
    }

    private suspend fun runOne(downloadId: Long) {
        val request = DownloadQueue.take(downloadId) ?: return
        val title = titleFor(request.target)
        updateNotification(title, "Starting download…", 0)
        repository.run(downloadId, request) { progress ->
            val text = when (progress.status) {
                DownloadStatus.REMUXING -> "Remuxing… ${progress.percent}%"
                else -> {
                    val sizeInfo = progress.bytesDownloaded?.let { downloaded ->
                        val downloadedMb = downloaded / (1024 * 1024)
                        val estimatedMb = progress.estimatedTotalBytes?.let { it / (1024 * 1024) }
                        if (estimatedMb != null) "$downloadedMb MB / ~$estimatedMb MB" else "$downloadedMb MB"
                    }
                    "${request.qualityLabel} via ${request.sourceId} — ${progress.percent}%" +
                        (sizeInfo?.let { " ($it)" } ?: "")
                }
            }
            updateNotification(title, text, progress.percent)
        }
        val entity = downloadDao.getById(downloadId)
        if (entity?.status == DownloadStatus.DONE) {
            updateNotification(title, "Download complete", 100, ongoing = false)
        } else {
            updateNotification(title, "Download failed: ${entity?.errorMessage ?: "Unknown error"}", 0, ongoing = false)
        }
    }

    private fun titleFor(target: DownloadTarget): String = when (target) {
        is DownloadTarget.Movie -> target.title
        is DownloadTarget.Episode -> "${target.showTitle} S${target.season.toString().padStart(2, '0')}E${target.episode.toString().padStart(2, '0')}"
    }

    private fun updateNotification(title: String, text: String, percent: Int, ongoing: Boolean = true) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, text, percent, ongoing))
        if (!ongoing) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
    }

    private fun buildNotification(title: String, text: String, percent: Int, ongoing: Boolean = true) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(ongoing)
            .setProgress(100, percent, false)
            .setOnlyAlertOnce(true)
            .build()

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        workerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun enqueue(context: Context, downloadId: Long, request: DownloadRequest) {
            DownloadQueue.put(downloadId, request)
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

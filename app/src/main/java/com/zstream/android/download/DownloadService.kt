package com.zstream.android.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zstream.android.MainActivity
import com.zstream.android.R
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.entity.DownloadStatus
import com.zstream.android.data.local.preferences.SettingsPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val CHANNEL_ID = "downloads"
private const val NOTIFICATION_ID = 4301
private const val EXTRA_DOWNLOAD_ID = "download_id"
const val OPEN_DOWNLOADS_EXTRA = "open_downloads"

private const val ACTION_PAUSE = "com.zstream.android.download.action.PAUSE"
private const val ACTION_RESUME = "com.zstream.android.download.action.RESUME"
private const val ACTION_CANCEL = "com.zstream.android.download.action.CANCEL"

/**
 * Foreground service that runs downloads in the order requests arrive, one at a time by default
 * or up to 5 concurrently when the "Allow parallel download" setting is on (see
 * concurrencyLimit()). Each request's actual data comes from DownloadQueue (see its doc comment)
 * — the Intent only carries the DB row id.
 */
@AndroidEntryPoint
class DownloadService : Service() {
    @Inject lateinit var repository: DownloadRepository
    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var storage: DownloadStorage
    @Inject lateinit var settingsPrefs: SettingsPreferences

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val queue = Channel<Long>(Channel.UNLIMITED)
    private var workerJob: Job? = null

    /** Each in-flight download runs as its own child Job so pausing/cancelling one doesn't kill the worker loop or other queued downloads. */
    private val activeJobs = ConcurrentHashMap<Long, Job>()

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
                waitForFreeSlot()
                val job = launch { runOne(downloadId) }
                activeJobs[downloadId] = job
                job.invokeOnCompletion { activeJobs.remove(downloadId) }
            }
        }
    }

    /** 1 at a time by default; up to 5 concurrent when "Allow parallel download" is on. */
    private suspend fun concurrencyLimit(): Int =
        if (settingsPrefs.settings.first().allowParallelDownload) 5 else 1

    private suspend fun waitForFreeSlot() {
        while (activeJobs.size >= concurrencyLimit()) {
            delay(300)
        }
    }

    private suspend fun cleanupFromPreviousProcess() {
        downloadDao.getInFlight().forEach { stale ->
            if (DownloadQueue.get(stale.id) != null) return@forEach
            val request = stale.toRequest()
            if (request != null) {
                DownloadQueue.put(stale.id, request)
                downloadDao.update(stale.copy(status = DownloadStatus.PAUSED))
            } else {
                File(cacheDir, "downloads/${stale.id}").deleteRecursively()
                downloadDao.update(stale.copy(status = DownloadStatus.FAILED, errorMessage = "Interrupted"))
            }
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
        startForeground(NOTIFICATION_ID, buildNotification("ZStream Download", "Preparing download…", 0, downloadId = -1, status = DownloadStatus.QUEUED))
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L
        if (downloadId >= 0) {
            when (intent?.action) {
                ACTION_PAUSE -> scope.launch { handlePause(downloadId) }
                ACTION_CANCEL -> scope.launch { handleCancel(downloadId) }
                ACTION_RESUME -> scope.launch { handleResume(downloadId) }
                else -> scope.launch { queue.send(downloadId) }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun handlePause(downloadId: Long) {
        val job = activeJobs[downloadId]
        if (job != null) {
            DownloadControl.markPauseRequested(downloadId)
            // cancelAndJoin (not cancel) — runOne() is itself cancelled by this, so it never
            // reaches its own post-download notification update; we must push the paused
            // notification ourselves once the repository's catch block has persisted the row.
            job.cancelAndJoin()
            val entity = downloadDao.getById(downloadId) ?: return
            val title = DownloadQueue.get(downloadId)?.let { titleFor(it.target) } ?: entity.title
            updateNotification(title, "Paused at ${entity.progressPercent}%", entity.progressPercent, downloadId, DownloadStatus.PAUSED)
        } else {
            val entity = downloadDao.getById(downloadId) ?: return
            if (entity.status == DownloadStatus.QUEUED) {
                downloadDao.update(entity.copy(status = DownloadStatus.PAUSED))
                updateNotification(entity.title, "Paused at ${entity.progressPercent}%", entity.progressPercent, downloadId, DownloadStatus.PAUSED)
            }
        }
    }

    private suspend fun handleCancel(downloadId: Long) {
        val job = activeJobs[downloadId]
        if (job != null) {
            // Not flagging DownloadControl here means the repository's catch block will treat
            // this cancellation as a real cancel (clean up files, delete the row) rather than a pause.
            job.cancelAndJoin()
        } else {
            val entity = downloadDao.getById(downloadId) ?: return
            entity.filePath?.let { runCatching { storage.deleteByDisplayPath(it); storage.deleteEmptyFolder(it) } }
            entity.subtitlePaths?.forEach { path -> runCatching { storage.deleteByDisplayPath(path) } }
            DownloadQueue.remove(downloadId)
            downloadDao.delete(entity)
        }
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private suspend fun handleResume(downloadId: Long) {
        val entity = downloadDao.getById(downloadId) ?: return
        if (entity.status != DownloadStatus.PAUSED && entity.status != DownloadStatus.FAILED) return
        if (DownloadQueue.get(downloadId) == null) {
            val request = entity.toRequest() ?: return
            DownloadQueue.put(downloadId, request)
        }
        downloadDao.update(entity.copy(status = DownloadStatus.QUEUED, errorMessage = null))
        queue.send(downloadId)
    }

    private suspend fun runOne(downloadId: Long) {
        val request = DownloadQueue.get(downloadId) ?: return
        val entity = downloadDao.getById(downloadId) ?: return
        if (entity.status == DownloadStatus.PAUSED || entity.status == DownloadStatus.CANCELLED) return

        val title = titleFor(request.target)
        updateNotification(title, "Starting download…", 0, downloadId, DownloadStatus.DOWNLOADING)
        repository.run(downloadId, request) { progress ->
            val text = when (progress.status) {
                DownloadStatus.REMUXING -> "Remuxing… ${progress.percent}%"
                else -> {
                    val sizeInfo = progress.bytesDownloaded?.let { downloaded ->
                        val downloadedMb = downloaded / (1024 * 1024)
                        val estimatedMb = progress.estimatedTotalBytes?.let { it / (1024 * 1024) }
                        if (estimatedMb != null) "$downloadedMb MB / ~$estimatedMb MB" else "$downloadedMb MB"
                    }
                    "${request.qualityLabel} via ${request.sourceDisplayName} — ${progress.percent}%" +
                        (sizeInfo?.let { " ($it)" } ?: "")
                }
            }
            updateNotification(title, text, progress.percent, downloadId, progress.status)
        }
        val finalEntity = downloadDao.getById(downloadId)
        when (finalEntity?.status) {
            DownloadStatus.DONE -> {
                DownloadQueue.remove(downloadId)
                updateNotification(title, "Download complete", 100, downloadId, DownloadStatus.DONE, ongoing = false)
            }
            DownloadStatus.PAUSED -> {
                updateNotification(title, "Paused", finalEntity.progressPercent, downloadId, DownloadStatus.PAUSED)
            }
            null -> {
                // Row was deleted — a true cancel already cleaned everything up.
                DownloadQueue.remove(downloadId)
            }
            else -> {
                DownloadQueue.remove(downloadId)
                updateNotification(title, "Download failed: ${finalEntity?.errorMessage ?: "Unknown error"}", 0, downloadId, DownloadStatus.FAILED, ongoing = false)
            }
        }
    }

    private fun titleFor(target: DownloadTarget): String = when (target) {
        is DownloadTarget.Movie -> target.title
        is DownloadTarget.Episode -> "${target.showTitle} S${target.season.toString().padStart(2, '0')}E${target.episode.toString().padStart(2, '0')}"
    }

    private fun updateNotification(title: String, text: String, percent: Int, downloadId: Long, status: DownloadStatus, ongoing: Boolean = true) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, text, percent, downloadId, status, ongoing))
        if (!ongoing) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
    }

    private fun buildNotification(
        title: String,
        text: String,
        percent: Int,
        downloadId: Long,
        status: DownloadStatus,
        ongoing: Boolean = true,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(OPEN_DOWNLOADS_EXTRA, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(ongoing)
            .setProgress(100, percent, false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)

        if (ongoing && downloadId >= 0) {
            val isPaused = status == DownloadStatus.PAUSED
            builder.addAction(0, if (isPaused) "Resume" else "Pause", actionPendingIntent(if (isPaused) ACTION_RESUME else ACTION_PAUSE, downloadId))
            builder.addAction(0, "Cancel", actionPendingIntent(ACTION_CANCEL, downloadId))
        }
        return builder.build()
    }

    private fun actionPendingIntent(action: String, downloadId: Long): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).setAction(action).putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        return PendingIntent.getService(
            this, "$action$downloadId".hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

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

        fun pause(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_PAUSE)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun resume(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_RESUME)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

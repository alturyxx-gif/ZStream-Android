package com.zstream.android.data.adb

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zstream.android.MainActivity
import com.zstream.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

const val DEFAULT_RELEASE_REPOSITORY = "https://github.com/alturyxx-gif/ZStream-Android"
const val RELEASE_UPDATE_EXTRA = "release_update"
const val OPEN_TV_INSTALLER_EXTRA = "open_tv_installer"

enum class ReleaseCheckInterval(val label: String, val hours: Long) {
    HOUR("1 hour", 1),
    DAY("1 day", 24),
    WEEK("1 week", 24 * 7);

    companion object {
        fun fromLabel(label: String): ReleaseCheckInterval = entries.firstOrNull { it.label == label } ?: DAY
    }
}

data class ReleaseSnapshot(
    val hashes: String,
    val version: String,
    val releaseUrl: String,
    val downloadUrl: String,
)

internal fun latestReleaseSnapshot(apks: List<GithubApkAsset>): ReleaseSnapshot? {
    val latest = apks.firstOrNull() ?: return null
    val hashes = apks.asSequence()
        .filter { it.releaseId == latest.releaseId }
        .map { it.digest ?: "${it.assetId}:${it.size}:${it.assetCreatedAt}" }
        .sorted()
        .joinToString("|")
    return ReleaseSnapshot(hashes, latest.releaseTag.ifBlank { latest.version }, latest.releaseUrl, latest.downloadUrl)
}

internal fun releaseChanged(previousHashes: String?, previousVersion: String?, latest: ReleaseSnapshot): Boolean =
    previousHashes != null && previousVersion != null &&
        (previousHashes != latest.hashes || previousVersion != latest.version)

/** Strips a leading "v"/"V" so release tags ("v1.3") and BuildConfig.VERSION_NAME ("v1.2") compare equal regardless of casing/prefix. */
internal fun normalizeReleaseVersion(v: String): String = v.trim().removePrefix("v").removePrefix("V")

/** True once the release we'd otherwise notify about is the version already running -- covers the
 * case where the user side-loaded the latest APK themselves (outside this manager's own baseline
 * tracking), so the stored hash/version baseline is stale but there's genuinely nothing to update. */
internal fun isAlreadyRunning(latestVersion: String, installedVersion: String): Boolean =
    normalizeReleaseVersion(latestVersion) == normalizeReleaseVersion(installedVersion)

data class ReleaseUpdateLaunch(val openTvInstaller: Boolean)

object ReleaseUpdateNavigation {
    private val _launch = MutableStateFlow<ReleaseUpdateLaunch?>(null)
    val launch = _launch.asStateFlow()

    fun dispatch(openTvInstaller: Boolean) {
        _launch.value = ReleaseUpdateLaunch(openTvInstaller)
    }

    fun consume() {
        _launch.value = null
    }
}

@Singleton
class ReleaseUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    private val _interval = MutableStateFlow(ReleaseCheckInterval.fromLabel(prefs.getString(KEY_INTERVAL, null).orEmpty()))
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.w("ReleaseUpdateManager", "Launch update check failed", e)
        }
    )

    val enabled = _enabled.asStateFlow()
    val interval = _interval.asStateFlow()
    val repositoryUrl: String get() = prefs.getString(KEY_REPOSITORY, DEFAULT_RELEASE_REPOSITORY).orEmpty()
    val hasPendingUpdate: Boolean get() = prefs.getBoolean(KEY_PENDING, false)
    val pendingReleaseUrl: String get() = prefs.getString(KEY_PENDING_URL, null) ?: "$repositoryUrl/releases"
    val pendingVersion: String get() = prefs.getString(KEY_PENDING_VERSION, null).orEmpty()
    val pendingDownloadUrl: String? get() = prefs.getString(KEY_PENDING_DOWNLOAD_URL, null)

    fun start() {
        createNotificationChannel()
        healStalePendingUpdate()
        schedule()
        checkOnLaunch()
    }

    /**
     * Synchronous, no-network self-heal for a pending-update flag left over from before the user
     * side-loaded the very release it points at -- e.g. they installed the latest APK by hand
     * outside this manager's own tracked baseline. Runs before the async launch check so a stale
     * "update available" prompt never flashes on screen even for a moment after the app reopens
     * already-updated.
     */
    private fun healStalePendingUpdate() {
        if (!hasPendingUpdate) return
        val pending = pendingVersion.takeIf { it.isNotBlank() } ?: return
        if (isAlreadyRunning(pending, com.zstream.android.BuildConfig.VERSION_NAME)) {
            clearPendingUpdate()
        }
    }

    /**
     * Fire-and-forget check run once per app open, in addition to the periodic WorkManager job --
     * catches updates published between periodic runs (which can be as sparse as once a week)
     * without waiting for the next scheduled check. Respects the same "disable background checks"
     * flag as the periodic job, and reuses checkForUpdate()'s hash/version baseline so it never
     * re-notifies for a release the user has already been told about (whether that notification
     * came from this launch check or the periodic worker).
     */
    fun checkOnLaunch() {
        if (!_enabled.value || repositoryUrl.isBlank()) return
        scope.launch {
            val apks = GithubReleaseCatalog().loadAllApks(repositoryUrl)
            if (checkForUpdate(apks)) showUpdateNotification()
        }
    }

    fun setRepositoryUrl(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl == repositoryUrl) return
        prefs.edit()
            .putString(KEY_REPOSITORY, cleanUrl)
            .remove(KEY_HASHES)
            .remove(KEY_VERSION)
            .remove(KEY_PENDING)
            .remove(KEY_PENDING_URL)
            .remove(KEY_PENDING_VERSION)
            .apply()
    }

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
        schedule()
    }

    fun setInterval(value: ReleaseCheckInterval) {
        prefs.edit().putString(KEY_INTERVAL, value.label).apply()
        _interval.value = value
        schedule()
    }

    fun recordScan(apks: List<GithubApkAsset>) {
        latestReleaseSnapshot(apks)?.let {
            saveBaseline(it)
            clearPendingUpdate()
        }
    }

    fun checkForUpdate(apks: List<GithubApkAsset>): Boolean {
        val latest = latestReleaseSnapshot(apks) ?: return false
        if (isAlreadyRunning(latest.version, com.zstream.android.BuildConfig.VERSION_NAME)) {
            // Covers side-loading the latest APK outside this manager's own tracked baseline
            // (e.g. installing it by hand) -- without this the stale hash/version baseline would
            // otherwise re-fire the "update available" notification for a release already running.
            saveBaseline(latest)
            clearPendingUpdate()
            return false
        }
        val previousHashes = prefs.getString(KEY_HASHES, null)
        val previousVersion = prefs.getString(KEY_VERSION, null)
        if (previousHashes == null || previousVersion == null) {
            saveBaseline(latest)
            return false
        }
        if (!releaseChanged(previousHashes, previousVersion, latest)) return false
        saveBaseline(latest)
        prefs.edit()
            .putBoolean(KEY_PENDING, true)
            .putString(KEY_PENDING_URL, latest.releaseUrl)
            .putString(KEY_PENDING_VERSION, latest.version)
            .putString(KEY_PENDING_DOWNLOAD_URL, latest.downloadUrl)
            .apply()
        return true
    }

    /**
     * Debug-only helper that fakes a pending release update (no real download URL, so the
     * install button in the update dialog just reports "no APK found") purely so the update UI
     * can be exercised without waiting for or faking an actual GitHub release.
     */
    fun simulateUpdate() {
        prefs.edit()
            .putBoolean(KEY_PENDING, true)
            .putString(KEY_PENDING_URL, "$repositoryUrl/releases")
            .putString(KEY_PENDING_VERSION, "test-${System.currentTimeMillis() % 1000}")
            .apply()
        ReleaseUpdateNavigation.dispatch(false)
    }

    fun clearPendingUpdate() {
        prefs.edit()
            .remove(KEY_PENDING).remove(KEY_PENDING_URL).remove(KEY_PENDING_VERSION).remove(KEY_PENDING_DOWNLOAD_URL)
            .apply()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    fun showUpdateNotification() {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val hasTv = TvAdbManager.get(context).getSavedTvs().isNotEmpty()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(RELEASE_UPDATE_EXTRA, true)
            putExtra(OPEN_TV_INSTALLER_EXTRA, hasTv)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            4102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val message = if (hasTv) {
            "A new APK is available. Update this phone and your paired TV."
        } else {
            "A new APK is available for this phone."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_group_megaphone)
            .setContentTitle("ZStream update available")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun saveBaseline(snapshot: ReleaseSnapshot) {
        prefs.edit().putString(KEY_HASHES, snapshot.hashes).putString(KEY_VERSION, snapshot.version).apply()
    }

    private fun schedule() {
        val workManager = WorkManager.getInstance(context)
        if (!_enabled.value) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReleaseUpdateWorker>(_interval.value.hours, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Release updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications when a watched GitHub repository publishes a new APK"
            },
        )
    }

    companion object {
        private const val PREFS = "release_updates"
        private const val KEY_REPOSITORY = "repository_url"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "interval"
        private const val KEY_HASHES = "latest_hashes"
        private const val KEY_VERSION = "latest_version"
        private const val KEY_PENDING = "pending_update"
        private const val KEY_PENDING_URL = "pending_release_url"
        private const val KEY_PENDING_VERSION = "pending_version"
        private const val KEY_PENDING_DOWNLOAD_URL = "pending_download_url"
        private const val WORK_NAME = "github-release-update-check"
        private const val CHANNEL_ID = "release_updates"
        private const val NOTIFICATION_ID = 4102
    }
}

class ReleaseUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val manager = ReleaseUpdateManager(applicationContext)
        if (!manager.enabled.value || manager.repositoryUrl.isBlank()) return Result.success()
        return try {
            val apks = GithubReleaseCatalog().loadAllApks(manager.repositoryUrl)
            if (manager.checkForUpdate(apks)) manager.showUpdateNotification()
            Result.success()
        } catch (_: IllegalArgumentException) {
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        }
    }
}

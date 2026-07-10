package com.zstream.android.download

import android.util.Log
import com.google.gson.Gson
import com.zstream.android.data.local.dao.DownloadDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DownloadIndexSync"
private val indexGson = Gson()

/**
 * Recovers DownloadEntity rows for videos that are still sitting on disk after a reinstall wipes
 * the app-private Room database. Each downloaded video has its own recovery metadata embedded
 * directly in the file (see DownloadStorage.appendMetadataBox/readMetadataBox) rather than a
 * separate index file — a prior JSON-index-file design kept hitting a hard wall: uninstalling
 * nulls out a MediaStore row's owner_package_name, and Android 13+'s granular storage permissions
 * only grant cross-app visibility for their own media type, so a plain application/json file can
 * never become queryable again once its owning install is gone, no matter the query logic. Video
 * files don't have that problem (READ_MEDIA_VIDEO covers them system-wide), so metadata riding
 * inside one survives exactly as well as the video itself does.
 */
@Singleton
class DownloadIndexSync @Inject constructor(
    private val downloadDao: DownloadDao,
    private val downloadStorage: DownloadStorage,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fire-and-forget reconcile, for app start. */
    fun start() {
        scope.launch { reconcile() }
    }

    /**
     * Scans Downloads/ZStream for video files, reads each one's embedded metadata box, and
     * re-inserts a DownloadEntity for any whose (tmdbId, season, episode) isn't already in the DB.
     * Safe to call repeatedly (e.g. a "rescan downloads" button) — already-tracked entries are
     * skipped, and a file with no box (or a corrupt one) is silently skipped rather than failing
     * the whole scan.
     */
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        val existing = downloadDao.getAllSync()
        val existingKeys = existing.map { "${it.tmdbId}|${it.season}|${it.episode}" }.toSet()

        val candidates = runCatching { downloadStorage.scanZStreamVideoFiles() }.getOrElse {
            Log.w(TAG, "Scan failed: ${it.message}")
            return@withContext
        }

        var recovered = 0
        for (ref in candidates) {
            val json = runCatching { downloadStorage.readMetadataBox(ref) }.getOrNull() ?: continue
            val entry = runCatching { indexGson.fromJson(json, DownloadIndexEntry::class.java) }.getOrNull() ?: continue
            val key = "${entry.tmdbId}|${entry.season}|${entry.episode}"
            if (key in existingKeys) continue
            runCatching { downloadDao.insert(entry.toDownloadEntity()) }
                .onSuccess { recovered++ }
                .onFailure { Log.w(TAG, "Failed to insert recovered download for key=$key: ${it.message}") }
        }
        if (recovered > 0) Log.i(TAG, "Recovered $recovered download(s) from embedded metadata")
    }
}

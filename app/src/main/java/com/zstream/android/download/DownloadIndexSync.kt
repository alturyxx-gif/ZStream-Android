package com.zstream.android.download

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zstream.android.data.local.dao.DownloadDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val indexGson = Gson()
private val indexListType = object : TypeToken<List<DownloadIndexEntry>>() {}.type

/**
 * Keeps a JSON recovery index of completed downloads in public storage (see
 * DownloadStorage.writeIndexJson) in sync with the DB, and reconciles it back on app start so a
 * reinstall — which wipes the app-private Room database but not the already-downloaded files —
 * doesn't leave the app with no record of videos that are still sitting on disk.
 */
@Singleton
class DownloadIndexSync @Inject constructor(
    private val downloadDao: DownloadDao,
    private val downloadStorage: DownloadStorage,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Reconciles once, then keeps watching for changes to the completed-downloads set. Call once at app start. */
    fun start() {
        scope.launch {
            reconcile()
            downloadDao.observeCompleted()
                .map { downloads -> downloads.mapNotNull { it.toIndexEntry() } }
                .distinctUntilChanged()
                .collect { entries ->
                    runCatching { downloadStorage.writeIndexJson(indexGson.toJson(entries, indexListType)) }
                }
        }
    }

    /** One-shot: re-inserts any indexed download missing from the DB, provided its file is still there. */
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        val json = runCatching { downloadStorage.readIndexJson() }.getOrNull() ?: return@withContext
        val entries: List<DownloadIndexEntry> = runCatching {
            indexGson.fromJson<List<DownloadIndexEntry>>(json, indexListType)
        }.getOrNull() ?: return@withContext

        val existing = downloadDao.getAllSync()
        val existingKeys = existing.map { "${it.tmdbId}|${it.season}|${it.episode}" }.toSet()

        entries.forEach { entry ->
            val key = "${entry.tmdbId}|${entry.season}|${entry.episode}"
            if (key in existingKeys) return@forEach
            if (downloadStorage.resolvePlayableUri(entry.filePath) == null) return@forEach
            runCatching { downloadDao.insert(entry.toDownloadEntity()) }
        }
    }
}

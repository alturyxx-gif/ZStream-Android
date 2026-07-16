package com.zstream.android.download

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * In-memory handoff from the quality-picker UI to DownloadService. A DownloadRequest isn't
 * trivially Parcelable (nested sealed class, plugin Caption type), and it never needs to survive
 * process death — DownloadService runs in the same process — so a simple in-memory map keyed by
 * the DB row id is simpler than wiring up serialization for an Intent extra.
 *
 * java.util.concurrent.ConcurrentHashMap isn't available on Kotlin/Native -- a plain MutableMap
 * guarded by a lock is the portable equivalent for this class's low-contention access pattern.
 */
object DownloadQueue {
    private val lock = SynchronizedObject()
    private val pending = mutableMapOf<Long, DownloadRequest>()

    fun put(downloadId: Long, request: DownloadRequest) {
        synchronized(lock) { pending[downloadId] = request }
    }

    /**
     * Non-removing lookup — a paused download's request must stay available so tapping "resume"
     * can restart it without re-resolving the source. Call [remove] explicitly once a download
     * reaches a terminal state (done/cancelled) so it doesn't leak forever.
     */
    fun get(downloadId: Long): DownloadRequest? = synchronized(lock) { pending[downloadId] }

    fun remove(downloadId: Long) {
        synchronized(lock) { pending.remove(downloadId) }
    }
}

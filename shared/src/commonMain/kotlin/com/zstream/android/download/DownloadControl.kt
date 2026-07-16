package com.zstream.android.download

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Both pause and cancel work by cancelling the same per-download coroutine Job (see
 * DownloadService) — the only difference is what DownloadRepository.run() does in response, which
 * it can't tell apart from a plain CancellationException alone. The service flags a downloadId
 * here immediately before cancelling its job for a pause (not for a cancel); the repository
 * consumes (reads + clears) that flag in its catch block to decide whether to preserve partial
 * segment files for a later resume (pause) or wipe everything (cancel).
 *
 * java.util.concurrent.ConcurrentHashMap isn't available on Kotlin/Native -- a plain MutableSet
 * guarded by a lock is the portable equivalent for this class's low-contention access pattern.
 */
object DownloadControl {
    private val lock = SynchronizedObject()
    private val pauseRequested = mutableSetOf<Long>()

    fun markPauseRequested(downloadId: Long) {
        synchronized(lock) { pauseRequested.add(downloadId) }
    }

    fun consumePauseRequested(downloadId: Long): Boolean =
        synchronized(lock) { pauseRequested.remove(downloadId) }
}

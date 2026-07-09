package com.zstream.android.download

import java.util.concurrent.ConcurrentHashMap

/**
 * Both pause and cancel work by cancelling the same per-download coroutine Job (see
 * DownloadService) — the only difference is what DownloadRepository.run() does in response, which
 * it can't tell apart from a plain CancellationException alone. The service flags a downloadId
 * here immediately before cancelling its job for a pause (not for a cancel); the repository
 * consumes (reads + clears) that flag in its catch block to decide whether to preserve partial
 * segment files for a later resume (pause) or wipe everything (cancel).
 */
object DownloadControl {
    private val pauseRequested = ConcurrentHashMap.newKeySet<Long>()

    fun markPauseRequested(downloadId: Long) {
        pauseRequested.add(downloadId)
    }

    fun consumePauseRequested(downloadId: Long): Boolean = pauseRequested.remove(downloadId)
}

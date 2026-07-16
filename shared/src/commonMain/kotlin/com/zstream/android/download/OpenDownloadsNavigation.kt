package com.zstream.android.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Signal from DownloadService's notification tap (via MainActivity's intent extra) to the
 * Compose side, telling it to navigate to the Downloads screen — mirrors
 * com.zstream.android.data.adb.ReleaseUpdateManager's ReleaseUpdateNavigation object.
 */
object OpenDownloadsNavigation {
    private val _open = MutableStateFlow(false)
    val open = _open.asStateFlow()

    fun dispatch() {
        _open.value = true
    }

    fun consume() {
        _open.value = false
    }
}

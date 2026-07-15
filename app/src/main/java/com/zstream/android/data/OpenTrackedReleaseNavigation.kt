package com.zstream.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Signal from a release-notify notification tap (via MainActivity's intent extras) to the
 * Compose side, telling it to open a specific movie/show's detail page -- mirrors
 * com.zstream.android.data.adb.ReleaseUpdateManager's ReleaseUpdateNavigation object.
 */
data class OpenTrackedReleaseTarget(val mediaType: String, val tmdbId: Int)

object OpenTrackedReleaseNavigation {
    private val _target = MutableStateFlow<OpenTrackedReleaseTarget?>(null)
    val target = _target.asStateFlow()

    fun dispatch(mediaType: String, tmdbId: Int) {
        _target.value = OpenTrackedReleaseTarget(mediaType, tmdbId)
    }

    fun consume() {
        _target.value = null
    }
}

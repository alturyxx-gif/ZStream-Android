package com.zstream.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

const val OPEN_TRACKED_RELEASE_SEASON_EXTRA = "open_tracked_release_season"
const val OPEN_TRACKED_RELEASE_EPISODE_EXTRA = "open_tracked_release_episode"

/**
 * Signal from a release-notify notification tap (via MainActivity's intent extras) to the
 * Compose side, telling it to open a specific movie/show's detail page -- mirrors
 * com.zstream.android.data.adb.ReleaseUpdateManager's ReleaseUpdateNavigation object.
 */
data class OpenTrackedReleaseTarget(
    val mediaType: String,
    val tmdbId: Int,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

object OpenTrackedReleaseNavigation {
    private val _target = MutableStateFlow<OpenTrackedReleaseTarget?>(null)
    val target = _target.asStateFlow()

    fun dispatch(mediaType: String, tmdbId: Int, seasonNumber: Int? = null, episodeNumber: Int? = null) {
        _target.value = OpenTrackedReleaseTarget(mediaType, tmdbId, seasonNumber, episodeNumber)
    }

    fun consume() {
        _target.value = null
    }
}

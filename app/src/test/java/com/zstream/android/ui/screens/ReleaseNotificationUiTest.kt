package com.zstream.android.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotificationUiTest {
    @Test
    fun unknownDateAndRetainedSubscriptionsKeepTheBellVisible() {
        assertTrue(shouldShowEpisodeReleaseNotification(isTracked = false, hasAired = true, airDate = null))
        assertTrue(shouldShowEpisodeReleaseNotification(isTracked = true, hasAired = true, airDate = "2026-07-01"))
        assertFalse(shouldShowEpisodeReleaseNotification(isTracked = false, hasAired = true, airDate = "2026-07-01"))
    }
}

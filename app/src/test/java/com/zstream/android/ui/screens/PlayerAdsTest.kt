package com.zstream.android.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerAdsTest {
    @Test
    fun vastResponseMustContainAnAd() {
        assertFalse(containsVastAd("<VAST version=\"4.0\"></VAST>"))
        assertTrue(containsVastAd("<VAST version=\"4.0\"><Ad id=\"1\"><InLine/></Ad></VAST>"))
    }
}

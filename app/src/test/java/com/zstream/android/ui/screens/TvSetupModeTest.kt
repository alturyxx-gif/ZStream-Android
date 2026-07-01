package com.zstream.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class TvSetupModeTest {
    @Test
    fun failuresAdvanceThroughWirelessThenLegacyFallbacks() {
        assertEquals(TvSetupMode.MANUAL_WIRELESS, TvSetupMode.AUTOMATIC.fallback())
        assertEquals(TvSetupMode.LEGACY, TvSetupMode.MANUAL_WIRELESS.fallback())
    }
}

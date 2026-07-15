package com.zstream.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSyncReleaseTransportModelTest {
    @Test
    fun legacyPhoneRequiresRepairButCallbackPhoneDoesNot() {
        assertTrue(
            PairedPhone("legacy", null, "Old phone", null, null, 0).needsRepair
        )
        assertFalse(
            PairedPhone("phone-id", "phone-id", "Pixel", "192.168.1.5", 43210, 0).needsRepair
        )
    }
}

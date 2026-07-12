package com.zstream.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorPresentationTest {
    @Test
    fun classifiesCapabilityAndTransportFailures() {
        assertEquals(
            "Unsupported video",
            playbackErrorPresentation(
                "ERROR_CODE_DECODING_FAILED",
                "format_supported=NO_EXCEEDS_CAPABILITIES",
            ).title,
        )
        assertEquals(
            "Stream unavailable",
            playbackErrorPresentation("ERROR_CODE_IO_BAD_HTTP_STATUS", "Source error", httpStatus = 403).title,
        )
        assertEquals(
            "Invalid stream",
            playbackErrorPresentation("ERROR_CODE_PARSING_MANIFEST_MALFORMED", "Malformed manifest").title,
        )
    }
}

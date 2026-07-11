package com.zstream.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class GraniteVttTest {
    @Test
    fun separatesGraniteCuesWithoutBlankLines() {
        val cues = vttCueBlocks(
            """00:00:00.000 --> 00:00:01.500
First line
00:00:01.500 --> 00:00:03.000
Second line"""
        )

        assertEquals(2, cues.size)
    }
}

package com.zstream.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferredInitialVariantTest {
    @Test
    fun prefersSecondVariantButKeepsManualAlternatives() {
        val sdr = StreamVariant("sdr", "", "4K", "hevc", "", "sdr-url")
        val hdr = StreamVariant("hdr", "", "4K", "hevc", "hdr", "hdr-url")
        val variants = listOf(sdr, hdr)

        assertEquals("hdr-url", preferredInitialVariantUrl("sdr-url", variants))
        assertEquals("hdr-url", preferredInitialVariantUrl("provider-default-url", variants))
        assertEquals(listOf("sdr-url", "hdr-url"), variants.map { it.streamUrl })
        assertEquals("hdr-url", preferredInitialVariantUrl("hdr-url", variants))
        assertEquals("sdr-url", preferredInitialVariantUrl("sdr-url", listOf(sdr)))
    }
}

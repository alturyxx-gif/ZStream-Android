package com.zstream.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferredInitialVariantTest {
    @Test
    fun prefersSecondVariantForArtemisOnly() {
        val sdr = StreamVariant("sdr", "", "4K", "hevc", "", "sdr-url")
        val hdr = StreamVariant("hdr", "", "4K", "hevc", "hdr", "hdr-url")
        val variants = listOf(sdr, hdr)

        // Artemis: prefer the second variant regardless of the provider default
        assertEquals("hdr-url", preferredInitialVariantUrl("artemis", "sdr-url", variants))
        assertEquals("hdr-url", preferredInitialVariantUrl("artemis", "provider-default-url", variants))
        assertEquals("hdr-url", preferredInitialVariantUrl("artemis", "hdr-url", variants))
        // Artemis with a single variant falls back to the default
        assertEquals("sdr-url", preferredInitialVariantUrl("artemis", "sdr-url", listOf(sdr)))

        // Any other source keeps the provider default (first) stream
        assertEquals("sdr-url", preferredInitialVariantUrl("stellar", "sdr-url", variants))
        assertEquals("provider-default-url", preferredInitialVariantUrl("nesterov", "provider-default-url", variants))

        assertEquals(listOf("sdr-url", "hdr-url"), variants.map { it.streamUrl })
        assertEquals("sdr-url", nextUnfailedVariantUrl("hdr-url", variants, setOf("hdr-url")))
        assertEquals(null, nextUnfailedVariantUrl("hdr-url", variants, setOf("hdr-url", "sdr-url")))
    }
}

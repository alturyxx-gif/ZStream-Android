package com.zstream.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleFlagsTest {

    @Test
    fun mapsLanguagesToRealFlagAssets() {
        assertEquals("file:///android_asset/flags/4x3/us.svg", languageToFlag("en"))
        assertEquals("file:///android_asset/flags/4x3/br.svg", languageToFlag("pt-BR"))
        assertEquals("file:///android_asset/flags/4x3/tw.svg", languageToFlag("zh-Hant"))
        assertEquals("file:///android_asset/flags/4x3/hr.svg", languageToFlag("hr"))
        assertEquals("file:///android_asset/flags/4x3/ir.svg", languageToFlag("fa"))
        assertEquals("file:///android_asset/flags/4x3/rs.svg", languageToFlag("sr"))
        assertEquals("file:///android_asset/flags/galicia.svg", languageToFlag("gl-ES"))
        assertEquals("file:///android_asset/flags/skull.svg", languageToFlag("pirate"))
    }

    @Test
    fun normalizesPortugueseAliases() {
        assertEquals("pt-BR", normalizeSubtitleLanguageCode("pb"))
        assertEquals("pt-BR", normalizeSubtitleLanguageCode("po"))
        assertEquals("pt-BR", normalizeSubtitleLanguageCode("br"))
    }
}

package com.zstream.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApplicationLanguageTest {
    @Test
    fun normalizesSupportedLanguageTagsWithoutCrossingRegionalVariants() {
        assertEquals("en", supportedApplicationLanguageTag("en-US"))
        assertEquals("es", supportedApplicationLanguageTag("es-MX"))
        assertEquals("pt-BR", supportedApplicationLanguageTag("pt-BR"))
        assertEquals("ur-PK", supportedApplicationLanguageTag("ur-PK"))
        assertEquals("zh-CN", supportedApplicationLanguageTag("zh-Hans"))
        assertEquals("zh-CN", supportedApplicationLanguageTag("zh-Hans-CN"))
        assertNull(supportedApplicationLanguageTag(""))
        assertNull(supportedApplicationLanguageTag("pt-PT"))
        assertNull(supportedApplicationLanguageTag("pt"))
        assertNull(supportedApplicationLanguageTag("zh-TW"))
        assertNull(supportedApplicationLanguageTag("zh-Hant"))
        assertNull(supportedApplicationLanguageTag("zh-Hant-CN"))
        assertNull(supportedApplicationLanguageTag("xx"))
    }
}

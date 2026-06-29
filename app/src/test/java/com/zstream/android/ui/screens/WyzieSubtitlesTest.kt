package com.zstream.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class WyzieSubtitlesTest {
    @Test
    fun parsesValidEntriesAndSkipsMissingUrls() {
        val tracks = parseWyzieSubtitles(
            """[{"url":"https://example.com/en.srt","language":"en","display":"English","format":"srt"},{"language":"es"}]"""
        )

        assertEquals(
            listOf(
                SubtitleTrack(
                    label = "English",
                    url = "https://example.com/en.srt",
                    language = "en",
                    type = "srt",
                    source = "wyzie",
                    external = true,
                )
            ),
            tracks,
        )
    }

    @Test
    fun validationAcceptsQuotaLimitedKeysButRejectsUnauthorizedKeys() {
        assertEquals(true, isAcceptedWyzieStatus(200))
        assertEquals(true, isAcceptedWyzieStatus(402))
        assertEquals(true, isAcceptedWyzieStatus(429))
        assertEquals(false, isAcceptedWyzieStatus(401))
        assertEquals(null, isAcceptedWyzieStatus(500))
    }

    @Test
    fun keepsMultipleWyzieReleasesForOneLanguage() {
        val tracks = parseWyzieSubtitles(
            """[{"id":"one","url":"https://example.com/one.srt","language":"en"},{"id":"two","url":"https://example.com/two.srt","language":"en"}]"""
        )

        assertEquals(listOf("one", "two"), tracks.map { it.id })
    }

    @Test
    fun automaticSelectionNeverFallsBackToAnotherLanguage() {
        val english = SubtitleTrack("English", "https://example.com/en.srt", "en")
        val spanish = SubtitleTrack("Spanish", "https://example.com/es.srt", "es")

        assertEquals(listOf(english), subtitleCandidates(listOf(spanish, english), "en"))
        assertEquals(emptyList<SubtitleTrack>(), subtitleCandidates(listOf(spanish), "en"))
    }
}

package com.zstream.android.data

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotifyCoreTest {
    @Test
    fun `same-day release waits until local noon`() {
        assertFalse(hasReachedReleaseDate("2026-07-14", LocalDateTime.of(2026, 7, 14, 11, 59)))
        assertTrue(hasReachedReleaseDate("2026-07-14", LocalDateTime.of(2026, 7, 14, 12, 0)))
        assertTrue(hasReachedReleaseDate("2026-07-13", LocalDateTime.of(2026, 7, 14, 1, 0)))
    }

    @Test
    fun `unknown or malformed release date stays pending`() {
        assertFalse(hasReachedReleaseDate(null, LocalDateTime.of(2026, 7, 14, 18, 0)))
        assertFalse(hasReachedReleaseDate("soon", LocalDateTime.of(2026, 7, 14, 18, 0)))
    }

    @Test
    fun `subscription request validates identity and computes key`() {
        assertEquals(
            "movie:42",
            ReleaseSubscriptionRequest(42, "movie", "Movie").key,
        )
        assertEquals(
            "tv:42:0:3",
            ReleaseSubscriptionRequest(
                tmdbId = 42,
                mediaType = "tv",
                title = "Show",
                seasonNumber = 0,
                episodeNumber = 3,
            ).key,
        )
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseSubscriptionRequest(42, "tv", "Show", seasonNumber = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseSubscriptionRequest(0, "movie", "Movie")
        }
    }
}

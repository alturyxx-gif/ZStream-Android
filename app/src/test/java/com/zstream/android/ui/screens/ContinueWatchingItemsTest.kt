package com.zstream.android.ui.screens

import com.zstream.android.data.local.entity.ProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContinueWatchingItemsTest {
    @Test
    fun continueWatchingSkipsCompletedRowsAndUsesLatestUpdate() {
        val result = listOf(
            ProgressEntity(
                id = "1_S1E10",
                tmdbId = "1",
                title = "Show",
                type = "show",
                watched = 10,
                duration = 100,
                updatedAt = 1_000,
                seasonNumber = 1,
                episodeNumber = 10,
            ),
            ProgressEntity(
                id = "1_S1E2",
                tmdbId = "1",
                title = "Show",
                type = "show",
                watched = 30,
                duration = 100,
                updatedAt = 2_000,
                seasonNumber = 1,
                episodeNumber = 2,
            ),
            ProgressEntity(
                id = "2",
                tmdbId = "2",
                title = "Movie",
                type = "movie",
                watched = 0,
                duration = 1,
                updatedAt = 3_000,
            ),
            ProgressEntity(
                id = "3",
                tmdbId = "3",
                title = "Movie",
                type = "movie",
                watched = 20,
                duration = 100,
                updatedAt = 1_500,
            ),
        ).toContinueWatchingResult()

        assertEquals(listOf(1, 3), result.media.map { it.id })
        assertEquals(2, result.progressMap["1"]?.episodeNumber)
        assertFalse(result.progressMap.containsKey("2"))
    }

    @Test
    fun placeholderHistoryRowsDoNotCountAsContinueWatching() {
        val result = listOf(
            ProgressEntity(
                id = "9",
                tmdbId = "9",
                title = "Watched Movie",
                type = "movie",
                watched = 0,
                duration = 1,
                updatedAt = 10_000,
            )
        ).toContinueWatchingResult()

        assertEquals(emptyList<Int>(), result.media.map { it.id })
        assertFalse(result.progressMap.containsKey("9"))
    }
}

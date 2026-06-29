package com.zstream.android.data

import org.junit.Assert.assertEquals
import org.junit.Test
import com.zstream.android.data.local.entity.ProgressEntity

class TraktRepositoryTest {
    @Test
    fun devicePollingHandlesTraktStatuses() {
        assertEquals(DevicePollAction.COMPLETE, devicePollAction(200))
        assertEquals(DevicePollAction.WAIT, devicePollAction(400))
        assertEquals(DevicePollAction.SLOW_DOWN, devicePollAction(429))
        assertEquals(DevicePollAction.FAIL, devicePollAction(410))
    }

    @Test
    fun showHistoryUsesShowAndEpisodeCoordinates() {
        val payload = buildTraktHistoryPayload(ProgressEntity(
            id = "123_S1E2",
            tmdbId = "123",
            title = "Show",
            type = "show",
            watched = 900,
            duration = 1800,
            episodeId = "999999",
            seasonNumber = 1,
            episodeNumber = 2,
            updatedAt = 0,
        ))!!

        val show = payload.getAsJsonArray("shows")[0].asJsonObject
        assertEquals(123, show.getAsJsonObject("ids").get("tmdb").asInt)
        val episode = show.getAsJsonArray("seasons")[0].asJsonObject
            .getAsJsonArray("episodes")[0].asJsonObject
        assertEquals(2, episode.get("number").asInt)
        assertEquals(null, payload.get("episodes"))
    }

    @Test
    fun incompleteMovieIsImportedAsPlaybackProgress() {
        val movie = ProgressEntity("123", "123", "Movie", type = "movie", watched = 50, duration = 100)
        assertEquals(50.0, traktPlaybackPercent(movie)!!, 0.0)
        assertEquals(null, traktPlaybackPercent(movie.copy(watched = 100)))
    }
}

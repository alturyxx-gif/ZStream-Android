package com.zstream.android.ui.screens

import com.zstream.android.data.model.Media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaIdentityTest {
    @Test
    fun `movie and tv with same TMDB id have different UI keys`() {
        val movie = media(id = 256215, type = "movie")
        val tv = media(id = 256215, type = "tv")

        assertNotEquals(movie.stableUiKey(), tv.stableUiKey())
    }

    @Test
    fun `duplicate pages keep one item per media identity`() {
        val movie = media(id = 256215, type = "movie")
        val duplicateMovie = media(id = 256215, type = "movie")
        val tv = media(id = 256215, type = "tv")

        assertEquals(listOf(movie, tv), listOf(movie, duplicateMovie, tv).distinctByMediaIdentity())
    }

    private fun media(id: Int, type: String) = Media(
        id = id,
        title = if (type == "movie") "Movie" else null,
        name = if (type == "tv") "TV" else null,
        overview = null,
        posterPath = null,
        backdropPath = null,
        releaseDate = null,
        firstAirDate = null,
        voteAverage = null,
        mediaType = type,
        genreIds = null,
    )
}

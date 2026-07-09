package com.zstream.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.zstream.android.data.model.Media

class LocalMediaGrouperTest {
    @Test
    fun movieFolderWithOneVideoBecomesMovieItem() {
        val guess = LocalMediaGrouper.infer("Movies/Blade Runner 2049/Blade Runner 2049.mkv")

        assertEquals("movie", guess.mediaKind)
        assertEquals("Blade Runner 2049", guess.groupTitle)
        assertNull(guess.season)
        assertNull(guess.episode)
    }

    @Test
    fun seasonFolderEpisodeGroupsUnderShowAndSeason() {
        val guess = LocalMediaGrouper.infer("Shows/Severance/Season 01/S01E02.mkv")

        assertEquals("show", guess.mediaKind)
        assertEquals("Severance", guess.groupTitle)
        assertEquals(1, guess.season)
        assertEquals(2, guess.episode)
    }

    @Test
    fun flatSeasonEpisodeFilenameGroupsCorrectly() {
        val guess = LocalMediaGrouper.infer("Downloads/Show.Name.S01E02.mkv")

        assertEquals("show", guess.mediaKind)
        assertEquals("Show Name", guess.groupTitle)
        assertEquals(1, guess.season)
        assertEquals(2, guess.episode)
    }

    @Test
    fun compactSeasonFolderGroupsUnderShowFolder() {
        val guess = LocalMediaGrouper.infer("Tanya/s1/e1.mkv")

        assertEquals("show", guess.mediaKind)
        assertEquals("Tanya", guess.groupTitle)
        assertEquals("show:tanya", guess.groupKey)
        assertEquals(1, guess.season)
        assertEquals(1, guess.episode)
    }

    @Test
    fun differentShowFoldersDoNotCollapseTogether() {
        val first = LocalMediaGrouper.infer("Show A/s1/e1.mkv")
        val second = LocalMediaGrouper.infer("Show B/s1/e1.mkv")

        assertEquals("show:show a", first.groupKey)
        assertEquals("show:show b", second.groupKey)
    }

    @Test
    fun metadataTitleWinsBeforeFilenameFallbacks() {
        val guess = LocalMediaGrouper.infer("Loose/video01.mp4", "Metadata.Show.S01E02")

        assertEquals("show", guess.mediaKind)
        assertEquals("Metadata Show", guess.groupTitle)
        assertEquals("metadata", guess.matchSource)
        assertEquals(1, guess.season)
        assertEquals(2, guess.episode)
    }

    @Test
    fun unknownFilesFallBackToUncategorized() {
        val guess = LocalMediaGrouper.infer("Camera/clip001.mp4")

        assertEquals("unknown", guess.mediaKind)
        assertEquals("Uncategorized", guess.groupTitle)
        assertEquals("uncategorized", guess.groupKey)
    }

    @Test
    fun tmdbMatcherPrefersSameTypeExactTitle() {
        val match = LocalTmdbMatcher.best(
            title = "Tanya",
            expectedType = "tv",
            results = listOf(
                media(1, title = "Tanya", type = "movie", poster = "/movie.jpg"),
                media(2, name = "Tanya", type = "tv", poster = null),
            ),
        )

        assertEquals(2, match?.id)
    }

    private fun media(id: Int, title: String? = null, name: String? = null, type: String, poster: String?) = Media(
        id = id,
        title = title,
        name = name,
        overview = null,
        posterPath = poster,
        backdropPath = null,
        releaseDate = null,
        firstAirDate = null,
        voteAverage = null,
        mediaType = type,
        genreIds = null,
    )
}

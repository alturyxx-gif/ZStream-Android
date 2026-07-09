package com.zstream.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun unknownFilesFallBackToParentFolder() {
        val guess = LocalMediaGrouper.infer("Camera/clip001.mp4")

        assertEquals("movie", guess.mediaKind)
        assertEquals("Camera", guess.groupTitle)
    }
}

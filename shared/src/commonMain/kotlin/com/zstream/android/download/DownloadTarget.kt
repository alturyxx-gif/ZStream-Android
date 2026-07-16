package com.zstream.android.download

/**
 * Describes what a download is "of", purely for folder/file naming. Not tied to any plugin or
 * TMDB type — the download layer only cares about how to name things on disk.
 */
sealed class DownloadTarget {
    data class Movie(
        val title: String,
        val year: Int? = null,
    ) : DownloadTarget()

    data class Episode(
        val showTitle: String,
        val season: Int,
        val episode: Int,
        val episodeTitle: String? = null,
    ) : DownloadTarget()
}

/** Strips characters illegal (or awkward) in Android/Windows/exFAT file and folder names. */
fun sanitizeFileName(name: String): String =
    name.replace(Regex("""[/\\:*?"<>|]"""), "")
        .trim()
        .trim('.')
        .ifBlank { "untitled" }

/** Zero-pads to 2 digits. String.format isn't available on Kotlin/Native, so this replaces it. */
private fun Int.pad2(): String = toString().padStart(2, '0')

/**
 * Folder path segments under the "ZStream" root, e.g.:
 *   Movie:   ["Interstellar (2014)"]
 *   Episode: ["Breaking Bad", "Season 01", "S01E02 - Cat's in the Bag..."]
 */
fun DownloadTarget.folderSegments(): List<String> = when (this) {
    is DownloadTarget.Movie -> {
        val label = if (year != null) "$title ($year)" else title
        listOf(sanitizeFileName(label))
    }
    is DownloadTarget.Episode -> {
        val seasonLabel = "Season ${season.pad2()}"
        val episodeLabel = buildString {
            append("S${season.pad2()}E${episode.pad2()}")
            if (!episodeTitle.isNullOrBlank()) append(" - $episodeTitle")
        }
        listOf(sanitizeFileName(showTitle), sanitizeFileName(seasonLabel), sanitizeFileName(episodeLabel))
    }
}

/** Base filename (no extension) for the main video file within its own folder. */
fun DownloadTarget.baseFileName(): String = when (this) {
    is DownloadTarget.Movie -> sanitizeFileName(if (year != null) "$title ($year)" else title)
    is DownloadTarget.Episode -> sanitizeFileName(
        buildString {
            append("S${season.pad2()}E${episode.pad2()}")
            if (!episodeTitle.isNullOrBlank()) append(" - $episodeTitle")
        }
    )
}

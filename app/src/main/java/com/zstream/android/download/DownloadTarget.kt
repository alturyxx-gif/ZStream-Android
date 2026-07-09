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
        val seasonLabel = "Season %02d".format(season)
        val episodeLabel = buildString {
            append("S%02dE%02d".format(season, episode))
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
            append("S%02dE%02d".format(season, episode))
            if (!episodeTitle.isNullOrBlank()) append(" - $episodeTitle")
        }
    )
}

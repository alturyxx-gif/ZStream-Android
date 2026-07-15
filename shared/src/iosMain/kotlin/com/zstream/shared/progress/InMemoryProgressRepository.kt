package com.zstream.shared.progress

/**
 * Placeholder ProgressRepository so iOS UI can be built against the real contract before a real
 * backend-synced implementation exists (see app/data/ProgressRepository.kt for the Android
 * version this will eventually be ported/adapted from). Holds state only for the life of the
 * process -- nothing persists to disk yet.
 */
class InMemoryProgressRepository : ProgressRepository {
    private val entries = mutableMapOf<String, ProgressEntry>()

    override suspend fun getProgressById(id: String): ProgressEntry? = entries[id]

    override suspend fun getAllProgress(): List<ProgressEntry> = entries.values.toList()

    override suspend fun getAllProgressForTmdb(tmdbId: String): List<ProgressEntry> =
        entries.values.filter { it.tmdbId == tmdbId }

    override suspend fun getMovieProgress(): List<ProgressEntry> =
        entries.values.filter { it.type == "movie" }

    override suspend fun getShowProgress(): List<ProgressEntry> =
        entries.values.filter { it.type == "show" }

    override suspend fun updateProgress(
        tmdbId: String,
        title: String,
        type: String,
        watched: Int,
        duration: Int,
        year: Int?,
        posterPath: String?,
        episodeId: String?,
        seasonId: String?,
        episodeNumber: Int?,
        seasonNumber: Int?,
    ) {
        val id = ProgressEntry.computeId(tmdbId, seasonNumber, episodeNumber)
        entries[id] = ProgressEntry(
            id = id,
            tmdbId = tmdbId,
            title = title,
            type = type,
            watched = watched,
            duration = duration,
            year = year,
            posterPath = posterPath,
            episodeId = episodeId,
            seasonId = seasonId,
            episodeNumber = episodeNumber,
            seasonNumber = seasonNumber,
        )
    }

    override suspend fun removeProgress(tmdbId: String) {
        entries.keys.filter { entries[it]?.tmdbId == tmdbId }.forEach { entries.remove(it) }
    }

    override suspend fun removeProgressItem(tmdbId: String, seasonNumber: Int?, episodeNumber: Int?) {
        entries.remove(ProgressEntry.computeId(tmdbId, seasonNumber, episodeNumber))
    }

    override suspend fun clearProgress() {
        entries.clear()
    }
}

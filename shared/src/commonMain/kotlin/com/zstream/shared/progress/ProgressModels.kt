package com.zstream.shared.progress

/**
 * Platform-agnostic mirror of Android's `ProgressEntity` (app/data/local/entity/ProgressEntity.kt),
 * stripped of the Room `@Entity` annotation so it can live in commonMain. Field-for-field
 * compatible so a future androidMain adapter can convert directly between the two without loss.
 */
data class ProgressEntry(
    val id: String,
    val tmdbId: String,
    val title: String,
    val type: String, // "movie" or "show"
    val watched: Int, // seconds watched
    val duration: Int, // seconds total
    val year: Int? = null,
    val posterPath: String? = null,
    val episodeId: String? = null,
    val seasonId: String? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val updatedAt: Long = 0L,
) {
    companion object {
        fun computeId(tmdbId: String, seasonNumber: Int?, episodeNumber: Int?): String {
            return if (seasonNumber != null && episodeNumber != null) {
                "${tmdbId}_S${seasonNumber}E$episodeNumber"
            } else {
                tmdbId
            }
        }
    }
}

/**
 * Contract for reading/writing watch progress, shared by Android (backed by Room + the real
 * backend sync in app/data/ProgressRepository.kt, not yet adapted to this interface) and iOS
 * (currently InMemoryProgressRepository -- see iosMain).
 *
 * No Flow here on purpose: bridging kotlinx.coroutines Flow to Swift needs an extra library
 * (KMP-NativeCoroutines or SKIE) that isn't wired into this project yet. Callers poll/refresh
 * instead of observing until that's decided.
 */
interface ProgressRepository {
    suspend fun getProgressById(id: String): ProgressEntry?
    suspend fun getAllProgress(): List<ProgressEntry>
    suspend fun getAllProgressForTmdb(tmdbId: String): List<ProgressEntry>
    suspend fun getMovieProgress(): List<ProgressEntry>
    suspend fun getShowProgress(): List<ProgressEntry>

    suspend fun updateProgress(
        tmdbId: String,
        title: String,
        type: String,
        watched: Int,
        duration: Int,
        year: Int? = null,
        posterPath: String? = null,
        episodeId: String? = null,
        seasonId: String? = null,
        episodeNumber: Int? = null,
        seasonNumber: Int? = null,
    )

    suspend fun removeProgress(tmdbId: String)
    suspend fun removeProgressItem(
        tmdbId: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    )
    suspend fun clearProgress()
}

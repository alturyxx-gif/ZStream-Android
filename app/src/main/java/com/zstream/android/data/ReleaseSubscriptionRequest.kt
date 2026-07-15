package com.zstream.android.data

import com.zstream.android.data.local.entity.trackedEpisodeKey
import com.zstream.android.data.local.entity.trackedMovieKey

data class ReleaseSubscriptionRequest(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
) {
    init {
        require(tmdbId > 0) { "A release subscription needs a valid TMDB id." }
        require(mediaType == "movie" || mediaType == "tv") {
            "Unsupported release notification type: $mediaType"
        }
        require(title.isNotBlank() && title.length <= MAX_TITLE_LENGTH) {
            "A release subscription needs a title of at most $MAX_TITLE_LENGTH characters."
        }
        require(posterPath == null || posterPath.length <= MAX_POSTER_PATH_LENGTH) {
            "The release subscription poster path is too long."
        }
        require(episodeTitle == null || episodeTitle.length <= MAX_TITLE_LENGTH) {
            "The release subscription episode title is too long."
        }
        if (mediaType == "tv") {
            require(seasonNumber != null && seasonNumber >= 0) {
                "A TV release subscription needs a valid season number."
            }
            require(episodeNumber != null && episodeNumber > 0) {
                "A TV release subscription needs a valid episode number."
            }
        }
    }

    val key: String
        get() = when (mediaType) {
            "movie" -> trackedMovieKey(tmdbId)
            "tv" -> trackedEpisodeKey(
                tmdbId,
                requireNotNull(seasonNumber) { "A TV release subscription needs a season number." },
                requireNotNull(episodeNumber) { "A TV release subscription needs an episode number." },
            )
            else -> error("Unsupported release notification type: $mediaType")
        }

    private companion object {
        const val MAX_TITLE_LENGTH = 200
        const val MAX_POSTER_PATH_LENGTH = 1_024
    }
}

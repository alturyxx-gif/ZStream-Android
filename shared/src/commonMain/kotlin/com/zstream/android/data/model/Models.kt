package com.zstream.android.data.model

import com.zstream.shared.Urls
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Media(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int>? = null,
    val logoPath: String? = null,
) {
    val displayTitle get() = title ?: name ?: ""
    val displayDate get() = releaseDate ?: firstAirDate ?: ""
    val type get() = mediaType ?: if (title != null) "movie" else "tv"

    fun posterUrl(size: String = "w500"): String? {
        val path = posterPath ?: return null
        return if (path.startsWith("http")) path else Urls.TMDB_IMAGE + "$size$path"
    }

    fun backdropUrl(size: String = "w1280"): String? {
        val path = backdropPath ?: return null
        return if (path.startsWith("http")) path else Urls.TMDB_IMAGE + "$size$path"
    }

    fun logoUrl(size: String = "w500"): String? {
        val path = logoPath ?: return null
        return if (path.startsWith("http")) path else Urls.TMDB_IMAGE + "$size$path"
    }
}

@Serializable
data class PagedResponse<T>(val results: List<T>, val page: Int, @SerialName("total_pages") val totalPages: Int)

@Serializable
data class MovieDetail(
    val id: Int,
    val title: String,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val runtime: Int? = null,
    val genres: List<Genre>? = null,
    val credits: Credits? = null,
    val images: ImageData? = null,
    val videos: VideoData? = null,
    val similar: SimilarMoviesResponse? = null,
    @SerialName("belongs_to_collection") val belongsToCollection: CollectionSummary? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
) {
    fun posterUrl(size: String = "w500"): String? {
        val path = posterPath ?: return null
        return if (path.startsWith("http")) path else Urls.TMDB_IMAGE + "$size$path"
    }

    fun backdropUrl(size: String = "w1280"): String? {
        val path = backdropPath ?: return null
        return if (path.startsWith("http")) path else Urls.TMDB_IMAGE + "$size$path"
    }

    fun logoUrl(size: String = "w500"): String? {
        val path = images?.logos?.firstOrNull()?.filePath ?: return null
        return if (path.startsWith("http")) path else Urls.TMDB_IMAGE + "$size$path"
    }
}

@Serializable
data class CollectionSummary(
    val id: Int,
    val name: String,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
)

@Serializable
data class TvDetail(
    val id: Int,
    val name: String,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    val seasons: List<Season>? = null,
    val genres: List<Genre>? = null,
    val credits: Credits? = null,
    val images: ImageData? = null,
    val videos: VideoData? = null,
    val similar: SimilarShowsResponse? = null,
    @SerialName("external_ids") val externalIds: ExternalIds? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
) {
    fun posterUrl(size: String = "w500"): String? {
        val path = posterPath ?: return null
        return if (path.startsWith("http")) path else Urls.TMDB_IMAGE + "$size$path"
    }

    fun backdropUrl(size: String = "w1280"): String? {
        val path = backdropPath ?: return null
        return if (path.startsWith("http")) path else Urls.TMDB_IMAGE + "$size$path"
    }

    fun logoUrl(size: String = "w500"): String? {
        val path = images?.logos?.firstOrNull()?.filePath ?: return null
        return if (path.startsWith("http")) path else Urls.TMDB_IMAGE + "$size$path"
    }
}

@Serializable
data class Season(
    val id: Int,
    @SerialName("season_number") val seasonNumber: Int,
    val name: String? = null,
    @SerialName("episode_count") val episodeCount: Int? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val episodes: List<Episode>? = null,
)

@Serializable
data class Episode(
    val id: Int,
    val name: String? = null,
    @SerialName("episode_number") val episodeNumber: Int,
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("still_path") val stillPath: String? = null,
    val overview: String? = null,
    @SerialName("air_date") val airDate: String? = null,
)

/**
 * TMDB dates are always "yyyy-MM-dd" -- lexicographic string comparison sorts them correctly
 * without needing a date-parsing library, which keeps this file free of JVM-only
 * java.text/java.util date APIs that don't exist on Kotlin/Native.
 */
expect fun todayIsoDate(): String

fun Episode.hasAired(todayIsoDate: String = todayIsoDate()): Boolean {
    val date = airDate?.takeIf { it.isNotBlank() } ?: return true
    return date <= todayIsoDate
}

fun List<Episode>.airedEpisodes(todayIsoDate: String = todayIsoDate()): List<Episode> =
    filter { it.hasAired(todayIsoDate) }

fun MovieDetail.hasReleased(todayIsoDate: String = todayIsoDate()): Boolean {
    val date = releaseDate?.takeIf { it.isNotBlank() } ?: return false
    return date <= todayIsoDate
}

private val monthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

fun Episode.formattedAirDate(): String? =
    airDate?.takeIf { it.isNotBlank() }?.let { raw ->
        val parts = raw.split("-")
        if (parts.size != 3) return@let raw
        val year = parts[0]
        val month = parts[1].toIntOrNull()
        val day = parts[2].toIntOrNull()
        if (month == null || day == null || month !in 1..12) return@let raw
        "${monthNames[month - 1]} $day, $year"
    }

@Serializable
data class Credits(val cast: List<CastMember>? = null)

@Serializable
data class CastMember(
    val id: Int,
    val name: String? = null,
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("external_ids") val externalIds: ExternalIds? = null,
)

@Serializable
data class ExternalIds(
    @SerialName("imdb_id") val imdbId: String? = null,
)

@Serializable
data class Genre(val id: Int, val name: String)

// TMDB append_to_response data models
@Serializable
data class ImageData(
    val logos: List<LogoData>? = null,
)

@Serializable
data class LogoData(
    @SerialName("file_path") val filePath: String,
)

@Serializable
data class VideoData(
    val results: List<TrailerData>? = null,
)

@Serializable
data class TrailerData(
    val id: String,
    val name: String,
    val key: String,
    val site: String, // "YouTube" or others
    val type: String, // "Trailer", "Teaser", etc.
)

@Serializable
data class SimilarMoviesResponse(
    val results: List<Media>? = null,
)

@Serializable
data class SimilarShowsResponse(
    val results: List<Media>? = null,
)

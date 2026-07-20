package com.zstream.android.data.model

import com.google.gson.annotations.SerializedName
import com.zstream.android.Urls
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Media(
    val id: Int,
    val title: String?,
    val name: String?,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("genre_ids") val genreIds: List<Int>?,
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
        val url = if (path.startsWith("http")) path else Urls.TMDB_IMAGE + "$size$path"
        return url
    }

    fun logoUrl(size: String = "w500"): String? {
        val path = logoPath ?: return null
        return if (path.startsWith("http")) path else Urls.TMDB_IMAGE + "$size$path"
    }
}

data class PagedResponse<T>(val results: List<T>, val page: Int, @SerializedName("total_pages") val totalPages: Int)

data class MovieDetail(
    val id: Int,
    val title: String,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("runtime") val runtime: Int?,
    val genres: List<Genre>?,
    val credits: Credits?,
    val images: ImageData? = null,
    val videos: VideoData? = null,
    val similar: SimilarMoviesResponse? = null,
    @SerializedName("belongs_to_collection") val belongsToCollection: CollectionSummary? = null,
    @SerializedName("imdb_id") val imdbId: String? = null,
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
        val path = images?.logos?.firstOrNull()?.file_path ?: return null
        return if (path.startsWith("http")) path else Urls.TMDB_IMAGE + "$size$path"
    }
}

data class CollectionSummary(
    val id: Int,
    val name: String,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
)

data class TvDetail(
    val id: Int,
    val name: String,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int?,
    val seasons: List<Season>?,
    val genres: List<Genre>?,
    val credits: Credits?,
    val images: ImageData? = null,
    val videos: VideoData? = null,
    val similar: SimilarShowsResponse? = null,
    @SerializedName("external_ids") val externalIds: ExternalIds? = null,
    @SerializedName("imdb_id") val imdbId: String? = null,
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
        val path = images?.logos?.firstOrNull()?.file_path ?: return null
        return if (path.startsWith("http")) path else Urls.TMDB_IMAGE + "$size$path"
    }
}

data class Season(
    val id: Int,
    @SerializedName("season_number") val seasonNumber: Int,
    val name: String?,
    @SerializedName("episode_count") val episodeCount: Int?,
    @SerializedName("poster_path") val posterPath: String?,
    val episodes: List<Episode>?,
)

data class Episode(
    val id: Int,
    val name: String?,
    @SerializedName("episode_number") val episodeNumber: Int,
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("still_path") val stillPath: String?,
    val overview: String?,
    @SerializedName("air_date") val airDate: String?,
    /** The "real" season number according to TMDB - ignoring episode group seasons*/
    val sourceSeasonNumber: Int? = null,
    /** See [sourceSeasonNumber]. */
    val sourceEpisodeNumber: Int? = null,
)

private fun todayIsoDate(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

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

private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

fun Episode.formattedAirDate(): String? =
    airDate?.takeIf { it.isNotBlank() }?.let { raw ->
        runCatching {
            isoDateFormat.parse(raw)?.let {
                DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(it)
            }
        }.getOrNull() ?: raw
    }

data class Credits(val cast: List<CastMember>?)

data class CastMember(
    val id: Int,
    val name: String?,
    val character: String?,
    @SerializedName("profile_path") val profilePath: String?,
    @SerializedName("external_ids") val externalIds: ExternalIds? = null,
)

data class ExternalIds(
    @SerializedName("imdb_id") val imdbId: String? = null,
)

data class Genre(val id: Int, val name: String)

// TMDB append_to_response data models
data class ImageData(
    val logos: List<LogoData>? = null,
)

data class LogoData(
    @SerializedName("file_path") val file_path: String,
)

data class VideoData(
    val results: List<TrailerData>? = null,
)

data class TrailerData(
    val id: String,
    val name: String,
    val key: String,
    val site: String, // "YouTube" or others
    val type: String, // "Trailer", "Teaser", etc.
)

data class SimilarMoviesResponse(
    val results: List<Media>? = null,
)

data class SimilarShowsResponse(
    val results: List<Media>? = null,
)

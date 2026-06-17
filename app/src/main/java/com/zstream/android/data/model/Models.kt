package com.zstream.android.data.model

import com.google.gson.annotations.SerializedName
import com.zstream.android.Urls

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
) {
    val displayTitle get() = title ?: name ?: ""
    val displayDate get() = releaseDate ?: firstAirDate ?: ""
    val type get() = mediaType ?: if (title != null) "movie" else "tv"
    fun posterUrl(size: String = "w500") = posterPath?.let { Urls.TMDB_IMAGE + "$size$it" }
    fun backdropUrl(size: String = "w1280") = backdropPath?.let { Urls.TMDB_IMAGE + "$size$it" }
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
) {
    fun posterUrl(size: String = "w500") = posterPath?.let { Urls.TMDB_IMAGE + "$size$it" }
    fun backdropUrl(size: String = "w1280") = backdropPath?.let { Urls.TMDB_IMAGE + "$size$it" }
}

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
) {
    fun posterUrl(size: String = "w500") = posterPath?.let { Urls.TMDB_IMAGE + "$size$it" }
    fun backdropUrl(size: String = "w1280") = backdropPath?.let { Urls.TMDB_IMAGE + "$size$it" }
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
)

data class Credits(val cast: List<CastMember>?)

data class CastMember(
    val id: Int,
    val name: String?,
    val character: String?,
    @SerializedName("profile_path") val profilePath: String?,
)

data class Genre(val id: Int, val name: String)

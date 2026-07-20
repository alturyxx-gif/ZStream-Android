package com.zstream.android.data.remote

import com.zstream.android.data.model.*
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/movie/week") suspend fun trendingMovies(@Query("page") page: Int = 1, @Query("language") language: String? = null): PagedResponse<Media>
    @GET("trending/tv/week") suspend fun trendingTv(@Query("page") page: Int = 1, @Query("language") language: String? = null): PagedResponse<Media>
    @GET("movie/popular") suspend fun popularMovies(@Query("page") page: Int = 1, @Query("language") language: String? = null): PagedResponse<Media>
    @GET("movie/now_playing") suspend fun nowPlayingMovies(@Query("page") page: Int = 1, @Query("language") language: String? = null): PagedResponse<Media>
    @GET("movie/top_rated") suspend fun topRatedMovies(@Query("page") page: Int = 1, @Query("language") language: String? = null): PagedResponse<Media>
    @GET("tv/popular") suspend fun popularTv(@Query("page") page: Int = 1, @Query("language") language: String? = null): PagedResponse<Media>
    @GET("tv/top_rated") suspend fun topRatedTv(@Query("page") page: Int = 1, @Query("language") language: String? = null): PagedResponse<Media>
    @GET("tv/on_the_air") suspend fun onAirTv(@Query("page") page: Int = 1, @Query("language") language: String? = null): PagedResponse<Media>
    @GET("search/multi") suspend fun search(@Query("query") query: String, @Query("page") page: Int = 1, @Query("language") language: String? = null): PagedResponse<Media>
    @GET("movie/{id}") suspend fun movieDetail(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits,images,videos,similar",
        @Query("include_image_language") imageLanguage: String = "en,null",
        @Query("language") language: String? = null
    ): MovieDetail
    @GET("tv/{id}") suspend fun tvDetail(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits,images,videos,similar,external_ids",
        @Query("include_image_language") imageLanguage: String = "en,null",
        @Query("language") language: String? = null
    ): TvDetail
    @GET("tv/{id}/season/{season}") suspend fun season(@Path("id") id: Int, @Path("season") season: Int, @Query("language") language: String? = null): Season
    @GET("collection/{id}") suspend fun collection(@Path("id") id: Int, @Query("language") language: String? = null): CollectionDetails
    @GET("movie/{id}/release_dates") suspend fun movieReleaseDates(@Path("id") id: Int): ReleaseDatesResponse
    @GET("tv/{id}/content_ratings") suspend fun tvContentRatings(@Path("id") id: Int): ContentRatingsResponse

    @GET("discover/movie") suspend fun discoverMovies(
        @Query("with_genres") genreId: String?,
        @Query("sort_by") sortBy: String? = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("language") language: String? = null
    ): PagedResponse<Media>

    @GET("discover/tv") suspend fun discoverTv(
        @Query("with_genres") genreId: String?,
        @Query("sort_by") sortBy: String? = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("language") language: String? = null
    ): PagedResponse<Media>
}

data class ReleaseDatesResponse(val results: List<ReleaseDatesCountry> = emptyList())
data class ReleaseDatesCountry(
    @SerializedName("iso_3166_1") val country: String,
    @SerializedName("release_dates") val releaseDates: List<ReleaseDateEntry> = emptyList(),
)
data class ReleaseDateEntry(val certification: String?)

data class ContentRatingsResponse(val results: List<ContentRatingCountry> = emptyList())
data class ContentRatingCountry(
    @SerializedName("iso_3166_1") val country: String,
    val rating: String?,
)

data class CollectionDetails(
    val id: Int,
    val name: String,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    val parts: List<CollectionPart> = emptyList(),
)

data class CollectionPart(
    val id: Int,
    val title: String,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
)

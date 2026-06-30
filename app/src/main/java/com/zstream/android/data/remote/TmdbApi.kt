package com.zstream.android.data.remote

import com.zstream.android.data.model.*
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/movie/week") suspend fun trendingMovies(@Query("page") page: Int = 1): PagedResponse<Media>
    @GET("trending/tv/week") suspend fun trendingTv(@Query("page") page: Int = 1): PagedResponse<Media>
    @GET("movie/popular") suspend fun popularMovies(@Query("page") page: Int = 1): PagedResponse<Media>
    @GET("movie/now_playing") suspend fun nowPlayingMovies(@Query("page") page: Int = 1): PagedResponse<Media>
    @GET("movie/top_rated") suspend fun topRatedMovies(@Query("page") page: Int = 1): PagedResponse<Media>
    @GET("tv/popular") suspend fun popularTv(@Query("page") page: Int = 1): PagedResponse<Media>
    @GET("tv/top_rated") suspend fun topRatedTv(@Query("page") page: Int = 1): PagedResponse<Media>
    @GET("tv/on_the_air") suspend fun onAirTv(@Query("page") page: Int = 1): PagedResponse<Media>
    @GET("search/multi") suspend fun search(@Query("query") query: String, @Query("page") page: Int = 1): PagedResponse<Media>
    @GET("movie/{id}") suspend fun movieDetail(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits,images,videos,similar",
        @Query("include_image_language") imageLanguage: String = "en,null"
    ): MovieDetail
    @GET("tv/{id}") suspend fun tvDetail(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits,images,videos,similar,external_ids",
        @Query("include_image_language") imageLanguage: String = "en,null"
    ): TvDetail
    @GET("tv/{id}/season/{season}") suspend fun season(@Path("id") id: Int, @Path("season") season: Int): Season
    @GET("collection/{id}") suspend fun collection(@Path("id") id: Int): CollectionDetails
}

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

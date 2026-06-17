package com.zstream.android.data.remote

import com.zstream.android.data.model.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/movie/week") suspend fun trendingMovies(): PagedResponse<Media>
    @GET("trending/tv/week") suspend fun trendingTv(): PagedResponse<Media>
    @GET("movie/popular") suspend fun popularMovies(): PagedResponse<Media>
    @GET("movie/now_playing") suspend fun nowPlayingMovies(): PagedResponse<Media>
    @GET("movie/top_rated") suspend fun topRatedMovies(): PagedResponse<Media>
    @GET("tv/popular") suspend fun popularTv(): PagedResponse<Media>
    @GET("tv/top_rated") suspend fun topRatedTv(): PagedResponse<Media>
    @GET("tv/on_the_air") suspend fun onAirTv(): PagedResponse<Media>
    @GET("search/multi") suspend fun search(@Query("query") query: String, @Query("page") page: Int = 1): PagedResponse<Media>
    @GET("movie/{id}") suspend fun movieDetail(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits,images,videos,similar",
        @Query("include_image_language") imageLanguage: String = "en,null"
    ): MovieDetail
    @GET("tv/{id}") suspend fun tvDetail(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits,images,videos,similar",
        @Query("include_image_language") imageLanguage: String = "en,null"
    ): TvDetail
    @GET("tv/{id}/season/{season}") suspend fun season(@Path("id") id: Int, @Path("season") season: Int): Season
}

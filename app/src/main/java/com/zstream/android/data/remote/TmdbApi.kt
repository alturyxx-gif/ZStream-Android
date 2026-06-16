package com.zstream.android.data.remote

import com.zstream.android.data.model.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/movie/week") suspend fun trendingMovies(): PagedResponse<Media>
    @GET("trending/tv/week") suspend fun trendingTv(): PagedResponse<Media>
    @GET("search/multi") suspend fun search(@Query("query") query: String, @Query("page") page: Int = 1): PagedResponse<Media>
    @GET("movie/{id}") suspend fun movieDetail(@Path("id") id: Int, @Query("append_to_response") append: String = "credits"): MovieDetail
    @GET("tv/{id}") suspend fun tvDetail(@Path("id") id: Int, @Query("append_to_response") append: String = "credits"): TvDetail
    @GET("tv/{id}/season/{season}") suspend fun season(@Path("id") id: Int, @Path("season") season: Int): Season
}

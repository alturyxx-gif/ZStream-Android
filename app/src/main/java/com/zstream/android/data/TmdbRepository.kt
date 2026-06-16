package com.zstream.android.data

import com.zstream.android.data.model.*
import com.zstream.android.data.remote.TmdbApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbRepository @Inject constructor(private val api: TmdbApi) {
    suspend fun trendingMovies() = api.trendingMovies().results
    suspend fun trendingTv() = api.trendingTv().results
    suspend fun search(query: String, page: Int = 1) = api.search(query, page).results
        .filter { it.mediaType == "movie" || it.mediaType == "tv" }
    suspend fun movieDetail(id: Int) = api.movieDetail(id)
    suspend fun tvDetail(id: Int) = api.tvDetail(id)
    suspend fun season(id: Int, season: Int) = api.season(id, season)
}

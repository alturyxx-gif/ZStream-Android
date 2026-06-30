package com.zstream.android.data

import com.zstream.android.data.model.*
import com.zstream.android.data.remote.TmdbApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbRepository @Inject constructor(private val api: TmdbApi) {
    suspend fun trendingMovies() = api.trendingMovies().results.map { it.copy(mediaType = "movie") }
    suspend fun trendingTv() = api.trendingTv().results.map { it.copy(mediaType = "tv") }
    suspend fun popularMovies() = api.popularMovies().results.map { it.copy(mediaType = "movie") }
    suspend fun nowPlayingMovies() = api.nowPlayingMovies().results.map { it.copy(mediaType = "movie") }
    suspend fun topRatedMovies() = api.topRatedMovies().results.map { it.copy(mediaType = "movie") }
    suspend fun popularTv() = api.popularTv().results.map { it.copy(mediaType = "tv") }
    suspend fun topRatedTv() = api.topRatedTv().results.map { it.copy(mediaType = "tv") }
    suspend fun onAirTv() = api.onAirTv().results.map { it.copy(mediaType = "tv") }
    suspend fun search(
        query: String,
        page: Int = 1,
        onTotalPages: ((Int) -> Unit)? = null //keep this last if adding something or pagecount search breaks
    ): List<Media> {
        val response = api.search(query, page)
        onTotalPages?.invoke(response.totalPages)
        return response.results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
    }
    suspend fun movieDetail(id: Int) = api.movieDetail(id)
    suspend fun collection(id: Int) = api.collection(id)
    suspend fun tvDetail(id: Int) = api.tvDetail(id)
    suspend fun season(id: Int, season: Int) = api.season(id, season)
}

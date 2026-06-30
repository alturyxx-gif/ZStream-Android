package com.zstream.android.data

import com.zstream.android.data.model.*
import com.zstream.android.data.model.PagedResponse
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
    data class PagedMediaResult(val items: List<Media>, val canLoadMore: Boolean)
    private fun PagedResponse<Media>.toMediaResult(type: String) = PagedMediaResult(
        items = results.map { it.copy(mediaType = type) },
        canLoadMore = page < totalPages,
    )
    suspend fun popularMoviesPage(page: Int) = api.popularMovies(page).toMediaResult("movie")
    suspend fun nowPlayingMoviesPage(page: Int) = api.nowPlayingMovies(page).toMediaResult("movie")
    suspend fun topRatedMoviesPage(page: Int) = api.topRatedMovies(page).toMediaResult("movie")
    suspend fun trendingMoviesPage(page: Int) = api.trendingMovies(page).toMediaResult("movie")
    suspend fun popularTvPage(page: Int) = api.popularTv(page).toMediaResult("tv")
    suspend fun topRatedTvPage(page: Int) = api.topRatedTv(page).toMediaResult("tv")
    suspend fun onAirTvPage(page: Int) = api.onAirTv(page).toMediaResult("tv")
    suspend fun trendingTvPage(page: Int) = api.trendingTv(page).toMediaResult("tv")
    suspend fun search(
        query: String,
        page: Int = 1,
        onTotalPages: ((Int) -> Unit)? = null //keep this last if adding something or pagecount search breaks
    ): List<Media> {
        val response = api.search(query, page)
        onTotalPages?.invoke(response.totalPages)
        return response.results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
    }
    suspend fun searchPaged(query: String, page: Int = 1): com.zstream.android.data.model.PagedResponse<Media> = api.search(query, page)
    suspend fun movieDetail(id: Int) = api.movieDetail(id)
    suspend fun collection(id: Int) = api.collection(id)
    suspend fun tvDetail(id: Int) = api.tvDetail(id)
    suspend fun season(id: Int, season: Int) = api.season(id, season)
}

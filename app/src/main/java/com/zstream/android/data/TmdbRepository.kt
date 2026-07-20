package com.zstream.android.data

import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.data.model.*
import com.zstream.android.data.model.PagedResponse
import com.zstream.android.data.remote.TmdbApi
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbRepository @Inject constructor(
    private val api: TmdbApi,
    private val settingsPrefs: SettingsPreferences,
) {
    private suspend fun metadataLanguage() = settingsPrefs.settings.first().applicationLanguage.ifBlank { null }

    suspend fun trendingMovies() = api.trendingMovies(language = metadataLanguage()).results.map { it.copy(mediaType = "movie") }
    suspend fun trendingTv() = api.trendingTv(language = metadataLanguage()).results.map { it.copy(mediaType = "tv") }
    suspend fun popularMovies() = api.popularMovies(language = metadataLanguage()).results.map { it.copy(mediaType = "movie") }
    suspend fun nowPlayingMovies() = api.nowPlayingMovies(language = metadataLanguage()).results.map { it.copy(mediaType = "movie") }
    suspend fun topRatedMovies() = api.topRatedMovies(language = metadataLanguage()).results.map { it.copy(mediaType = "movie") }
    suspend fun popularTv() = api.popularTv(language = metadataLanguage()).results.map { it.copy(mediaType = "tv") }
    suspend fun topRatedTv() = api.topRatedTv(language = metadataLanguage()).results.map { it.copy(mediaType = "tv") }
    suspend fun onAirTv() = api.onAirTv(language = metadataLanguage()).results.map { it.copy(mediaType = "tv") }
    data class PagedMediaResult(val items: List<Media>, val canLoadMore: Boolean)
    private fun PagedResponse<Media>.toMediaResult(type: String) = PagedMediaResult(
        items = results.map { it.copy(mediaType = type) },
        canLoadMore = page < totalPages,
    )
    suspend fun popularMoviesPage(page: Int) = api.popularMovies(page, metadataLanguage()).toMediaResult("movie")
    suspend fun nowPlayingMoviesPage(page: Int) = api.nowPlayingMovies(page, metadataLanguage()).toMediaResult("movie")
    suspend fun topRatedMoviesPage(page: Int) = api.topRatedMovies(page, metadataLanguage()).toMediaResult("movie")
    suspend fun trendingMoviesPage(page: Int) = api.trendingMovies(page, metadataLanguage()).toMediaResult("movie")
    suspend fun popularTvPage(page: Int) = api.popularTv(page, metadataLanguage()).toMediaResult("tv")
    suspend fun topRatedTvPage(page: Int) = api.topRatedTv(page, metadataLanguage()).toMediaResult("tv")
    suspend fun onAirTvPage(page: Int) = api.onAirTv(page, metadataLanguage()).toMediaResult("tv")
    suspend fun trendingTvPage(page: Int) = api.trendingTv(page, metadataLanguage()).toMediaResult("tv")
    suspend fun discoverMoviesPage(genreId: Int?, sortBy: String?, page: Int) = api.discoverMovies(genreId?.toString(), sortBy, page, metadataLanguage()).toMediaResult("movie")
    suspend fun discoverTvPage(genreId: Int?, sortBy: String?, page: Int) = api.discoverTv(genreId?.toString(), sortBy, page, metadataLanguage()).toMediaResult("tv")
    suspend fun search(
        query: String,
        page: Int = 1,
        onTotalPages: ((Int) -> Unit)? = null //keep this last if adding something or pagecount search breaks
    ): List<Media> {
        val response = api.search(query, page, metadataLanguage())
        onTotalPages?.invoke(response.totalPages)
        return response.results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
    }
    suspend fun searchPaged(query: String, page: Int = 1): com.zstream.android.data.model.PagedResponse<Media> = api.search(query, page, metadataLanguage())
    suspend fun movieDetail(id: Int): MovieDetail {
        val language = metadataLanguage()
        val detail = api.movieDetail(id, language = language)
        if (language == null || !detail.overview.isNullOrBlank()) return detail
        val fallbackOverview = api.movieDetail(id, language = null).overview
        return if (fallbackOverview.isNullOrBlank()) detail else detail.copy(overview = fallbackOverview)
    }
    suspend fun collection(id: Int) = api.collection(id, metadataLanguage())
    suspend fun tvDetail(id: Int): TvDetail {
        val language = metadataLanguage()
        val detail = api.tvDetail(id, language = language)
        if (language == null || !detail.overview.isNullOrBlank()) return detail
        val fallbackOverview = api.tvDetail(id, language = null).overview
        return if (fallbackOverview.isNullOrBlank()) detail else detail.copy(overview = fallbackOverview)
    }
    suspend fun season(id: Int, season: Int): Season {
        val language = metadataLanguage()
        val result = api.season(id, season, language)
        if (language == null || result.episodes.isNullOrEmpty() || result.episodes.none { it.overview.isNullOrBlank() }) return result
        val fallback = api.season(id, season, null)
        val fallbackById = fallback.episodes?.associateBy { it.id } ?: emptyMap()
        return result.copy(episodes = result.episodes.map { ep ->
            if (!ep.overview.isNullOrBlank()) ep
            else fallbackById[ep.id]?.overview?.takeUnless { it.isBlank() }?.let { ep.copy(overview = it) } ?: ep
        })
    }
}

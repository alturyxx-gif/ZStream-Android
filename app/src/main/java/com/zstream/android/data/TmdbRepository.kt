package com.zstream.android.data

import android.util.Log
import com.zstream.android.data.model.*
import com.zstream.android.data.model.PagedResponse
import com.zstream.android.data.remote.TmdbApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbRepository @Inject constructor(private val api: TmdbApi) {
    private val seasonCatalogCache = java.util.concurrent.ConcurrentHashMap<Int, TvSeasonCatalog>()
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
    suspend fun discoverMoviesPage(genreId: Int?, sortBy: String?, page: Int) = api.discoverMovies(genreId?.toString(), sortBy, page).toMediaResult("movie")
    suspend fun discoverTvPage(genreId: Int?, sortBy: String?, page: Int) = api.discoverTv(genreId?.toString(), sortBy, page).toMediaResult("tv")
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

    suspend fun seasonCatalog(tvId: Int, detail: TvDetail? = null): TvSeasonCatalog {
        seasonCatalogCache[tvId]?.let { return it }

        val tvDetail = if (detail != null) {
            detail
        } else {
            runCatching { tvDetail(tvId) }.getOrNull()
                ?: return TvSeasonCatalog(seasons = emptyList(), usingEpisodeGroups = false)
        }

        val catalog = resolveSeasonCatalog(tvId, tvDetail)
        seasonCatalogCache[tvId] = catalog
        return catalog
    }

    fun cachedSeasonCatalog(tvId: Int): TvSeasonCatalog? = seasonCatalogCache[tvId]

    fun clearSeasonCatalogCache(tvId: Int? = null) {
        if (tvId == null) seasonCatalogCache.clear() else seasonCatalogCache.remove(tvId)
    }

    suspend fun seasonForPlayback(tvId: Int, seasonNumber: Int, detail: TvDetail? = null): Season? {
        val catalog = seasonCatalog(tvId, detail)
        if (catalog.usingEpisodeGroups) {
            return catalog.season(seasonNumber)
        }
        return runCatching { season(tvId, seasonNumber) }.getOrNull()
    }

    private suspend fun resolveSeasonCatalog(tvId: Int, detail: TvDetail?): TvSeasonCatalog {
        if (detail == null) {
            return TvSeasonCatalog(seasons = emptyList(), usingEpisodeGroups = false)
        }
        if (!EpisodeGroupResolver.shouldPreferEpisodeGroups(detail)) {
            return EpisodeGroupResolver.defaultCatalogFromDetail(detail)
        }
        val summaries = runCatching { api.episodeGroups(tvId).results }
            .onFailure { Log.w(TAG, "episode_groups failed for tv $tvId", it) }
            .getOrDefault(emptyList())
        val best = EpisodeGroupResolver.pickBestGroup(summaries)
            ?: return EpisodeGroupResolver.defaultCatalogFromDetail(detail)

        val groupDetail = runCatching { api.episodeGroupDetail(best.id) }
            .onFailure { Log.w(TAG, "episode_group detail failed for ${best.id}", it) }
            .getOrNull()
            ?: return EpisodeGroupResolver.defaultCatalogFromDetail(detail)

        val mapped = EpisodeGroupResolver.mapGroupDetailToCatalog(groupDetail)
        if (mapped.seasons.size < 2) {
            return EpisodeGroupResolver.defaultCatalogFromDetail(detail)
        }
        Log.i(
            TAG,
            "Using episode group '${mapped.groupName}' (${mapped.groupId}) for tv $tvId " +
                "— ${mapped.seasons.size} display seasons",
        )
        return mapped
    }

    private companion object {
        private const val TAG = "TmdbRepository"
    }
}


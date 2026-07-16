package com.zstream.android.data.remote

import com.zstream.android.data.model.Media
import com.zstream.android.data.model.MovieDetail
import com.zstream.android.data.model.PagedResponse
import com.zstream.android.data.model.Season
import com.zstream.android.data.model.TvDetail
import com.zstream.shared.Urls
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url

/** TMDB's default public key, same fallback already committed in app/build.gradle.kts. */
const val TMDB_API_KEY_DEFAULT = "REDACTED"

/**
 * Ktor has no Retrofit-style annotated-interface codegen, so each endpoint is a plain suspend
 * function issuing a GET against Urls.TMDB_BASE. [apiKey] is a lambda (not a fixed value) so
 * callers can swap in a user-supplied TMDB key at runtime without rebuilding this client.
 */
class TmdbApi(
    private val client: HttpClient,
    private val apiKey: () -> String = { TMDB_API_KEY_DEFAULT },
) {
    private suspend inline fun <reified T> get(path: String, query: Map<String, String?> = emptyMap()): T =
        client.get {
            url(Urls.TMDB_BASE + path)
            parameter("api_key", apiKey())
            query.forEach { (k, v) -> if (v != null) parameter(k, v) }
        }.body()

    suspend fun trendingMovies(page: Int = 1): PagedResponse<Media> =
        get("trending/movie/week", mapOf("page" to page.toString()))

    suspend fun trendingTv(page: Int = 1): PagedResponse<Media> =
        get("trending/tv/week", mapOf("page" to page.toString()))

    suspend fun popularMovies(page: Int = 1): PagedResponse<Media> =
        get("movie/popular", mapOf("page" to page.toString()))

    suspend fun nowPlayingMovies(page: Int = 1): PagedResponse<Media> =
        get("movie/now_playing", mapOf("page" to page.toString()))

    suspend fun topRatedMovies(page: Int = 1): PagedResponse<Media> =
        get("movie/top_rated", mapOf("page" to page.toString()))

    suspend fun popularTv(page: Int = 1): PagedResponse<Media> =
        get("tv/popular", mapOf("page" to page.toString()))

    suspend fun topRatedTv(page: Int = 1): PagedResponse<Media> =
        get("tv/top_rated", mapOf("page" to page.toString()))

    suspend fun onAirTv(page: Int = 1): PagedResponse<Media> =
        get("tv/on_the_air", mapOf("page" to page.toString()))

    suspend fun search(query: String, page: Int = 1): PagedResponse<Media> =
        get("search/multi", mapOf("query" to query, "page" to page.toString()))

    suspend fun movieDetail(
        id: Int,
        append: String = "credits,images,videos,similar",
        imageLanguage: String = "en,null",
    ): MovieDetail = get(
        "movie/$id",
        mapOf("append_to_response" to append, "include_image_language" to imageLanguage),
    )

    suspend fun tvDetail(
        id: Int,
        append: String = "credits,images,videos,similar,external_ids",
        imageLanguage: String = "en,null",
    ): TvDetail = get(
        "tv/$id",
        mapOf("append_to_response" to append, "include_image_language" to imageLanguage),
    )

    suspend fun season(id: Int, season: Int): Season = get("tv/$id/season/$season")

    suspend fun collection(id: Int): CollectionDetails = get("collection/$id")

    suspend fun movieReleaseDates(id: Int): ReleaseDatesResponse = get("movie/$id/release_dates")

    suspend fun tvContentRatings(id: Int): ContentRatingsResponse = get("tv/$id/content_ratings")

    suspend fun discoverMovies(genreId: String?, sortBy: String? = "popularity.desc", page: Int = 1): PagedResponse<Media> =
        get("discover/movie", mapOf("with_genres" to genreId, "sort_by" to sortBy, "page" to page.toString()))

    suspend fun discoverTv(genreId: String?, sortBy: String? = "popularity.desc", page: Int = 1): PagedResponse<Media> =
        get("discover/tv", mapOf("with_genres" to genreId, "sort_by" to sortBy, "page" to page.toString()))
}

package com.zstream.android.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

interface ImdbApi {
    @POST(".") suspend fun graphQL(@Body body: ImdbGraphQLRequest): ImdbGraphQLResponse
}

data class ImdbGraphQLRequest(
    val query: String,
    val variables: Map<String, Any?>,
)

data class ImdbGraphQLResponse(
    val data: ImdbGraphQLData?,
    val errors: List<Map<String, Any?>>? = null,
)

data class ImdbGraphQLData(
    val title: ImdbTitleNode? = null,
    val chartTitles: ImdbEdges<ImdbCardNode>? = null,
    val advancedTitleSearch: ImdbSearchConnection? = null,
)

data class ImdbEdges<T>(val edges: List<ImdbEdge<T>> = emptyList())
data class ImdbEdge<T>(val cursor: String? = null, val node: T? = null)

data class ImdbSearchConnection(
    val total: Int = 0,
    val pageInfo: ImdbPageInfo? = null,
    val edges: List<ImdbSearchEdge> = emptyList(),
)
data class ImdbPageInfo(val hasNextPage: Boolean = false, val endCursor: String? = null)
data class ImdbSearchEdge(val cursor: String? = null, val node: ImdbSearchNode? = null)
data class ImdbSearchNode(val title: ImdbCardNode? = null)

data class ImdbText(val text: String? = null)
data class ImdbNameText(val value: String? = null)
data class ImdbImage(val url: String? = null, val width: Int? = null, val height: Int? = null)
data class ImdbReleaseYear(val year: Int? = null, val endYear: Int? = null)
data class ImdbRatingsSummary(val aggregateRating: Double? = null, val voteCount: Int? = null)
data class ImdbTitleType(val id: String? = null, val text: String? = null)
data class ImdbGenre(val id: String? = null, val text: String? = null)
data class ImdbGenreList(val genres: List<ImdbGenre> = emptyList())
data class ImdbPlot(val plotText: ImdbPlotText? = null)
data class ImdbPlotText(val plainText: String? = null)
data class ImdbCertificate(val rating: String? = null)
data class ImdbRuntime(val seconds: Int? = null)

data class ImdbPlaybackDisplayName(val value: String? = null)
data class ImdbPlaybackUrl(
    val url: String? = null,
    val mimeType: String? = null,
    val displayName: ImdbPlaybackDisplayName? = null,
)

data class ImdbVideoNode(
    val id: String? = null,
    val name: ImdbNameText? = null,
    val thumbnail: ImdbImage? = null,
    val playbackURLs: List<ImdbPlaybackUrl> = emptyList(),
)

data class ImdbCardNode(
    val id: String,
    val titleText: ImdbText? = null,
    val releaseYear: ImdbReleaseYear? = null,
    val ratingsSummary: ImdbRatingsSummary? = null,
    val primaryImage: ImdbImage? = null,
    val titleType: ImdbTitleType? = null,
    val runtime: ImdbRuntime? = null,
    val genres: ImdbGenreList? = null,
)

data class ImdbTitleNode(
    val id: String,
    val titleText: ImdbText? = null,
    val originalTitleText: ImdbText? = null,
    val releaseYear: ImdbReleaseYear? = null,
    val plot: ImdbPlot? = null,
    val ratingsSummary: ImdbRatingsSummary? = null,
    val primaryImage: ImdbImage? = null,
    val titleType: ImdbTitleType? = null,
    val certificate: ImdbCertificate? = null,
    val runtime: ImdbRuntime? = null,
    val genres: ImdbGenreList? = null,
    val latestTrailer: ImdbVideoNode? = null,
    val primaryVideos: ImdbEdges<ImdbVideoNodeWrapper>? = null,
    val moreLikeThisTitles: ImdbEdges<ImdbCardNode>? = null,
)

// primaryVideos edges wrap { node { ...ImdbVideoNode } } — reuse ImdbVideoNode directly as the node type.
typealias ImdbVideoNodeWrapper = ImdbVideoNode

const val IMDB_TITLE_QUERY = """
query TitleBundle(${'$'}id: ID!, ${'$'}similarFirst: Int!, ${'$'}videosFirst: Int!) {
  title(id: ${'$'}id) {
    id
    titleText { text }
    originalTitleText { text }
    releaseYear { year endYear }
    plot { plotText { plainText } }
    ratingsSummary { aggregateRating voteCount }
    primaryImage { url width height }
    titleType { id text }
    certificate { rating }
    runtime { seconds }
    genres { genres { id text } }
    latestTrailer {
      id
      name { value }
      thumbnail { url }
      playbackURLs { url mimeType displayName { value } }
    }
    primaryVideos(first: ${'$'}videosFirst) {
      edges {
        node {
          id
          name { value }
          thumbnail { url }
          playbackURLs { url mimeType displayName { value } }
        }
      }
    }
    moreLikeThisTitles(first: ${'$'}similarFirst) {
      edges {
        node {
          id
          titleText { text }
          releaseYear { year endYear }
          ratingsSummary { aggregateRating voteCount }
          primaryImage { url }
          titleType { id text }
        }
      }
    }
  }
}
"""

const val IMDB_CHART_TITLES_QUERY = """
query ChartTitles(${'$'}first: Int!, ${'$'}chart: ChartTitleOptions!) {
  chartTitles(first: ${'$'}first, chart: ${'$'}chart) {
    edges {
      node {
        id
        titleText { text }
        releaseYear { year endYear }
        ratingsSummary { aggregateRating voteCount }
        primaryImage { url }
        titleType { id text }
      }
    }
  }
}
"""

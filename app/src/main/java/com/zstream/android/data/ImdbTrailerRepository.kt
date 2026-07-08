package com.zstream.android.data

import com.zstream.android.data.remote.IMDB_TITLE_QUERY
import com.zstream.android.data.remote.ImdbApi
import com.zstream.android.data.remote.ImdbGraphQLRequest
import com.zstream.android.data.remote.ImdbPlaybackUrl
import javax.inject.Inject
import javax.inject.Singleton

data class ImdbTrailer(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val playbackUrl: String,
    val mimeType: String?,
)

@Singleton
class ImdbTrailerRepository @Inject constructor(
    private val api: ImdbApi,
) {
    private fun normalizeImdbId(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("tt")) trimmed else "tt$trimmed"
    }

    // Prefer mp4 over HLS/DASH manifests since these play directly in ExoPlayer
    // without extra source-type plumbing; fall back to whatever's first otherwise.
    private fun bestPlaybackUrl(urls: List<ImdbPlaybackUrl>): ImdbPlaybackUrl? {
        if (urls.isEmpty()) return null
        return urls.firstOrNull { it.mimeType?.contains("mp4", ignoreCase = true) == true } ?: urls.first()
    }

    suspend fun getTrailers(imdbId: String, videosFirst: Int = 12): List<ImdbTrailer> {
        val response = api.graphQL(
            ImdbGraphQLRequest(
                query = IMDB_TITLE_QUERY,
                variables = mapOf(
                    "id" to normalizeImdbId(imdbId),
                    "similarFirst" to 1,
                    "videosFirst" to videosFirst,
                ),
            )
        )
        val title = response.data?.title ?: return emptyList()
        val out = mutableListOf<ImdbTrailer>()

        title.latestTrailer?.let { node ->
            bestPlaybackUrl(node.playbackURLs)?.let { playback ->
                out += ImdbTrailer(
                    id = node.id ?: "latest",
                    name = node.name?.value ?: "Trailer",
                    thumbnailUrl = node.thumbnail?.url,
                    playbackUrl = playback.url ?: return@let,
                    mimeType = playback.mimeType,
                )
            }
        }

        title.primaryVideos?.edges.orEmpty().forEach { edge ->
            val node = edge.node ?: return@forEach
            val id = node.id ?: return@forEach
            if (id == title.latestTrailer?.id) return@forEach
            val playback = bestPlaybackUrl(node.playbackURLs) ?: return@forEach
            out += ImdbTrailer(
                id = id,
                name = node.name?.value ?: "Video",
                thumbnailUrl = node.thumbnail?.url,
                playbackUrl = playback.url ?: return@forEach,
                mimeType = playback.mimeType,
            )
        }

        return out
    }
}

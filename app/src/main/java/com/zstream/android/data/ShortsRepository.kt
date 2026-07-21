package com.zstream.android.data

import com.zstream.android.data.model.ShortItem
import com.zstream.android.data.model.ShortsStreamResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNELS_PER_PAGE = 5

@Singleton
class ShortsRepository @Inject constructor(
    private val rssFetcher: ShortsRssFetcher,
    private val tmdbMatcher: ShortsTmdbMatcher,
    private val streamResolver: YoutubeStreamResolver,
) {
    suspend fun loadPage(excludeIds: Set<String>, limit: Int = 10): List<ShortItem> = coroutineScope {
        val channels = ShortsChannels.all.shuffled().take(CHANNELS_PER_PAGE)
        val perChannel = channels.map { channel ->
            async { channel to rssFetcher.fetchChannel(channel.id) }
        }.awaitAll()

        val candidates = perChannel
            .flatMap { (channel, videos) -> videos.map { channel to it } }
            .filter { (_, video) -> video.videoId !in excludeIds && !looksLikeInterview(video.title) }
            .shuffled()
            .take(limit)

        candidates.map { (channel, video) ->
            async {
                val match = runCatching { tmdbMatcher.match(video.title) }.getOrNull()
                ShortItem(
                    id = video.videoId,
                    videoId = video.videoId,
                    title = video.title,
                    channelName = channel.name,
                    thumbnailUrl = video.thumbnailUrl,
                    durationSec = 0,
                    tmdbId = match?.tmdbId,
                    mediaType = match?.mediaType,
                )
            }
        }.awaitAll()
    }

    suspend fun resolveStream(videoId: String): ShortsStreamResponse =
        streamResolver.resolve(videoId)
}

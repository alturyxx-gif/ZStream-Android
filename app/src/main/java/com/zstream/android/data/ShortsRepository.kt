package com.zstream.android.data

import com.zstream.android.data.model.ShortsFeedResponse
import com.zstream.android.data.model.ShortsStreamResponse
import com.zstream.android.data.remote.ShortsApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortsRepository @Inject constructor(
    private val api: ShortsApi,
    private val streamResolver: YoutubeStreamResolver,
) {
    suspend fun loadPage(cursor: String?, limit: Int = 10): ShortsFeedResponse =
        api.feed(cursor, limit)

    suspend fun resolveStream(videoId: String): ShortsStreamResponse =
        streamResolver.resolve(videoId)
}

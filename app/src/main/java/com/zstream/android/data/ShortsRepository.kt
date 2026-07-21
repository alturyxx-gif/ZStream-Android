package com.zstream.android.data

import com.zstream.android.data.model.ShortsFeedResponse
import com.zstream.android.data.model.ShortsStreamResponse
import com.zstream.android.data.remote.ShortsApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over [ShortsApi]. [resolveStream] always hits the network
 * fresh -- the resolved video/audio URLs are signed and short-lived, so
 * caching them client-side would just mean playing a stale link.
 */
@Singleton
class ShortsRepository @Inject constructor(
    private val api: ShortsApi,
) {
    suspend fun loadPage(cursor: String?, limit: Int = 10): ShortsFeedResponse =
        api.feed(cursor, limit)

    suspend fun resolveStream(videoId: String): ShortsStreamResponse =
        api.stream(videoId)
}

package com.zstream.android.data.remote

import com.zstream.android.data.model.ShortsFeedResponse
import com.zstream.android.data.model.ShortsStreamResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ShortsApi {
    @GET("feed")
    suspend fun feed(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int = 10,
    ): ShortsFeedResponse

    @GET("stream/{videoId}")
    suspend fun stream(@Path("videoId") videoId: String): ShortsStreamResponse
}

package com.zstream.android.data.model

import com.google.gson.annotations.SerializedName

data class ShortItem(
    val id: String,
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationSec: Int,
    val tmdbId: Int?,
    val mediaType: String?,
)

data class ShortsFeedResponse(
    val items: List<ShortItem>,
    val nextCursor: String?,
)

data class ShortsStreamResponse(
    val videoUrl: String,
    val audioUrl: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val isVertical: Boolean,
    @SerializedName("expiresAt") val expiresAtEpochSec: Long,
    val userAgent: String,
    val clipStartMs: Long = 0L,
    val clipEndMs: Long? = null,
)

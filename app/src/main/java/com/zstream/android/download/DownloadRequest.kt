package com.zstream.android.download

import com.zstream.android.plugin.Caption

/** Everything needed to run one download end to end. Built by the quality-picker UI. */
data class DownloadRequest(
    val tmdbId: String,
    val target: DownloadTarget,
    val sourceId: String,
    /** Human-readable plugin name (e.g. "Artemis"), as reported by the plugin itself — falls back to [sourceId] if unknown. */
    val sourceDisplayName: String = sourceId,
    val variantId: String,
    val qualityLabel: String,
    val streamUrl: String,
    val streamType: String, // "hls" or "file"
    val headers: Map<String, String>,
    val captions: List<Caption> = emptyList(),
    /** Separate audio-only rendition URL, resolved from the master playlist's #EXT-X-MEDIA group when this stream doesn't mux audio into the video segments. */
    val audioStreamUrl: String? = null,
    /** Raw TMDB poster path (e.g. "/abc123.jpg"), shown in the Downloads list. */
    val posterPath: String? = null,
)

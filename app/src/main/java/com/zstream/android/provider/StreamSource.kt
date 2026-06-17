package com.zstream.android.provider

import com.zstream.android.Urls


data class MediaRequest(
    val type: Type,
    val tmdbId: String,
    val season: Int? = null,
    val episode: Int? = null,
) {
    enum class Type { MOVIE, SHOW }
}

/**
 * A streaming provider (embed site) definition.
 *
 * To add a new source you only need to implement these three things — everything else
 * (the headless WebView discovery loop, the local HLS proxy, the ExoPlayer wiring) is
 * shared and provider-agnostic. See [StreamExtractor] and [WebViewProxyServer].
 *
 * The three per-source variables, learned empirically per the playbook:
 *  1. [embedUrl]        — the page URL whose JavaScript generates the signed stream URL.
 *  2. [isStreamUrl]     — how to recognise the stream request among all page requests.
 *  3. [playbackHeaders] — the headers the CDN edge validates (almost always Referer).
 *                         Discover these by diffing the WebView's own successful request
 *                         headers; the embed site's own origin is the usual Referer value.
 */
interface StreamSource {
    /** Stable identifier, surfaced in the source-status UI. */
    val id: String

    /** Headers applied to every proxy fetch (segments + playlists). */
    val playbackHeaders: Map<String, String>

    /** Build the embed page URL the WebView loads to discover the stream. */
    fun embedUrl(media: MediaRequest): String

    /**
     * Return true if [url] (an intercepted WebView request) is the stream we want.
     * Ignore loopback URLs — those are our own proxy.
     */
    fun isStreamUrl(url: String): Boolean
}

/**
 * vidlink.pro — HLS via the storm.vodvidl.site CDN.
 *
 * Notes specific to this source:
 *  - The CDN edge validates Referer == the embed site (vidlink.pro), NOT the megacloud
 *    referer embedded in the stream URL's `headers` query param (that one is forwarded
 *    upstream by the storm proxy, not checked at the edge).
 *  - Master playlist references child playlists by ABSOLUTE PATH (/proxy/...), so the
 *    proxy's m3u8 rewriter must resolve `/`-prefixed refs against scheme://host.
 */
object VidlinkSource : StreamSource {
    override val id = "vidlink"

    override val playbackHeaders = mapOf(
        "Referer" to Urls.VIDLINK,
        "Origin" to Urls.VIDLINK.trimEnd('/'),
    )

    override fun embedUrl(media: MediaRequest): String = when (media.type) {
        MediaRequest.Type.SHOW ->
            "${Urls.VIDLINK}tv/${media.tmdbId}/${media.season}/${media.episode}"
        MediaRequest.Type.MOVIE ->
            "${Urls.VIDLINK}movie/${media.tmdbId}"
    }

    override fun isStreamUrl(url: String): Boolean {
        if (url.contains("127.0.0.1")) return false
        if (url.contains(".m3u8")) return true
        if (url.contains(".mp4") && !url.contains("thumbnail") && !url.contains("poster")) return true
        return false
    }
}

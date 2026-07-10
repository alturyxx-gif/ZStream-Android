package com.zstream.android.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Minimal HLS playlist model + parser, ported from the desktop app's Go implementation
 * (source-specific-hls-downloads.txt) since neither OkHttp nor the JDK has one built in.
 */
data class HlsKey(val method: String, val uri: String, val iv: String)

data class HlsSegment(
    val uri: String,
    val duration: Double,
    val key: HlsKey?,
    val seq: Long,
)

data class HlsVariant(val uri: String, val bandwidth: Long, val height: Int? = null, val audioGroupId: String? = null)

data class HlsAudioRendition(
    val uri: String,
    val language: String,
    val name: String,
    val isDefault: Boolean,
)

data class HlsPlaylist(
    val isMaster: Boolean,
    val variants: List<HlsVariant> = emptyList(),
    val segments: List<HlsSegment> = emptyList(),
    val initUri: String? = null,
    val keys: List<HlsKey> = emptyList(),
    val mediaSeq: Long = 0,
    val audioRenditions: Map<String, List<HlsAudioRendition>> = emptyMap(),
)

/**
 * OkHttp's default User-Agent ("okhttp/4.x") gets 403'd by CDNs with hotlink/anti-scraper rules
 * that otherwise allow ExoPlayer's own default UA during normal playback of the same URL — so
 * every outgoing download request needs a browser-like UA unless the resolve response already
 * supplied one.
 */
private const val DEFAULT_DOWNLOAD_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"

fun downloadHeaders(headers: Map<String, String>): okhttp3.Headers {
    val hasUserAgent = headers.keys.any { it.equals("User-Agent", ignoreCase = true) }
    val merged = if (hasUserAgent) headers else headers + ("User-Agent" to DEFAULT_DOWNLOAD_USER_AGENT)
    return merged.toHeaders()
}

private val attrRegex = Regex("""([A-Z0-9-]+)=(?:"([^"]*)"|([^,]+))""")

private fun parseAttrs(s: String): Map<String, String> =
    attrRegex.findAll(s).associate { m ->
        val key = m.groupValues[1]
        val value = m.groupValues[2].ifEmpty { m.groupValues[3] }
        key to value
    }

private fun resolveAgainst(ref: String, base: String): String {
    if (ref.isEmpty()) return ""
    return runCatching { java.net.URI(base).resolve(ref).toString() }.getOrDefault(ref)
}

fun parseHlsPlaylist(body: String, baseUrl: String): HlsPlaylist {
    val lines = body.split("\n")
    var isMaster = false
    val variants = mutableListOf<HlsVariant>()
    val segments = mutableListOf<HlsSegment>()
    var initUri: String? = null
    val keySet = LinkedHashMap<String, HlsKey>()
    var curKey: HlsKey? = null
    var pendingBandwidth: Long? = null
    var pendingHeight: Int? = null
    var pendingAudioGroupId: String? = null
    var extinfDur = 0.0
    var haveExtinf = false
    var mediaSeq = 0L
    val audioRenditions = LinkedHashMap<String, MutableList<HlsAudioRendition>>()

    fun upsertKey(k: HlsKey?): HlsKey? {
        if (k == null || k.method.equals("NONE", true)) return null
        val cacheKey = "${k.uri}|${k.iv}"
        return keySet.getOrPut(cacheKey) { k }
    }

    for (raw in lines) {
        val line = raw.trim()
        if (line.isEmpty()) continue

        when {
            line.startsWith("#EXT-X-STREAM-INF:") -> {
                isMaster = true
                val attrs = parseAttrs(line)
                pendingBandwidth = attrs["BANDWIDTH"]?.toLongOrNull()
                pendingHeight = attrs["RESOLUTION"]?.substringAfter('x', "")?.toIntOrNull()
                pendingAudioGroupId = attrs["AUDIO"]
            }
            line.startsWith("#EXT-X-MEDIA:") -> {
                val attrs = parseAttrs(line)
                if (attrs["TYPE"] == "AUDIO") {
                    val groupId = attrs["GROUP-ID"]
                    val uri = attrs["URI"]
                    if (groupId != null && !uri.isNullOrEmpty()) {
                        audioRenditions.getOrPut(groupId) { mutableListOf() }.add(
                            HlsAudioRendition(
                                uri = resolveAgainst(uri, baseUrl),
                                language = attrs["LANGUAGE"].orEmpty(),
                                name = attrs["NAME"].orEmpty(),
                                isDefault = attrs["DEFAULT"].equals("YES", ignoreCase = true),
                            )
                        )
                    }
                }
            }
            line.startsWith("#EXT-X-KEY:") -> {
                val attrs = parseAttrs(line)
                val method = attrs["METHOD"].orEmpty()
                curKey = if (method.isEmpty() || method.equals("NONE", true)) {
                    null
                } else {
                    upsertKey(HlsKey(method, resolveAgainst(attrs["URI"].orEmpty(), baseUrl), attrs["IV"].orEmpty()))
                }
            }
            line.startsWith("#EXT-X-MAP:") -> {
                val attrs = parseAttrs(line)
                initUri = resolveAgainst(attrs["URI"].orEmpty(), baseUrl)
            }
            line.startsWith("#EXT-X-MEDIA-SEQUENCE") -> {
                val idx = line.indexOf(':')
                if (idx > 0) mediaSeq = line.substring(idx + 1).trim().toLongOrNull() ?: 0L
            }
            line.startsWith("#EXTINF:") -> {
                var v = line.removePrefix("#EXTINF:")
                val comma = v.indexOf(',')
                if (comma >= 0) v = v.substring(0, comma)
                extinfDur = v.trim().toDoubleOrNull() ?: 0.0
                haveExtinf = true
            }
            line.startsWith("#EXT-X-BYTERANGE:") || line.startsWith("#EXT-X-DISCONTINUITY") -> {
                // Not used for download/remux correctness here — byte-range HLS segments are rare
                // and discontinuities don't affect segment fetch/decrypt logic.
            }
            line.startsWith("#") -> Unit
            else -> {
                val abs = resolveAgainst(line, baseUrl)
                if (pendingBandwidth != null) {
                    variants.add(HlsVariant(abs, pendingBandwidth, pendingHeight, pendingAudioGroupId))
                    pendingBandwidth = null
                    pendingHeight = null
                    pendingAudioGroupId = null
                } else if (haveExtinf) {
                    segments.add(HlsSegment(abs, extinfDur, curKey, mediaSeq + segments.size))
                    haveExtinf = false
                }
            }
        }
    }

    return HlsPlaylist(isMaster, variants, segments, initUri, keySet.values.toList(), mediaSeq, audioRenditions)
}

data class HlsQualityOption(val uri: String, val bandwidth: Long, val height: Int? = null, val audioOptions: List<HlsAudioRendition> = emptyList())

fun fetchHlsQualityOptions(client: OkHttpClient, playlistUrl: String, headers: Map<String, String>): List<HlsQualityOption> {
    val request = Request.Builder().url(playlistUrl).headers(downloadHeaders(headers)).build()
    val body = client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) error("HLS playlist fetch failed: HTTP ${resp.code}")
        resp.body?.string() ?: error("Empty playlist response")
    }
    val playlist = parseHlsPlaylist(body, playlistUrl)
    if (!playlist.isMaster) return emptyList()
    return playlist.variants.sortedByDescending { it.bandwidth }.map { v ->
        HlsQualityOption(v.uri, v.bandwidth, v.height, v.audioGroupId?.let { playlist.audioRenditions[it] }.orEmpty())
    }
}

fun pickDefaultAudioRendition(options: List<HlsAudioRendition>): HlsAudioRendition? =
    options.firstOrNull { it.isDefault } ?: options.firstOrNull()

suspend fun probeHlsSegment(client: OkHttpClient, playlistUrl: String, headers: Map<String, String>): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val fetched = fetchAndParseHlsPlaylist(client, playlistUrl, headers)
            val segment = fetched.playlist.segments.firstOrNull() ?: return@runCatching true
            val request = Request.Builder()
                .url(segment.uri)
                .headers(downloadHeaders(headers))
                .header("Range", "bytes=0-0")
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

data class HlsFetchResult(val playlist: HlsPlaylist, val audioPlaylistUrl: String?)

fun fetchAndParseHlsPlaylist(client: OkHttpClient, playlistUrl: String, headers: Map<String, String>): HlsFetchResult {
    var url = playlistUrl
    var audioUrl: String? = null
    repeat(3) {
        val request = Request.Builder().url(url).headers(downloadHeaders(headers)).build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HLS playlist fetch failed: HTTP ${resp.code}")
            resp.body?.string() ?: error("Empty playlist response")
        }
        val playlist = parseHlsPlaylist(body, url)
        if (playlist.isMaster) {
            val best = playlist.variants.maxByOrNull { it.bandwidth } ?: error("Master playlist has no variants")
            audioUrl = best.audioGroupId?.let { playlist.audioRenditions[it] }?.let { pickDefaultAudioRendition(it) }?.uri
            url = best.uri
        } else {
            return HlsFetchResult(playlist, audioUrl)
        }
    }
    error("HLS playlist recursion too deep")
}

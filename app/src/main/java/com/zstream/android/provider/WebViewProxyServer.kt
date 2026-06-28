package com.zstream.android.provider

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "WebViewProxy"

/**
 * Local HTTP proxy that lets native ExoPlayer fetch a CDN-protected HLS stream.
 *
 * ExoPlayer requests `http://127.0.0.1:{port}/{url-encoded-real-url}`. The proxy decodes
 * the real URL, fetches it with OkHttp while injecting [playbackHeaders] (the headers the
 * CDN edge validates — usually a Referer), rewrites m3u8 playlists so every child URL
 * routes back through the proxy, and streams the bytes to ExoPlayer.
 *
 * Provider-agnostic: the only per-source input is [playbackHeaders], set by the caller
 * once the active stream is known.
 */
class WebViewProxyServer {
    private val serverSocket = ServerSocket(0)
    val port: Int get() = serverSocket.localPort

    /** Headers injected into every upstream fetch. Set per active source before playback. */
    @Volatile
    var playbackHeaders: Map<String, String> = emptyMap()

    private val okhttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun start() {
        Thread {
            Log.d(TAG, "listening on $port")
            while (!serverSocket.isClosed) {
                try { val s = serverSocket.accept(); Thread { handle(s) }.start() }
                catch (e: Exception) { if (!serverSocket.isClosed) Log.e(TAG, "accept", e) }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun handle(socket: Socket) {
        try {
            val reader = socket.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return
            while (reader.readLine()?.isNotEmpty() == true) { /* drain headers */ }

            val rawPath = requestLine.split(" ").getOrNull(1) ?: return
            val targetUrl = URLDecoder.decode(rawPath.trimStart('/'), "UTF-8")
            val isM3u8 = targetUrl.contains(".m3u8")
            Log.d(TAG, "serving ${if (isM3u8) "M3U8" else "SEG"} ${targetUrl.takeLast(45)}")

            val reqBuilder = Request.Builder().url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
            playbackHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

            val response = okhttp.newCall(reqBuilder.build()).execute()
            val out = socket.getOutputStream()
            if (!response.isSuccessful) {
                Log.e(TAG, "upstream ${response.code} for ${targetUrl.takeLast(45)}")
                out.write("HTTP/1.1 ${response.code} Upstream\r\n\r\n".toByteArray())
                response.close()
                return
            }

            if (isM3u8) {
                val bytes = response.body!!.bytes()
                response.close()
                val body = rewriteM3u8(String(bytes), targetUrl)
                Log.d(TAG, "sent ${body.size}b ${targetUrl.takeLast(45)}")
                out.write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nContent-Type: application/vnd.apple.mpegurl\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n").toByteArray())
                out.write(body)
            } else {
                // Stream segment directly — avoids buffering entire TS chunk in memory
                val body = response.body!!
                val contentLength = body.contentLength()
                val contentType = response.header("Content-Type") ?: "video/mp2t"
                
                val header = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    if (contentLength >= 0) append("Content-Length: $contentLength\r\n")
                    append("Content-Type: $contentType\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n")
                }
                out.write(header.toByteArray())
                val buf = ByteArray(65536)
                var total = 0
                body.byteStream().use { stream ->
                    var n: Int
                    while (stream.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        total += n
                    }
                }
                response.close()
                Log.d(TAG, "sent ${total}b (type=$contentType) ${targetUrl.takeLast(45)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "handle error: ${e.message}")
        } finally {
            socket.close()
        }
    }

    /**
     * Rewrite every URL line in the playlist so it routes back through this proxy.
     * Child URLs are resolved (relative/absolute-path → absolute) against the playlist's own URL.
     */
    private fun rewriteM3u8(text: String, playlistUrl: String): ByteArray {
        val base = playlistUrl.substringBefore("?").substringBeforeLast("/") + "/"
        val origin = Regex("^(https?://[^/]+)").find(playlistUrl)?.groupValues?.get(1) ?: ""
        val sb = StringBuilder()
        for (line in text.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> sb.append(line)
                trimmed.startsWith("#") -> sb.append(rewriteTagUris(line, origin, base))
                else -> sb.append(proxify(resolveUrl(trimmed, origin, base)))
            }
            sb.append("\n")
        }
        return sb.toString().toByteArray()
    }

    /** Resolve an HLS URI reference against the playlist origin/base per URL rules. */
    private fun resolveUrl(ref: String, origin: String, base: String): String = when {
        ref.startsWith("http://") || ref.startsWith("https://") -> ref
        ref.startsWith("/") -> origin + ref          // absolute path → scheme://host + path
        else -> base + ref                            // relative → playlist directory
    }

    /** Handle URI="..." inside tags like #EXT-X-KEY and #EXT-X-MEDIA */
    private fun rewriteTagUris(line: String, origin: String, base: String): String {
        val regex = Regex("URI=\"([^\"]+)\"")
        return regex.replace(line) { m ->
            "URI=\"${proxify(resolveUrl(m.groupValues[1], origin, base))}\""
        }
    }

    private fun proxify(realUrl: String): String =
        "http://127.0.0.1:$port/${URLEncoder.encode(realUrl, "UTF-8")}"

    fun stop() = serverSocket.close()
}

@OptIn(UnstableApi::class)
class WebViewDataSource(
    private val proxyPort: Int,
    private val delegate: HttpDataSource = DefaultHttpDataSource.Factory().createDataSource()
) : HttpDataSource by delegate {
    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri.toString()
        // Child URLs from a rewritten m3u8 are already proxy URLs — pass through unchanged.
        val proxied = if (uri.startsWith("http://127.0.0.1:"))
            uri
        else
            "http://127.0.0.1:$proxyPort/${URLEncoder.encode(uri, "UTF-8")}"
        return delegate.open(dataSpec.buildUpon().setUri(android.net.Uri.parse(proxied)).build())
    }

    @UnstableApi
    class Factory(private val proxyPort: Int) : DataSource.Factory {
        override fun createDataSource(): DataSource = WebViewDataSource(proxyPort)
    }
}

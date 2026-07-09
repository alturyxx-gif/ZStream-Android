package com.zstream.android.download

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStream

/**
 * For `streamType == "file"` sources: the URL already points at a finished, playable container
 * (mp4/mkv/whatever) — no HLS parsing, no remux, just stream the bytes straight to disk.
 */
object DirectFileDownloader {
    suspend fun download(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
        output: OutputStream,
        onProgress: suspend (bytesReceived: Long, totalBytes: Long?) -> Unit,
    ) {
        val request = Request.Builder().url(url).headers(downloadHeaders(headers)).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("Direct download failed: HTTP ${resp.code}")
            val body = resp.body ?: error("Empty response body")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                output.use { out ->
                    val buf = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        received += n
                        onProgress(received, total)
                    }
                }
            }
        }
    }
}

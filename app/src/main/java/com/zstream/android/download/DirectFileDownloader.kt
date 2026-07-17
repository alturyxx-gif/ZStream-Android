package com.zstream.android.download

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "DirectFileDownloader"

/** Below this, connection-setup overhead isn't worth splitting into ranged chunks. */
private const val MIN_SIZE_FOR_CHUNKING = 8L * 1024 * 1024
private const val CHUNK_COUNT = 8
private const val COPY_BUFFER_BYTES = 256 * 1024

/**
 * For `streamType == "file"` sources: the URL already points at a finished, playable container
 * (mp4/mkv/whatever) — no HLS parsing, no remux, just get the bytes to disk as fast as possible.
 *
 * Splits into [CHUNK_COUNT] concurrent Range-request downloads (same technique IDM/aria2-style
 * downloaders use to beat a single connection's per-connection throttling) when the server
 * confirms Range support and the file is big enough to be worth it, falling back to one
 * sequential stream otherwise. Each chunk lands in its own file under [workDir] first (never
 * writes into arbitrary offsets of the caller's OutputStream directly — that stream may be a
 * SAF/MediaStore-backed one that doesn't support seeking) and is only concatenated into the real
 * destination once every chunk is down, in order.
 */
object DirectFileDownloader {
    suspend fun download(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
        output: OutputStream,
        workDir: File,
        onProgress: suspend (bytesReceived: Long, totalBytes: Long?) -> Unit,
    ) {
        val probe = probeRangeSupport(client, url, headers)
        if (probe == null || probe.contentLength < MIN_SIZE_FOR_CHUNKING) {
            Log.i(TAG, "no range support or file too small (${probe?.contentLength ?: -1} bytes) — single-stream download")
            downloadSequential(client, url, headers, output, onProgress)
            return
        }

        Log.i(TAG, "range support confirmed, ${probe.contentLength / (1024 * 1024)}MB across $CHUNK_COUNT chunks")
        workDir.mkdirs()
        val total = probe.contentLength
        val chunkSize = (total + CHUNK_COUNT - 1) / CHUNK_COUNT
        val ranges = (0 until CHUNK_COUNT).map { i ->
            val start = i * chunkSize
            val end = minOf(start + chunkSize - 1, total - 1)
            start to end
        }.filter { it.first <= it.second }
        val chunkFiles = ranges.indices.map { i -> File(workDir, "chunk-%02d".format(i)) }

        val receivedTotal = AtomicLong(
            // Resume: chunk files already fully downloaded from a previous attempt count immediately.
            ranges.indices.sumOf { i ->
                val (start, end) = ranges[i]
                val expected = end - start + 1
                if (chunkFiles[i].exists() && chunkFiles[i].length() == expected) expected else 0L
            },
        )
        val progressMutex = Mutex()
        var lastReportedPct = -1
        var lastReportAtMs = 0L

        suspend fun reportChunkBytes(delta: Long) {
            val now = receivedTotal.addAndGet(delta)
            val pct = (now * 100 / total).toInt()
            val nowMs = System.currentTimeMillis()
            progressMutex.withLock {
                if (pct != lastReportedPct && (nowMs - lastReportAtMs >= 200 || pct == 100)) {
                    lastReportedPct = pct
                    lastReportAtMs = nowMs
                    onProgress(now, total)
                }
            }
        }

        coroutineScope {
            ranges.indices.map { i ->
                async(Dispatchers.IO) {
                    val (start, end) = ranges[i]
                    val dest = chunkFiles[i]
                    val expected = end - start + 1
                    if (dest.exists() && dest.length() == expected) return@async
                    downloadRangeWithRetry(client, url, headers, start, end, dest, ::reportChunkBytes)
                }
            }.awaitAll()
        }

        output.use { out ->
            for (file in chunkFiles) {
                file.inputStream().use { it.copyTo(out, bufferSize = COPY_BUFFER_BYTES) }
            }
        }
        chunkFiles.forEach { it.delete() }
        onProgress(total, total)
    }

    private data class RangeProbe(val contentLength: Long)

    /** A Range: bytes=0-0 probe rather than HEAD — some CDNs handle HEAD inconsistently (wrong/missing
     * Content-Length) but every CDN that actually supports ranged GETs answers this correctly with a
     * real 206 + Content-Range, which is the only thing that matters for chunking. */
    private fun probeRangeSupport(client: OkHttpClient, url: String, headers: Map<String, String>): RangeProbe? =
        try {
            val request = Request.Builder().url(url).headers(downloadHeaders(headers)).header("Range", "bytes=0-0").build()
            client.newCall(request).execute().use { resp ->
                if (resp.code != 206) return null
                val contentRange = resp.header("Content-Range") ?: return null
                val total = contentRange.substringAfter('/', "").toLongOrNull() ?: return null
                if (total <= 0) null else RangeProbe(total)
            }
        } catch (t: Exception) {
            Log.w(TAG, "range probe failed, falling back to sequential: ${t.message}")
            null
        }

    private suspend fun downloadRangeWithRetry(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
        start: Long,
        end: Long,
        dest: File,
        onBytes: suspend (Long) -> Unit,
    ) {
        val maxAttempts = 5
        var attempt = 0
        while (true) {
            try {
                downloadRange(client, url, headers, start, end, dest, onBytes)
                return
            } catch (t: Exception) {
                attempt++
                if (attempt >= maxAttempts) throw t
                Log.w(TAG, "chunk $start-$end failed (attempt $attempt/$maxAttempts): ${t.message}")
                dest.delete()
                delay(500L * (1L shl (attempt - 1))) // 500ms, 1s, 2s, 4s
            }
        }
    }

    private suspend fun downloadRange(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
        start: Long,
        end: Long,
        dest: File,
        onBytes: suspend (Long) -> Unit,
    ) {
        val request = Request.Builder().url(url).headers(downloadHeaders(headers)).header("Range", "bytes=$start-$end").build()
        client.newCall(request).execute().use { resp ->
            if (resp.code != 206 && resp.code != 200) error("Range download failed: HTTP ${resp.code}")
            val body = resp.body ?: error("Empty response body")
            val part = File(dest.parentFile, dest.name + ".part")
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        onBytes(n.toLong())
                    }
                }
            }
            if (!part.renameTo(dest)) part.copyTo(dest, overwrite = true).also { part.delete() }
        }
    }

    private suspend fun downloadSequential(
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
            var lastReportedPct = -1
            var lastReportAtMs = 0L
            body.byteStream().use { input ->
                output.use { out ->
                    val buf = ByteArray(COPY_BUFFER_BYTES)
                    var received = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        received += n
                        // Gated like the HLS engine's per-segment reporting — an unthrottled callback
                        // here means one DB write per 256KB read, which for a multi-GB file is
                        // thousands of synchronous Room writes competing with the download's own disk
                        // I/O for no benefit (the UI can't render updates that fast anyway).
                        val pct = total?.let { (received * 100 / it).toInt() } ?: -1
                        val nowMs = System.currentTimeMillis()
                        if (pct != lastReportedPct || nowMs - lastReportAtMs >= 200) {
                            lastReportedPct = pct
                            lastReportAtMs = nowMs
                            onProgress(received, total)
                        }
                    }
                    onProgress(received, total)
                }
            }
        }
    }
}

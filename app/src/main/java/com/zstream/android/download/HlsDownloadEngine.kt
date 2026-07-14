package com.zstream.android.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileDescriptor
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TAG = "HlsDownloadEngine"

const val DEFAULT_SEGMENT_WORKERS = 6
const val PARALLEL_MODE_SEGMENT_WORKERS = 5
const val ADAPTIVE_MAX_WORKERS_SINGLE = 14
const val ADAPTIVE_MAX_WORKERS_PARALLEL = 10
private const val ADAPTIVE_MIN_WORKERS = 3
private const val ADAPTIVE_RAMP_UP_STREAK = 15


const val MAGNOLIA_SEGMENT_WORKERS = 10
const val MAGNOLIA_PARALLEL_MODE_SEGMENT_WORKERS = 8
const val MAGNOLIA_ADAPTIVE_MAX_WORKERS_SINGLE = 24
const val MAGNOLIA_ADAPTIVE_MAX_WORKERS_PARALLEL = 16


private class AdaptiveConcurrencyController(startLimit: Int, private val maxLimit: Int) {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private var limit = startLimit.coerceIn(ADAPTIVE_MIN_WORKERS, maxLimit)
    private var active = 0
    private var cleanStreak = 0

    private suspend fun acquire() {
        while (true) {
            val granted = mutex.withLock { if (active < limit) { active++; true } else false }
            if (granted) return
            kotlinx.coroutines.delay(25)
        }
    }

    private suspend fun release() {
        mutex.withLock { active-- }
    }

    suspend fun onSegmentSuccess() {
        mutex.withLock {
            cleanStreak++
            if (cleanStreak >= ADAPTIVE_RAMP_UP_STREAK && limit < maxLimit) {
                limit++
                cleanStreak = 0
                Log.i(TAG, "adaptive concurrency: ramped up to $limit")
            }
        }
    }

    suspend fun onRateLimited() {
        mutex.withLock {
            val newLimit = (limit / 2).coerceAtLeast(ADAPTIVE_MIN_WORKERS)
            if (newLimit != limit) Log.i(TAG, "adaptive concurrency: rate limited, cut $limit -> $newLimit")
            limit = newLimit
            cleanStreak = 0
        }
    }

    suspend fun <T> withPermit(block: suspend () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            release()
        }
    }
}

data class HlsDownloadProgress(
    val segmentsDone: Int,
    val segmentsTotal: Int,
    val bytesReceived: Long,
    /** bytesReceived/segmentsDone extrapolated across segmentsTotal — an estimate, not exact, since HLS segments vary in size. */
    val estimatedTotalBytes: Long?,
)


class HlsDownloadEngine(private val client: OkHttpClient) {

    suspend fun download(
        videoPlaylistUrl: String,
        audioPlaylistUrl: String?,
        headers: Map<String, String>,
        workDir: File,
        outputFd: FileDescriptor,
        onProgress: suspend (HlsDownloadProgress) -> Unit,
        onRemuxProgress: suspend (done: Int, total: Int) -> Unit,
        /** Fired the moment a segment enters a retry/backoff loop (rate limit, throttle, transient error), and again with null once it clears -- lets the UI explain a stalled percentage in real time instead of waiting for the next segment to finish. */
        onStall: suspend (String?) -> Unit = {},
        segmentWorkers: Int = DEFAULT_SEGMENT_WORKERS,
        maxSegmentWorkers: Int = ADAPTIVE_MAX_WORKERS_SINGLE,
    ) = coroutineScope {
        workDir.mkdirs()

        val videoFetch = fetchAndParseHlsPlaylist(client, videoPlaylistUrl, headers)
        val videoPlaylist = videoFetch.playlist
        check(videoPlaylist.segments.isNotEmpty()) { "Video playlist has no segments" }
        val resolvedAudioUrl = audioPlaylistUrl ?: videoFetch.audioPlaylistUrl
        val audioPlaylist = resolvedAudioUrl?.let { fetchAndParseHlsPlaylist(client, it, headers).playlist }
        Log.i(TAG, "playlists parsed: video segments=${videoPlaylist.segments.size} fmp4=${videoPlaylist.initUri != null} " +
            "audio segments=${audioPlaylist?.segments?.size ?: 0} fmp4=${audioPlaylist?.initUri != null} " +
            "audioUrl=${resolvedAudioUrl != null}")

        val videoKeys = downloadKeys(videoPlaylist.keys, headers, workDir)
        val audioKeys = audioPlaylist?.let { downloadKeys(it.keys, headers, workDir) } ?: emptyMap()

        val videoSet = segmentFiles(videoPlaylist, workDir, "v_")
        val audioSet = audioPlaylist?.let { segmentFiles(it, workDir, "a_") }

        videoSet.initUrl?.let { url ->
            downloadPlain(url, headers, videoSet.initFile!!)
            Log.i(TAG, "video init segment downloaded: ${videoSet.initFile.length()} bytes")
        }
        audioSet?.initUrl?.let { url ->
            downloadPlain(url, headers, audioSet.initFile!!)
            Log.i(TAG, "audio init segment downloaded: ${audioSet.initFile.length()} bytes")
        }

        val videoFiles = videoSet.segments
        val audioFiles = audioSet?.segments ?: emptyList()
        val total = videoFiles.size + audioFiles.size
        var done = 0
        var bytes = 0L
        var lastReportedPct = -1
        val progressLock = Any()

        val remuxJob = async(Dispatchers.IO) {
            remux(
                videoInitFile = videoSet.initFile,
                videoSegments = videoFiles.map { it.first to it.second },
                audioInitFile = audioSet?.initFile,
                audioSegments = audioFiles.map { it.first to it.second },
                outputFd = outputFd,
                onRemuxProgress = onRemuxProgress,
            )
        }

        val controller = AdaptiveConcurrencyController(startLimit = segmentWorkers, maxLimit = maxSegmentWorkers)
        val jobs = (videoFiles + audioFiles).map { (segment, dest, key) ->
            async(Dispatchers.IO) {
                controller.withPermit {
                    if (!(dest.exists() && dest.length() > 0)) {
                        val size = downloadSegment(
                            segment.uri, headers, dest, key, videoKeys + audioKeys,
                            onRateLimited = controller::onRateLimited,
                            onStall = onStall,
                        )
                        synchronized(progressLock) { bytes += size }
                        controller.onSegmentSuccess()
                        onStall(null)
                    }
                    val (snapshot, shouldReport) = synchronized(progressLock) {
                        done++
                        val pct = if (total > 0) done * 100 / total else 0
                        val report = pct > lastReportedPct || done == total
                        if (report) lastReportedPct = pct
                        val estimatedTotal = if (done > 0) bytes * total / done else null
                        HlsDownloadProgress(done, total, bytes, estimatedTotal) to report
                    }
                    if (snapshot.segmentsDone % 10 == 0 || snapshot.segmentsDone == total) {
                        Log.i(TAG, "segments ${snapshot.segmentsDone}/${snapshot.segmentsTotal}, " +
                            "downloaded ${snapshot.bytesReceived / 1024}KB so far")
                    }
                    // Gated to one call per percentage point rather than per segment — with hundreds
                    // of segments finishing in a couple seconds, per-segment notification/DB updates
                    // get silently dropped by Android's own update-rate throttling, making progress
                    // look stuck; this keeps updates bounded to ~100 regardless of segment count.
                    if (shouldReport) onProgress(snapshot)
                }
            }
        }
        jobs.awaitAll()
        Log.i(TAG, "all segments downloaded: total ${bytes / 1024}KB across $total segments, waiting on remux")

        remuxJob.await()
        Log.i(TAG, "remux complete")
    }

    private fun downloadKeys(keys: List<HlsKey>, headers: Map<String, String>, workDir: File): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        for (key in keys) {
            if (key.method.equals("NONE", true)) continue
            val request = Request.Builder().url(key.uri).headers(downloadHeaders(headers)).build()
            val bytes = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("HLS key fetch failed: HTTP ${resp.code}")
                resp.body?.bytes() ?: error("Empty key response")
            }
            out["${key.uri}|${key.iv}"] = bytes
        }
        return out
    }

    
    private data class SegmentFileSet(
        val initUrl: String?,
        val initFile: File?,
        val segments: List<Triple<HlsSegment, File, HlsKey?>>,
    )

    private fun segmentFiles(
        playlist: HlsPlaylist,
        workDir: File,
        prefix: String,
    ): SegmentFileSet {
        val segments = playlist.segments.map { seg ->
            Triple(seg, File(workDir, "$prefix${"seg-%06d".format(seg.seq - playlist.mediaSeq)}"), seg.key)
        }
        return SegmentFileSet(
            initUrl = playlist.initUri,
            initFile = playlist.initUri?.let { File(workDir, "${prefix}init") },
            segments = segments,
        )
    }

    private suspend fun downloadPlain(url: String, headers: Map<String, String>, dest: File) {
        if (dest.exists() && dest.length() > 0) return
        dest.writeBytes(fetchWithRetry(url, headers))
    }

    private suspend fun downloadSegment(
        url: String,
        headers: Map<String, String>,
        dest: File,
        key: HlsKey?,
        keyMaterial: Map<String, ByteArray>,
        onRateLimited: suspend () -> Unit,
        onStall: suspend (String?) -> Unit = {},
    ): Long {
        val raw = fetchWithRetry(url, headers, onRateLimited = onRateLimited, onStall = onStall)
        if (raw.isEmpty()) error("Empty segment body: $url")
        val plain = if (key != null) decryptAes128(raw, keyMaterial["${key.uri}|${key.iv}"] ?: error("Missing key for segment"), key.iv) else raw
        if (plain.isEmpty()) error("Segment decrypted to 0 bytes: $url")
        val part = File(dest.parentFile, dest.name + ".part")
        part.writeBytes(plain)
        if (!part.renameTo(dest)) dest.writeBytes(plain)
        return plain.size.toLong()
    }

    private class RateLimited(val retryAfterMs: Long?) : Exception()

  
    private suspend fun fetchWithRetry(
        url: String,
        headers: Map<String, String>,
        maxAttempts: Int = 5,
        maxRateLimitAttempts: Int = 60,
        onRateLimited: (suspend () -> Unit)? = null,
        onStall: suspend (String?) -> Unit = {},
    ): ByteArray {
        var lastError: Exception? = null
        var otherAttempts = 0
        var rateLimitAttempts = 0
        var notifiedRateLimit = false
        while (true) {
            val request = Request.Builder().url(url).headers(downloadHeaders(headers)).build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (resp.code == 429) {
                        val retryAfterMs = resp.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000)
                        lastError = RateLimited(retryAfterMs)
                        return@use
                    }
                    if (resp.code in 500..599) {
                        lastError = IllegalStateException("Segment fetch failed: HTTP ${resp.code}")
                        return@use
                    }
                    if (!resp.isSuccessful) {
                        // 403 here almost always means an expired/short-TTL signed CDN URL (e.g.
                        // Nesterov) outliving its token partway through a long download -- log the
                        // code plus which attempt/URL so a failure report actually distinguishes
                        // that from a source that was just broken from the start.
                        Log.w(TAG, "Segment fetch non-2xx: HTTP ${resp.code} attempt=${otherAttempts + 1} url=$url")
                        error("Segment fetch failed: HTTP ${resp.code}")
                    }
                    val bodyBytes = resp.body?.bytes()
                    if (bodyBytes == null || bodyBytes.isEmpty()) {
                        // Some CDNs throttle silently -- a 200 OK with a 0-byte body instead of a
                        // proper 429 -- especially once a burst of real 429s has already happened.
                        // Treat it exactly like a rate limit signal (no Retry-After to honor here,
                        // so it falls back to capped exponential backoff) instead of letting an
                        // empty body propagate up and fail the whole segment/download outright.
                        lastError = RateLimited(retryAfterMs = null)
                        return@use
                    }
                    return bodyBytes
                }
            } catch (t: Exception) {
                lastError = t
            }

            val err = lastError
            if (err is RateLimited) {
                if (!notifiedRateLimit) {
                    notifiedRateLimit = true
                    onRateLimited?.invoke()
                }
                rateLimitAttempts++
                if (rateLimitAttempts >= maxRateLimitAttempts) {
                    onStall("Rate limited by server, gave up after $rateLimitAttempts attempts")
                    throw IllegalStateException("Segment rate limited after $rateLimitAttempts attempts")
                }
                onStall("Server rate-limiting downloads, retrying ($rateLimitAttempts/$maxRateLimitAttempts)…")
                val delayMs = err.retryAfterMs ?: (1000L * (1L shl minOf(rateLimitAttempts - 1, 5))).coerceAtMost(30_000L)
                kotlinx.coroutines.delay(delayMs)
            } else {
                otherAttempts++
                if (otherAttempts >= maxAttempts) {
                    onStall("Segment fetch failed after $otherAttempts attempts: ${err?.message ?: "unknown error"}")
                    throw err ?: IllegalStateException("Segment fetch failed after $otherAttempts attempts")
                }
                onStall("Segment fetch error, retrying ($otherAttempts/$maxAttempts): ${err?.message ?: "unknown error"}")
                kotlinx.coroutines.delay(500L * (1L shl (otherAttempts - 1))) // 500ms, 1s, 2s, 4s
            }
        }
    }

    private fun decryptAes128(data: ByteArray, keyBytes: ByteArray, ivHex: String): ByteArray {
        val iv = if (ivHex.isNotEmpty()) {
            hexToBytes(ivHex.removePrefix("0x").removePrefix("0X")).copyOf(16)
        } else {
            ByteArray(16) // Sequence-number-derived IV is rare in practice; zero IV covers most sources.
        }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        val decrypted = cipher.doFinal(data)
        // Strip PKCS7 padding manually since segments are fetched as one full ciphertext blob.
        val padLen = decrypted.lastOrNull()?.toInt()?.and(0xFF) ?: 0
        return if (padLen in 1..16 && padLen <= decrypted.size) decrypted.copyOf(decrypted.size - padLen) else decrypted
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte() }

    private suspend fun awaitSegmentReady(file: File) {
        kotlinx.coroutines.withTimeout(10 * 60_000L) {
            while (!(file.exists() && file.length() > 0)) {
                kotlinx.coroutines.delay(40)
            }
        }
    }

   
    private suspend fun remux(
        videoInitFile: File?,
        videoSegments: List<Pair<HlsSegment, File>>,
        audioInitFile: File?,
        audioSegments: List<Pair<HlsSegment, File>>,
        outputFd: FileDescriptor,
        onRemuxProgress: suspend (done: Int, total: Int) -> Unit,
    ) {
        videoSegments.firstOrNull()?.second?.let { awaitSegmentReady(it) }
        audioSegments.firstOrNull()?.second?.let { awaitSegmentReady(it) }
        val muxer = MediaMuxer(outputFd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            var videoMuxIndex = -1
            var audioMuxIndex = -1
            val videoInitBytes = videoInitFile?.takeIf { it.exists() && it.length() > 0 }?.readBytes()
            val audioInitBytes = audioInitFile?.takeIf { it.exists() && it.length() > 0 }?.readBytes()

            // The primary "video" segments are usually a single HLS variant with audio muxed into
            // the same container (no separate #EXT-X-MEDIA audio rendition, which this parser
            // doesn't even extract) — probe for BOTH track types here rather than assuming
            // video-only, or the downloaded audio silently gets dropped during remux.
            fun probeTracks(initBytes: ByteArray?, file: File?) {
                file ?: return
                withExtractor(initBytes, file) { ex ->
                    for (i in 0 until ex.trackCount) {
                        val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                        when {
                            mime.startsWith("video/") && videoMuxIndex == -1 -> videoMuxIndex = muxer.addTrack(ex.getTrackFormat(i))
                            mime.startsWith("audio/") && audioMuxIndex == -1 -> audioMuxIndex = muxer.addTrack(ex.getTrackFormat(i))
                        }
                    }
                }
            }
            probeTracks(videoInitBytes, videoSegments.firstOrNull()?.second)
            probeTracks(audioInitBytes, audioSegments.firstOrNull()?.second)
            muxer.start()

            Log.i(TAG, "muxer tracks registered: videoMuxIndex=$videoMuxIndex audioMuxIndex=$audioMuxIndex")
            val totalFiles = videoSegments.size + audioSegments.size
            val offsets = TrackOffsets()
            writeSegments(muxer, videoInitBytes, videoSegments, videoMuxIndex, audioMuxIndex, offsets, baseDone = 0, totalFiles, onRemuxProgress)
            writeSegments(muxer, audioInitBytes, audioSegments, videoMuxIndex, audioMuxIndex, offsets, baseDone = videoSegments.size, totalFiles, onRemuxProgress)
        } finally {
            runCatching { muxer.stop() }
                .onFailure { Log.e(TAG, "MediaMuxer stop failed: ${it.message}", it) }
            muxer.release()
        }
    }

    private class TrackOffsets {
        var videoTimeOffsetUs = 0L
        var audioTimeOffsetUs = 0L
        var videoMaxPtsUs = -1L
        var audioMaxPtsUs = -1L
    }

    /**
     * Opens [file] with MediaExtractor, prepending [initBytes] first if this track uses a
     * fragmented-MP4 init segment (see SegmentFileSet doc comment) — writes a small combined temp
     * file next to [file] since MediaExtractor only accepts a file path/descriptor, not a stream.
     */
    private fun withExtractor(initBytes: ByteArray?, file: File, block: (MediaExtractor) -> Unit) {
        val source = if (initBytes != null) {
            val combined = File(file.parentFile, file.name + ".combined")
            combined.outputStream().use { out ->
                out.write(initBytes)
                file.inputStream().use { it.copyTo(out) }
            }
            combined
        } else {
            file
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.absolutePath)
        } catch (t: Throwable) {
            Log.e(TAG, "MediaExtractor.setDataSource failed for ${file.name} " +
                "(size=${file.length()}, combinedWithInit=${initBytes != null})", t)
            throw t
        }
        try {
            block(extractor)
        } finally {
            extractor.release()
            if (source !== file) source.delete()
        }
    }

  
    private suspend fun writeSegments(
        muxer: MediaMuxer,
        initBytes: ByteArray?,
        segments: List<Pair<HlsSegment, File>>,
        videoMuxIndex: Int,
        audioMuxIndex: Int,
        offsets: TrackOffsets,
        baseDone: Int,
        totalFiles: Int,
        onRemuxProgress: suspend (done: Int, total: Int) -> Unit,
    ) {
        if (segments.isEmpty() || (videoMuxIndex == -1 && audioMuxIndex == -1)) return
        var lastReportedPct = -1
        val bufferInfo = MediaCodec.BufferInfo()
        val buffer = java.nio.ByteBuffer.allocate(8 shl 20)

        for ((index, entry) in segments.withIndex()) {
            coroutineContext.ensureActive()
            val (segment, file) = entry
            awaitSegmentReady(file)
            var sawVideo = false
            var sawAudio = false
            withExtractor(initBytes, file) { extractor ->
                // extractor's own track index -> (muxer track index, isVideo)
                val tracks = mutableMapOf<Int, Pair<Int, Boolean>>()
                for (i in 0 until extractor.trackCount) {
                    val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                    when {
                        mime.startsWith("video/") && videoMuxIndex != -1 -> { tracks[i] = videoMuxIndex to true; extractor.selectTrack(i); sawVideo = true }
                        mime.startsWith("audio/") && audioMuxIndex != -1 -> { tracks[i] = audioMuxIndex to false; extractor.selectTrack(i); sawAudio = true }
                    }
                }
                if (tracks.isEmpty()) return@withExtractor

                if (sawVideo && offsets.videoTimeOffsetUs <= offsets.videoMaxPtsUs) {
                    offsets.videoTimeOffsetUs = offsets.videoMaxPtsUs + 1
                }
                if (sawAudio && offsets.audioTimeOffsetUs <= offsets.audioMaxPtsUs) {
                    offsets.audioTimeOffsetUs = offsets.audioMaxPtsUs + 1
                }

                val firstPtsPerTrack = mutableMapOf<Int, Long>()
                while (true) {
                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    val extractorTrack = extractor.sampleTrackIndex
                    val mapped = tracks[extractorTrack]
                    if (mapped == null) {
                        extractor.advance()
                        continue
                    }
                    val (muxIndex, isVideo) = mapped
                    val pts = extractor.sampleTime
                    val firstPts = firstPtsPerTrack.getOrPut(extractorTrack) { pts }
                    val baseOffset = if (isVideo) offsets.videoTimeOffsetUs else offsets.audioTimeOffsetUs
                    val safePts = baseOffset + (pts - firstPts)
                    if (isVideo) {
                        offsets.videoMaxPtsUs = maxOf(offsets.videoMaxPtsUs, safePts)
                    } else {
                        offsets.audioMaxPtsUs = maxOf(offsets.audioMaxPtsUs, safePts)
                    }

                    var sampleOffset = 0
                    var effectiveSize = sampleSize
                    if (!isVideo && sampleSize > 7 &&
                        buffer.get(0) == 0xFF.toByte() && (buffer.get(1).toInt() and 0xF0) == 0xF0
                    ) {
                        val protectionAbsent = buffer.get(1).toInt() and 0x01
                        val headerLen = if (protectionAbsent == 1) 7 else 9
                        if (sampleSize > headerLen) {
                            sampleOffset = headerLen
                            effectiveSize = sampleSize - headerLen
                        }
                    }

                    bufferInfo.apply {
                        size = effectiveSize
                        offset = sampleOffset
                        flags = extractor.sampleFlags
                        presentationTimeUs = safePts
                    }
                    muxer.writeSampleData(muxIndex, buffer, bufferInfo)
                    extractor.advance()
                }
            }
       
            val durationUs = (segment.duration * 1_000_000).toLong()
            if (sawVideo) offsets.videoTimeOffsetUs += durationUs
            if (sawAudio) offsets.audioTimeOffsetUs += durationUs

            val done = baseDone + index + 1
            val pct = if (totalFiles > 0) done * 100 / totalFiles else 0
            if (pct > lastReportedPct || done == totalFiles) {
                lastReportedPct = pct
                onRemuxProgress(done, totalFiles)
            }
            if (index % 20 == 0 || index == segments.lastIndex) {
                Log.i(TAG, "remux: segment ${index + 1}/${segments.size}")
            }
        }
    }
}

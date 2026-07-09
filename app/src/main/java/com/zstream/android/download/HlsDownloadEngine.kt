package com.zstream.android.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileDescriptor
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TAG = "HlsDownloadEngine"
private const val SEGMENT_WORKERS = 3

data class HlsDownloadProgress(
    val segmentsDone: Int,
    val segmentsTotal: Int,
    val bytesReceived: Long,
    /** bytesReceived/segmentsDone extrapolated across segmentsTotal — an estimate, not exact, since HLS segments vary in size. */
    val estimatedTotalBytes: Long?,
)

/**
 * Downloads an HLS video (+ optional separate audio) stream to local segment files, decrypting
 * AES-128 segments as needed, then remuxes them into a single output file via MediaMuxer —
 * no re-encode, no ffmpeg, matching the desktop app's ffmpeg "-c copy" step but using Android's
 * own demux/mux APIs (MediaExtractor to read each segment's samples, MediaMuxer to write them).
 */
class HlsDownloadEngine(private val client: OkHttpClient) {

    suspend fun download(
        videoPlaylistUrl: String,
        audioPlaylistUrl: String?,
        headers: Map<String, String>,
        workDir: File,
        outputFd: FileDescriptor,
        onProgress: suspend (HlsDownloadProgress) -> Unit,
        onRemuxProgress: suspend (done: Int, total: Int) -> Unit,
    ) = coroutineScope {
        workDir.mkdirs()

        val videoFetch = fetchAndParseHlsPlaylist(client, videoPlaylistUrl, headers)
        val videoPlaylist = videoFetch.playlist
        check(videoPlaylist.segments.isNotEmpty()) { "Video playlist has no segments" }
        // A caller-supplied audioPlaylistUrl (resolved when the user picked a quality, from the
        // master playlist's #EXT-X-MEDIA audio group) takes priority; fall back to whatever
        // fetchAndParseHlsPlaylist itself discovered in case videoPlaylistUrl was a master too.
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

        val semaphore = Semaphore(SEGMENT_WORKERS)
        val jobs = (videoFiles + audioFiles).map { (segment, dest, key) ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    if (!(dest.exists() && dest.length() > 0)) {
                        val size = downloadSegment(segment.uri, headers, dest, key, videoKeys + audioKeys)
                        synchronized(progressLock) { bytes += size }
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
        Log.i(TAG, "all segments downloaded: total ${bytes / 1024}KB across $total segments, starting remux")

        remux(
            videoInitFile = videoSet.initFile,
            videoSegmentFiles = videoFiles.map { it.second },
            audioInitFile = audioSet?.initFile,
            audioSegmentFiles = audioFiles.map { it.second },
            outputFd = outputFd,
            onRemuxProgress = onRemuxProgress,
        )
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

    /**
     * [initUrl]/[initFile] are kept separate from [segments] (rather than treated as just another
     * segment) because a fragmented-MP4 (CMAF) init segment is only a bare ftyp+moov box — it has
     * no samples of its own, and each subsequent media segment is only a moof+mdat fragment that
     * MediaExtractor cannot open standalone. The init bytes get prepended to each fragment's bytes
     * at extraction time instead (see writeTrack/registerTrack).
     */
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
    ): Long {
        val raw = fetchWithRetry(url, headers)
        val plain = if (key != null) decryptAes128(raw, keyMaterial["${key.uri}|${key.iv}"] ?: error("Missing key for segment"), key.iv) else raw
        dest.writeBytes(plain)
        return plain.size.toLong()
    }

    /**
     * Segment CDNs commonly rate-limit (HTTP 429) under our parallel-worker load — retry with
     * backoff instead of failing the whole download the first time one segment gets throttled.
     */
    private suspend fun fetchWithRetry(url: String, headers: Map<String, String>, maxAttempts: Int = 5): ByteArray {
        var lastError: Exception? = null
        for (attempt in 0 until maxAttempts) {
            if (attempt > 0) kotlinx.coroutines.delay(500L * (1L shl (attempt - 1))) // 500ms, 1s, 2s, 4s
            val request = Request.Builder().url(url).headers(downloadHeaders(headers)).build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (resp.code == 429 || resp.code in 500..599) {
                        lastError = IllegalStateException("Segment fetch failed: HTTP ${resp.code}")
                        return@use
                    }
                    if (!resp.isSuccessful) error("Segment fetch failed: HTTP ${resp.code}")
                    return resp.body?.bytes() ?: error("Empty segment response")
                }
            } catch (t: Exception) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("Segment fetch failed after $maxAttempts attempts")
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

    /**
     * Demuxes each segment file with MediaExtractor and re-writes its samples into a single
     * MediaMuxer output — the Android-native equivalent of `ffmpeg -c copy`. Presentation
     * timestamps are offset per track so segments play back-to-back without resetting to zero.
     */
    private suspend fun remux(
        videoInitFile: File?,
        videoSegmentFiles: List<File>,
        audioInitFile: File?,
        audioSegmentFiles: List<File>,
        outputFd: FileDescriptor,
        onRemuxProgress: suspend (done: Int, total: Int) -> Unit,
    ) {
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
            probeTracks(videoInitBytes, videoSegmentFiles.firstOrNull())
            probeTracks(audioInitBytes, audioSegmentFiles.firstOrNull())
            muxer.start()

            Log.i(TAG, "muxer tracks registered: videoMuxIndex=$videoMuxIndex audioMuxIndex=$audioMuxIndex")
            val totalFiles = videoSegmentFiles.size + audioSegmentFiles.size
            val offsets = TrackOffsets()
            writeSegments(muxer, videoInitBytes, videoSegmentFiles, videoMuxIndex, audioMuxIndex, offsets, baseDone = 0, totalFiles, onRemuxProgress)
            writeSegments(muxer, audioInitBytes, audioSegmentFiles, videoMuxIndex, audioMuxIndex, offsets, baseDone = videoSegmentFiles.size, totalFiles, onRemuxProgress)
        } finally {
            runCatching { muxer.stop() }
                .onFailure { Log.e(TAG, "MediaMuxer stop failed: ${it.message}", it) }
            muxer.release()
        }
    }

    private class TrackOffsets {
        var videoTimeOffsetUs = 0L
        var audioTimeOffsetUs = 0L
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

    /**
     * Writes every segment file's samples into the muxer, selecting whichever of video/audio
     * tracks are actually present and registered (a segment file may contain both, one, or —
     * for a genuinely separate audio-only playlist — just audio). PTS is offset per track type
     * independently so video and audio each stay monotonic across segment boundaries even though
     * they share one file-iteration loop.
     */
    private suspend fun writeSegments(
        muxer: MediaMuxer,
        initBytes: ByteArray?,
        files: List<File>,
        videoMuxIndex: Int,
        audioMuxIndex: Int,
        offsets: TrackOffsets,
        baseDone: Int,
        totalFiles: Int,
        onRemuxProgress: suspend (done: Int, total: Int) -> Unit,
    ) {
        if (files.isEmpty() || (videoMuxIndex == -1 && audioMuxIndex == -1)) return
        var lastReportedPct = -1
        val bufferInfo = MediaCodec.BufferInfo()
        val buffer = java.nio.ByteBuffer.allocate(1 shl 20) // 1MB, grown below if a sample is bigger

        for ((index, file) in files.withIndex()) {
            withExtractor(initBytes, file) { extractor ->
                // extractor's own track index -> (muxer track index, isVideo)
                val tracks = mutableMapOf<Int, Pair<Int, Boolean>>()
                for (i in 0 until extractor.trackCount) {
                    val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                    when {
                        mime.startsWith("video/") && videoMuxIndex != -1 -> { tracks[i] = videoMuxIndex to true; extractor.selectTrack(i) }
                        mime.startsWith("audio/") && audioMuxIndex != -1 -> { tracks[i] = audioMuxIndex to false; extractor.selectTrack(i) }
                    }
                }
                if (tracks.isEmpty()) return@withExtractor

                val firstPtsPerTrack = mutableMapOf<Int, Long>()
                val maxPtsPerTrack = mutableMapOf<Int, Long>()
                while (true) {
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

                    bufferInfo.apply {
                        size = sampleSize
                        offset = 0
                        flags = extractor.sampleFlags
                        presentationTimeUs = baseOffset + (pts - firstPts)
                    }
                    muxer.writeSampleData(muxIndex, buffer, bufferInfo)
                    maxPtsPerTrack[extractorTrack] = maxOf(maxPtsPerTrack[extractorTrack] ?: 0L, bufferInfo.presentationTimeUs)
                    extractor.advance()
                }
                for ((trackIdx, mapping) in tracks) {
                    val maxPts = maxPtsPerTrack[trackIdx] ?: continue
                    if (mapping.second) offsets.videoTimeOffsetUs = maxPts else offsets.audioTimeOffsetUs = maxPts
                }
            }
            val done = baseDone + index + 1
            val pct = if (totalFiles > 0) done * 100 / totalFiles else 0
            if (pct > lastReportedPct || done == totalFiles) {
                lastReportedPct = pct
                onRemuxProgress(done, totalFiles)
            }
            if (index % 20 == 0 || index == files.lastIndex) {
                Log.i(TAG, "remux: segment ${index + 1}/${files.size}")
            }
        }
    }
}

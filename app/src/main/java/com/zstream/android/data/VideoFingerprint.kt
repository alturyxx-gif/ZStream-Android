package com.zstream.android.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.FileInputStream
import java.security.MessageDigest

object VideoFingerprint {
    private const val SAMPLE_BYTES = 64 * 1024

    fun compute(context: Context, uri: Uri, size: Long?, durationMs: Long?): String? {
        val fileSize = size?.takeIf { it > 0 } ?: return null
        val duration = durationMs ?: videoDurationMs(context, uri)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("size=$fileSize;duration=${duration ?: -1};".toByteArray())
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    val sampleSize = minOf(SAMPLE_BYTES.toLong(), fileSize).toInt()
                    listOf(0L, ((fileSize - sampleSize) / 2).coerceAtLeast(0L), (fileSize - sampleSize).coerceAtLeast(0L))
                        .forEachIndexed { index, offset ->
                            val buffer = java.nio.ByteBuffer.allocate(sampleSize)
                            channel.position(offset)
                            while (buffer.hasRemaining() && channel.read(buffer) > 0) Unit
                            digest.update("sample$index@$offset=".toByteArray())
                            digest.update(buffer.array(), 0, buffer.position())
                        }
                }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun videoDurationMs(context: Context, uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

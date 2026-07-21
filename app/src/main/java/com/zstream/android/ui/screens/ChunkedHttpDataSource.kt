package com.zstream.android.ui.screens

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource

private const val MAX_CHUNK_BYTES = 8L * 1024 * 1024

class ChunkedHttpDataSourceFactory(
    private val upstreamFactory: HttpDataSource.Factory,
    private val knownTotalLength: Long,
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        ChunkedHttpDataSource(upstreamFactory.createDataSource(), knownTotalLength)
}

private class ChunkedHttpDataSource(
    private val upstream: HttpDataSource,
    private val knownTotalLength: Long,
) : BaseDataSource(true) {

    private lateinit var baseDataSpec: DataSpec
    private var position: Long = 0
    private var bytesRemaining: Long = 0
    private var chunkEnd: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        baseDataSpec = dataSpec
        position = dataSpec.position
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (knownTotalLength > 0) {
            knownTotalLength - dataSpec.position
        } else {
            Long.MAX_VALUE
        }
        openChunk()
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    private fun openChunk() {
        val chunkBudget = minOf(bytesRemaining, MAX_CHUNK_BYTES)
        chunkEnd = position + chunkBudget
        val chunkSpec = baseDataSpec.buildUpon()
            .setPosition(position)
            .setLength(chunkBudget)
            .build()
        upstream.open(chunkSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT
        if (position >= chunkEnd) {
            upstream.close()
            openChunk()
        }
        val maxReadable = minOf(length.toLong(), chunkEnd - position).toInt()
        if (maxReadable <= 0) return C.RESULT_END_OF_INPUT
        val read = upstream.read(buffer, offset, maxReadable)
        if (read == C.RESULT_END_OF_INPUT) {
            upstream.close()
            openChunk()
            return read(buffer, offset, length)
        }
        if (read > 0) {
            position += read
            bytesRemaining -= read
            bytesTransferred(read)
        }
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        try {
            upstream.close()
        } finally {
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}

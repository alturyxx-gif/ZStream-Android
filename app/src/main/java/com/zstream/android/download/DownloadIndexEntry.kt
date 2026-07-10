package com.zstream.android.download

import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.entity.DownloadStatus

/**
 * Persisted shape for the metadata atom embedded directly into each downloaded video file (see
 * DownloadStorage's appendMetadataBox/readMetadataBox). Deliberately decoupled from DownloadEntity
 * — a future Room migration to that entity must not silently change what an old embedded box
 * deserializes into.
 */
data class DownloadIndexEntry(
    val tmdbId: String,
    val type: String,
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val sourceId: String,
    val variantId: String,
    val qualityLabel: String,
    val posterPath: String? = null,
    val filePath: String,
    val contentFingerprint: String? = null,
    val subtitlePaths: List<String>? = null,
    val finishedAt: Long? = null,
)

fun DownloadIndexEntry.toDownloadEntity(): DownloadEntity = DownloadEntity(
    tmdbId = tmdbId,
    type = type,
    title = title,
    season = season,
    episode = episode,
    episodeTitle = episodeTitle,
    sourceId = sourceId,
    variantId = variantId,
    qualityLabel = qualityLabel,
    posterPath = posterPath,
    status = DownloadStatus.DONE,
    progressPercent = 100,
    filePath = filePath,
    contentFingerprint = contentFingerprint,
    subtitlePaths = subtitlePaths,
    finishedAt = finishedAt,
)

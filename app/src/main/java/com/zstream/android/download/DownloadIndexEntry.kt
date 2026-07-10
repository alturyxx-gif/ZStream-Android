package com.zstream.android.download

import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.entity.DownloadStatus

/**
 * Persisted shape for the recovery index written to public storage (see DownloadStorage's
 * writeIndexJson/readIndexJson). Deliberately decoupled from DownloadEntity — a future Room
 * migration to that entity must not silently change what an old index file deserializes into.
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

/** Null if this row has no file yet (shouldn't happen for a DONE download, but guards defensively). */
fun DownloadEntity.toIndexEntry(): DownloadIndexEntry? {
    val path = filePath ?: return null
    return DownloadIndexEntry(
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
        filePath = path,
        contentFingerprint = contentFingerprint,
        subtitlePaths = subtitlePaths,
        finishedAt = finishedAt,
    )
}

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

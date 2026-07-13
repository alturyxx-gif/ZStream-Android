package com.zstream.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus { QUEUED, DOWNLOADING, REMUXING, PAUSED, DONE, FAILED, CANCELLED }

/**
 * Represents one download of a movie or a single episode. Downloads are always a finished,
 * standalone file under Downloads/ZStream/... (see com.zstream.android.download.DownloadStorage)
 * — there is no separate "cache then export" state, the file is written directly.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tmdbId: String,
    val type: String, // "movie" or "show"
    val title: String, // movie title, or show title for an episode
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val sourceId: String,
    val variantId: String,
    val qualityLabel: String, // e.g. "1080p HEVC", for display
    val posterPath: String? = null, // raw TMDB poster path, e.g. "/abc123.jpg"
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progressPercent: Int = 0,
    val filePath: String? = null, // display path once known (MediaStore relative path or absolute legacy path)
    val contentFingerprint: String? = null,
    val subtitlePaths: List<String>? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val segDone: Int = 0,
    val segTotal: Int = 0,
    val remuxDone: Int = 0,
    val remuxTotal: Int = 0,
    val speedBps: Long? = null,
    val bytesDownloaded: Long? = null,
    val estimatedTotalBytes: Long? = null,
    val streamUrl: String? = null,
    val streamType: String? = null,
    val audioStreamUrl: String? = null,
    val audioLanguage: String? = null,
    val headersJson: String? = null,
    val captionsJson: String? = null,
    // Null = the app's own Downloads/ZStream folder (MediaStore on API 29+, legacy File below
    // that). Non-null = a SAF tree the user picked (see DownloadDestinationBroker) -- filePath/
    // subtitlePaths are still relative paths, but resolved against this tree instead of Downloads.
    val storageTreeUri: String? = null,
)

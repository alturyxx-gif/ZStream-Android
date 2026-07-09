package com.zstream.android.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_media",
    foreignKeys = [
        ForeignKey(
            entity = LocalLibraryFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("folderId")]
)
data class LocalMediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folderId: Long,
    val documentUri: String,
    val displayName: String,
    val relativePath: String,
    val size: Long? = null,
    val durationMs: Long? = null,
    val modifiedAt: Long? = null,
    val groupTitle: String,
    val mediaKind: String,
    val season: Int? = null,
    val episode: Int? = null,
    val groupKey: String = "$mediaKind:${groupTitle.lowercase()}",
    val matchSource: String = "filename",
    val tmdbId: String? = null,
    val tmdbType: String? = null,
    val posterPath: String? = null,
    val thumbnailPath: String? = null,
    val metadataTitle: String? = null,
    val fingerprint: String? = null,
)

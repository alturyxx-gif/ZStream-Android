package com.zstream.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_file_progress")
data class LocalFileProgressEntity(
    @PrimaryKey val id: String,
    val title: String,
    val posterPath: String? = null,
    val thumbnailPath: String? = null,
    val watched: Int,
    val duration: Int,
    val updatedAt: Long = System.currentTimeMillis(),
)

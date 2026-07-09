package com.zstream.android.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "skip_segments", indices = [Index("mediaKey")])
data class SkipSegmentEntity(
    @PrimaryKey val id: String,
    val mediaKey: String,
    val segmentType: String,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val source: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

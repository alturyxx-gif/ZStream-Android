package com.zstream.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.zstream.android.data.local.entity.SkipSegmentEntity

@Dao
interface SkipSegmentDao {
    @Query("SELECT * FROM skip_segments WHERE mediaKey = :mediaKey ORDER BY startMs")
    suspend fun getForMedia(mediaKey: String): List<SkipSegmentEntity>

    @Query("DELETE FROM skip_segments WHERE mediaKey = :mediaKey")
    suspend fun deleteForMedia(mediaKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<SkipSegmentEntity>)

    @Transaction
    suspend fun replaceForMedia(mediaKey: String, segments: List<SkipSegmentEntity>) {
        deleteForMedia(mediaKey)
        if (segments.isNotEmpty()) insertAll(segments)
    }
}

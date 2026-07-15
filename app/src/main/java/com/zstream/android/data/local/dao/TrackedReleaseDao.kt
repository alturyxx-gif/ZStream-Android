package com.zstream.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zstream.android.data.local.entity.TrackedReleaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedReleaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackedReleaseEntity)

    @Query("DELETE FROM tracked_releases WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT EXISTS(SELECT 1 FROM tracked_releases WHERE key = :key)")
    fun observeTracked(key: String): Flow<Boolean>

    @Query("SELECT * FROM tracked_releases ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<TrackedReleaseEntity>>

    @Query("SELECT * FROM tracked_releases")
    suspend fun getAllSync(): List<TrackedReleaseEntity>
}

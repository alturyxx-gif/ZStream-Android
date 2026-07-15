package com.zstream.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.zstream.android.data.local.entity.TrackedReleaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedReleaseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TrackedReleaseEntity): Long

    @Query("SELECT * FROM tracked_releases WHERE ownerId = :ownerId AND `key` = :key LIMIT 1")
    suspend fun get(ownerId: String, key: String): TrackedReleaseEntity?

    @Query("DELETE FROM tracked_releases WHERE ownerId = :ownerId AND `key` = :key")
    suspend fun delete(ownerId: String, key: String): Int

    @Query("DELETE FROM tracked_releases WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM tracked_releases WHERE id = :id AND ownerId = :ownerId AND `key` = :key")
    suspend fun deleteGeneration(ownerId: String, key: String, id: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM tracked_releases WHERE ownerId = :ownerId AND `key` = :key)")
    fun observeTracked(ownerId: String, key: String): Flow<Boolean>

    @Query("SELECT * FROM tracked_releases WHERE ownerId = :ownerId ORDER BY addedAt DESC")
    fun observeAll(ownerId: String): Flow<List<TrackedReleaseEntity>>

    @Query("SELECT * FROM tracked_releases WHERE ownerId = :ownerId")
    suspend fun getAllSync(ownerId: String): List<TrackedReleaseEntity>

    @Query("SELECT COUNT(*) FROM tracked_releases")
    suspend fun countAll(): Int

    /** Returns the inserted generation id, 0 when already present, or -1 when insertion is blocked. */
    @Transaction
    suspend fun subscribe(entity: TrackedReleaseEntity, allowInsert: Boolean): Long {
        if (get(entity.ownerId, entity.key) != null) return 0
        if (!allowInsert) return -1
        return insert(entity).coerceAtLeast(0)
    }

    /** Returns 1 when now tracked, 0 when removed, or -1 when a new subscription is blocked. */
    @Transaction
    suspend fun toggle(entity: TrackedReleaseEntity, allowInsert: Boolean): Int {
        val existing = get(entity.ownerId, entity.key)
        if (existing != null) {
            deleteById(existing.id)
            return 0
        }
        if (!allowInsert) return -1
        return if (insert(entity) > 0) 1 else 0
    }
}

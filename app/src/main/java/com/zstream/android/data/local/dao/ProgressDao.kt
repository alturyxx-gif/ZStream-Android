package com.zstream.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zstream.android.data.local.entity.ProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: ProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(progress: List<ProgressEntity>)

    @Update
    suspend fun update(progress: ProgressEntity)

    @Delete
    suspend fun delete(progress: ProgressEntity)

    @Query("DELETE FROM progress WHERE tmdbId = :tmdbId")
    suspend fun deleteByTmdbId(tmdbId: String)

    @Query("SELECT * FROM progress WHERE tmdbId = :tmdbId")
    suspend fun getByTmdbId(tmdbId: String): ProgressEntity?

    @Query("SELECT * FROM progress WHERE tmdbId = :tmdbId")
    fun observeByTmdbId(tmdbId: String): Flow<ProgressEntity?>

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE type = 'movie' ORDER BY updatedAt DESC LIMIT 50")
    fun observeMovies(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE type = 'show' ORDER BY updatedAt DESC LIMIT 50")
    fun observeShows(): Flow<List<ProgressEntity>>

    @Query("DELETE FROM progress")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM progress")
    suspend fun count(): Int

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    suspend fun getAllSync(): List<ProgressEntity>
}

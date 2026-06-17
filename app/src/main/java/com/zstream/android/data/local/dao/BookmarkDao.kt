package com.zstream.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zstream.android.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookmarks: List<BookmarkEntity>)

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE tmdbId = :tmdbId")
    suspend fun deleteByTmdbId(tmdbId: String)

    @Query("SELECT * FROM bookmarks WHERE tmdbId = :tmdbId")
    suspend fun getByTmdbId(tmdbId: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE tmdbId = :tmdbId")
    fun observeByTmdbId(tmdbId: String): Flow<BookmarkEntity?>

    @Query("SELECT * FROM bookmarks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE type = 'movie' ORDER BY updatedAt DESC")
    fun observeMovies(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE type = 'show' ORDER BY updatedAt DESC")
    fun observeShows(): Flow<List<BookmarkEntity>>

    @Query("DELETE FROM bookmarks")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE tmdbId = :tmdbId)")
    suspend fun exists(tmdbId: String): Boolean

    @Query("SELECT * FROM bookmarks ORDER BY updatedAt DESC")
    suspend fun getAllSync(): List<BookmarkEntity>
}

package com.zstream.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zstream.android.data.local.entity.LocalLibraryFolderEntity
import com.zstream.android.data.local.entity.LocalMediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalLibraryDao {
    @Query("SELECT * FROM local_library_folders ORDER BY addedAt DESC")
    fun observeFolders(): Flow<List<LocalLibraryFolderEntity>>

    @Query("SELECT * FROM local_media ORDER BY groupTitle COLLATE NOCASE, season, episode, displayName COLLATE NOCASE")
    fun observeMedia(): Flow<List<LocalMediaEntity>>

    @Query("SELECT * FROM local_media WHERE id = :id")
    suspend fun getMedia(id: Long): LocalMediaEntity?

    @Query("SELECT * FROM local_library_folders WHERE id = :id")
    suspend fun getFolder(id: Long): LocalLibraryFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: LocalLibraryFolderEntity): Long

    @Update
    suspend fun updateFolder(folder: LocalLibraryFolderEntity)

    @Delete
    suspend fun deleteFolder(folder: LocalLibraryFolderEntity)

    @Insert
    suspend fun insertMedia(media: List<LocalMediaEntity>)

    @Query("DELETE FROM local_media WHERE folderId = :folderId")
    suspend fun deleteMediaForFolder(folderId: Long)

    @Transaction
    suspend fun replaceMediaForFolder(folderId: Long, media: List<LocalMediaEntity>) {
        deleteMediaForFolder(folderId)
        if (media.isNotEmpty()) insertMedia(media)
    }
}

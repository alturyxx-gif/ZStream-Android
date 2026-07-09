package com.zstream.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zstream.android.data.local.entity.LocalFileProgressEntity

@Dao
interface LocalFileProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: LocalFileProgressEntity)

    @Query("SELECT * FROM local_file_progress WHERE id = :id")
    suspend fun get(id: String): LocalFileProgressEntity?

    @Query("DELETE FROM local_file_progress WHERE id = :id")
    suspend fun delete(id: String)
}

package com.zstream.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zstream.android.data.local.entity.CertificationEntity

@Dao
interface CertificationDao {
    @Query("SELECT * FROM certifications WHERE id = :id")
    suspend fun get(id: String): CertificationEntity?

    @Query("SELECT * FROM certifications WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<CertificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CertificationEntity)
}

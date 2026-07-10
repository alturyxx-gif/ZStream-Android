package com.zstream.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zstream.android.data.local.entity.CachedEpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedEpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(episodes: List<CachedEpisodeEntity>)

    @Query("SELECT * FROM season_episodes WHERE tmdbId = :tmdbId AND season = :season ORDER BY episode ASC")
    fun observeSeason(tmdbId: String, season: Int): Flow<List<CachedEpisodeEntity>>

    @Query("SELECT * FROM season_episodes WHERE tmdbId = :tmdbId AND season = :season ORDER BY episode ASC")
    suspend fun getSeasonSync(tmdbId: String, season: Int): List<CachedEpisodeEntity>

    @Query("SELECT DISTINCT season FROM season_episodes WHERE tmdbId = :tmdbId ORDER BY season ASC")
    fun observeAvailableSeasons(tmdbId: String): Flow<List<Int>>

    @Query("SELECT DISTINCT season FROM season_episodes WHERE tmdbId = :tmdbId ORDER BY season ASC")
    suspend fun getAvailableSeasonsSync(tmdbId: String): List<Int>
}

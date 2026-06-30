package com.zstream.android.di

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerCacheModule {

    @Provides
    @Singleton
    fun providePlayerCache(@ApplicationContext context: Context): SimpleCache {
        return SimpleCache(
            java.io.File(context.cacheDir, "exoplayer-cache"),
            LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024),
            StandaloneDatabaseProvider(context),
        )
    }
}

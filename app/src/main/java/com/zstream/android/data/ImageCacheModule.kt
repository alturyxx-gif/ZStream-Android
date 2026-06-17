package com.zstream.android.data

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val TAG = "ImageCacheConfig"

/**
 * Provides a configured ImageLoader with LRU disk cache (100MB) for image caching
 * Based on requirements: Implement LRU cache with 100MB capacity limit
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageCacheModule {

    @Singleton
    @Provides
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        val maxCacheSize = 100 * 1024 * 1024L // 100MB

        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25) // Use 25% of available memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(maxCacheSize)
                    .build()
            }
            .build()
            .apply {
                Log.d(TAG, "ImageLoader configured with 100MB LRU disk cache")
            }
    }
}

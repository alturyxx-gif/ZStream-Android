package com.zstream.android.di

import android.content.Context
import com.zstream.android.data.local.AppDatabase
import com.zstream.android.data.local.dao.BookmarkDao
import com.zstream.android.data.local.dao.CachedEpisodeDao
import com.zstream.android.data.local.dao.CertificationDao
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.dao.LocalFileProgressDao
import com.zstream.android.data.local.dao.LocalLibraryDao
import com.zstream.android.data.local.dao.ProgressDao
import com.zstream.android.data.local.dao.SkipSegmentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideProgressDao(database: AppDatabase): ProgressDao {
        return database.progressDao()
    }

    @Provides
    fun provideBookmarkDao(database: AppDatabase): BookmarkDao {
        return database.bookmarkDao()
    }

    @Provides
    fun provideDownloadDao(database: AppDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    fun provideLocalLibraryDao(database: AppDatabase): LocalLibraryDao {
        return database.localLibraryDao()
    }

    @Provides
    fun provideLocalFileProgressDao(database: AppDatabase): LocalFileProgressDao {
        return database.localFileProgressDao()
    }

    @Provides
    fun provideSkipSegmentDao(database: AppDatabase): SkipSegmentDao {
        return database.skipSegmentDao()
    }

    @Provides
    fun provideCachedEpisodeDao(database: AppDatabase): CachedEpisodeDao {
        return database.cachedEpisodeDao()
    }

    @Provides
    fun provideCertificationDao(database: AppDatabase): CertificationDao {
        return database.certificationDao()
    }
}

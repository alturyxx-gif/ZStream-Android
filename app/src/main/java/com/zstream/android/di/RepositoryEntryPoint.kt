package com.zstream.android.di

import com.zstream.android.data.BookmarkRepository
import com.zstream.android.data.TmdbRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {
    fun bookmarkRepository(): BookmarkRepository
    fun tmdbRepository(): TmdbRepository
}

package com.zstream.android.di

import com.zstream.android.data.BookmarkRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {
    fun bookmarkRepository(): BookmarkRepository
}

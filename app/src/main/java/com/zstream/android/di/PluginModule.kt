package com.zstream.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PluginDataStore

@Module
@InstallIn(SingletonComponent::class)
object PluginModule {

    /**
     * Single DataStore<Preferences> instance shared by PluginManager and SourceOrderStore.
     * Stored in "plugin_prefs" — separate from the app's "app_settings" settings store.
     */
    @Provides
    @Singleton
    @PluginDataStore
    fun providePluginDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("plugin_prefs") }
    )
}

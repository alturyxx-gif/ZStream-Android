package com.zstream.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import org.conscrypt.Conscrypt
import java.security.Security

@HiltAndroidApp
class ZStreamApp : Application(), ImageLoaderFactory {
    @javax.inject.Inject lateinit var traktRepository: com.zstream.android.data.TraktRepository
    @javax.inject.Inject lateinit var releaseUpdateManager: com.zstream.android.data.adb.ReleaseUpdateManager

    override fun onCreate() {
        super.onCreate()
        // registers modern TLS provider for Android 7 (API 24) which lacks ISRG Root X1;
        // no-op on API 25+ where the system trust store already has it
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        traktRepository.start()
        releaseUpdateManager.start()
    }
    
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ImageLoaderEntryPoint {
        fun imageLoader(): ImageLoader
    }

    override fun newImageLoader(): ImageLoader {
        return EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java).imageLoader()
    }
}

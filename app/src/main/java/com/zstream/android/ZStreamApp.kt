package com.zstream.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class ZStreamApp : Application(), ImageLoaderFactory {
    @javax.inject.Inject lateinit var traktRepository: com.zstream.android.data.TraktRepository
    @javax.inject.Inject lateinit var releaseUpdateManager: com.zstream.android.data.adb.ReleaseUpdateManager
    @javax.inject.Inject lateinit var rybbitAnalytics: com.zstream.android.data.RybbitAnalytics

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        CrashLog.breadcrumb("App", "onCreate start")
        traktRepository.start()
        CrashLog.breadcrumb("App", "traktRepository.start() done")
        releaseUpdateManager.start()
        CrashLog.breadcrumb("App", "releaseUpdateManager.start() done")
        rybbitAnalytics.trackEvent("app_open")
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

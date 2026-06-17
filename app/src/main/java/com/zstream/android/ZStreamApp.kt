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
    
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ImageLoaderEntryPoint {
        fun imageLoader(): ImageLoader
    }

    override fun newImageLoader(): ImageLoader {
        return EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java).imageLoader()
    }
}

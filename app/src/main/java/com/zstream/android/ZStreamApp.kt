package com.zstream.android

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class ZStreamApp : Application(), ImageLoaderFactory {
    @javax.inject.Inject lateinit var traktRepository: com.zstream.android.data.TraktRepository
    @javax.inject.Inject lateinit var releaseUpdateManager: com.zstream.android.data.adb.ReleaseUpdateManager
    @javax.inject.Inject lateinit var releaseNotifyManager: com.zstream.android.data.ReleaseNotifyManager
    @javax.inject.Inject lateinit var rybbitAnalytics: com.zstream.android.data.RybbitAnalytics

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        CrashLog.breadcrumb("App", "onCreate start")
        traktRepository.start()
        CrashLog.breadcrumb("App", "traktRepository.start() done")
        releaseUpdateManager.start()
        CrashLog.breadcrumb("App", "releaseUpdateManager.start() done")
        // Reconcile persisted subscriptions after a crash/process kill. An empty check is one-shot;
        // the worker only re-arms itself while tracked rows still exist.
        releaseNotifyManager.ensureStarted()
        CrashLog.breadcrumb("App", "releaseNotifyManager.ensureStarted() done")
        rybbitAnalytics.trackEvent("app_open")

        // Subscribes every install to the shared broadcast topic so the backend can push an
        // announcement to all users by sending a single FCM message to this topic.
        FirebaseMessaging.getInstance().subscribeToTopic(com.zstream.android.data.BroadcastMessagingService.BROADCAST_TOPIC)
            .addOnFailureListener { e -> Log.w("ZStreamApp", "Failed to subscribe to broadcast topic", e) }
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

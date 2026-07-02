package com.zstream.android.data

import android.content.Context
import android.util.Log
import coil.EventListener
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.zstream.android.di.TlsHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

private const val TAG = "ZSTREAM_IMG"

@Module
@InstallIn(SingletonComponent::class)
object ImageCacheModule {

    @Singleton
    @Provides
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        val maxCacheSize = 100 * 1024 * 1024L // 100MB

        val loggingClient = OkHttpClient.Builder()
            .apply {
                val tls = TlsHelper.build()
                sslSocketFactory(tls.socketFactory, tls.trustManager)
            }
            .addInterceptor { chain ->
                val req = chain.request()
                Log.d(TAG, "→ OkHttp request: ${req.url}")
                try {
                    val resp = chain.proceed(req)
                    Log.d(TAG, "← OkHttp response: ${resp.code} for ${req.url}")
                    resp
                } catch (e: Exception) {
                    Log.e(TAG, "✗ OkHttp FAILED for ${req.url}: ${e.javaClass.simpleName}: ${e.message}", e)
                    throw e
                }
            }
            .build()

        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(maxCacheSize)
                    .build()
            }
            .okHttpClient(loggingClient)
            .eventListener(object : EventListener {
                override fun onStart(request: ImageRequest) {
                    Log.d(TAG, "onStart: ${request.data}")
                }
                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    Log.d(TAG, "onSuccess: ${request.data} dataSource=${result.dataSource}")
                }
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    Log.e(TAG, "onError: ${request.data} throwable=${result.throwable.javaClass.simpleName}: ${result.throwable.message}", result.throwable)
                }
                override fun onCancel(request: ImageRequest) {
                    Log.d(TAG, "onCancel: ${request.data}")
                }
            })
            .build()
            .also { Log.d(TAG, "ImageLoader configured with dedicated OkHttpClient + logging") }
    }
}

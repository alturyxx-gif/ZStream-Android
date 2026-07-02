package com.zstream.android.di

import android.content.Context
import com.zstream.android.BuildConfig
import com.zstream.android.Urls
import com.zstream.android.data.remote.BackendApi
import com.zstream.android.data.remote.TmdbApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.Cache
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class TmdbRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class BackendRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun okHttp(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .apply {
            val tls = TlsHelper.build()
            sslSocketFactory(tls.socketFactory, tls.trustManager)
        }
        .cache(Cache(context.cacheDir.resolve("http_cache"), 100L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .addInterceptor { chain ->
            val req = chain.request()
            try {
                chain.proceed(req)
            } catch (e: Exception) {
                android.util.Log.e("ZSTREAM_NET", "✗ OkHttp FAILED: ${req.url}: ${e.javaClass.simpleName}: ${e.message}", e)
                throw e
            }
        }
        .build()

    @Provides @Singleton @TmdbRetrofit
    fun tmdbRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(Urls.TMDB_BASE)
        .client(client.newBuilder().addInterceptor { chain ->
            val key = TmdbTokenCache.token ?: BuildConfig.TMDB_API_KEY
            val original = chain.request()
            val url = original.url.newBuilder().addQueryParameter("api_key", key).build()
            chain.proceed(original.newBuilder().url(url).build())
        }.build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun tmdbApi(@TmdbRetrofit retrofit: Retrofit): TmdbApi = retrofit.create(TmdbApi::class.java)

    @Provides @Singleton @BackendRetrofit
    fun backendRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(Urls.BACKEND)
        .client(client.newBuilder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .addInterceptor { chain ->
                val resp = chain.proceed(chain.request())
                val body = resp.body?.string() ?: ""
                android.util.Log.d("BackendRaw", "${chain.request().method} ${chain.request().url} -> ${resp.code}\n$body")
                resp.newBuilder().body(okhttp3.ResponseBody.create(resp.body?.contentType(), body)).build()
            }
            .build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun backendApi(@BackendRetrofit retrofit: Retrofit): BackendApi = retrofit.create(BackendApi::class.java)
}

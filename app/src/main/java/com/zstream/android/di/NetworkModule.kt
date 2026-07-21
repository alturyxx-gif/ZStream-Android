package com.zstream.android.di

import android.content.Context
import com.zstream.android.BuildConfig
import com.zstream.android.Urls
import com.zstream.android.data.BackendConfig
import com.zstream.android.data.remote.BackendApi
import com.zstream.android.data.remote.ImdbApi
import com.zstream.android.data.remote.RybbitApi
import com.zstream.android.data.remote.ShortsApi
import com.zstream.android.data.remote.TmdbApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class TmdbRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class BackendRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ImdbRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class RybbitRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ShortsRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun okHttp(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(context.cacheDir.resolve("http_cache"), 100L * 1024 * 1024))
        // Default OkHttp caps concurrent requests to the SAME host at 5 (Dispatcher.maxRequestsPerHost)
        // regardless of how many coroutines are "allowed" to run by our own AdaptiveConcurrencyController
        // — every HLS/segment download target is one host, so without this every download tier above
        // segmentWorkers=5 was silently throttled back down to 5 real concurrent connections by OkHttp
        // itself. Raised well above the highest configured tier (24) with headroom for future tiers.
        .dispatcher(Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 48
        })
        // Matches maxRequestsPerHost so a full-concurrency download doesn't spend most of its time
        // opening fresh TCP+TLS connections instead of reusing pooled ones (default pool is only 5).
        .connectionPool(ConnectionPool(48, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
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
    fun backendRetrofit(client: OkHttpClient, backendConfig: BackendConfig): Retrofit = Retrofit.Builder()
        .baseUrl(Urls.BACKEND)
        .client(client.newBuilder()
            .apply {
                // Retrofit's baseUrl is fixed at construction time, but the backend host is a
                // user-configurable setting (see BackendConfig) that can change without an app
                // restart -- so rewrite each outgoing request to the currently-configured host
                // here instead. Requests are built by Retrofit relative to the default baseUrl
                // (which has an empty "/" path), so the custom host's own path prefix (if any)
                // just needs the original request's already-relative path appended to it.
                addInterceptor { chain ->
                    val original = chain.request()
                    val custom = backendConfig.customUrl?.toHttpUrlOrNull()
                    if (custom == null) {
                        chain.proceed(original)
                    } else {
                        val newUrl = custom.newBuilder()
                            .encodedPath(custom.encodedPath.trimEnd('/') + original.url.encodedPath)
                            .encodedQuery(original.url.encodedQuery)
                            .build()
                        chain.proceed(original.newBuilder().url(newUrl).build())
                    }
                }
            }
            .apply {
                // Both interceptors log full request/response bodies -- which include the
                // session bearer token and user settings (febbox/tidb/debrid/trakt keys).
                // Debug-only: a release build must never write these to logcat, and the raw
                // dumper also fully buffers every response body into memory just to log it.
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                    addInterceptor { chain ->
                        val resp = chain.proceed(chain.request())
                        val body = resp.body?.string() ?: ""
                        android.util.Log.d("BackendRaw", "${chain.request().method} ${chain.request().url} -> ${resp.code}\n$body")
                        resp.newBuilder().body(okhttp3.ResponseBody.create(resp.body?.contentType(), body)).build()
                    }
                }
            }
            .build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun backendApi(@BackendRetrofit retrofit: Retrofit): BackendApi = retrofit.create(BackendApi::class.java)

    @Provides @Singleton @ImdbRetrofit
    fun imdbRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(Urls.IMDB_GRAPHQL)
        .client(client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                chain.proceed(req)
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                }
            }
            .build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun imdbApi(@ImdbRetrofit retrofit: Retrofit): ImdbApi = retrofit.create(ImdbApi::class.java)

    @Provides @Singleton @RybbitRetrofit
    fun rybbitRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(Urls.RYBBIT_BASE)
        .client(client.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun rybbitApi(@RybbitRetrofit retrofit: Retrofit): RybbitApi = retrofit.create(RybbitApi::class.java)

    @Provides @Singleton @ShortsRetrofit
    fun shortsRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(Urls.CYNTHIA_BASE)
        .client(client.newBuilder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun shortsApi(@ShortsRetrofit retrofit: Retrofit): ShortsApi = retrofit.create(ShortsApi::class.java)
}

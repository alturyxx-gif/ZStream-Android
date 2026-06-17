package com.zstream.android.di

import com.zstream.android.BuildConfig
import com.zstream.android.Urls
import com.zstream.android.data.remote.BackendApi
import com.zstream.android.data.remote.TmdbApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class TmdbRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class BackendRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
        .build()

    @Provides @Singleton @TmdbRetrofit
    fun tmdbRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(Urls.TMDB_BASE)
        .client(client.newBuilder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("Authorization", "Bearer ${BuildConfig.TMDB_TOKEN}").build())
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

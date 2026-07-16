package com.zstream.android.di

import com.zstream.android.data.TmdbRepository
import com.zstream.android.data.remote.TmdbApi
import com.zstream.android.data.remote.createHttpClient
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val sharedModule = module {
    single { createHttpClient() }
    single { TmdbApi(get()) }
    singleOf(::TmdbRepository)
}

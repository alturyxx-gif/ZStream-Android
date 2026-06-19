package com.zstream.android.di

object TmdbTokenCache {
    @Volatile
    var token: String? = null
}

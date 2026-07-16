package com.zstream.android.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * No expect/actual engine needed: androidMain only has ktor-client-okhttp on its classpath and
 * iosMain only has ktor-client-darwin, so Ktor's engine auto-discovery resolves the right one
 * per-platform from this single commonMain call site.
 */
fun createHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(Logging) {
        level = LogLevel.NONE
    }
}

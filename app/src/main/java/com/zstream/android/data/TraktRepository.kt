package com.zstream.android.data

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.zstream.android.BuildConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.zstream.android.data.local.dao.BookmarkDao
import com.zstream.android.data.local.dao.ProgressDao
import com.zstream.android.data.local.entity.BookmarkEntity
import com.zstream.android.data.local.entity.ProgressEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

private val Context.traktStore by preferencesDataStore("trakt")

// Credentials come from BuildConfig (trakt.client_id / trakt.client_secret in local.properties,
// or TRAKT_CLIENT_ID / TRAKT_CLIENT_SECRET env vars in CI) -- never hardcoded, this is a public
// repo. Empty values just make Trakt connect fail; the rest of the app is unaffected.
private object TraktCredentials {
    val CLIENT_ID = BuildConfig.TRAKT_CLIENT_ID
    val CLIENT_SECRET = BuildConfig.TRAKT_CLIENT_SECRET
}

data class TraktState(
    val connected: Boolean = false,
    val name: String? = null,
    val username: String? = null,
    val avatar: String? = null,
    val syncing: Boolean = false,
    val activationCode: String? = null,
    val lastError: String? = null,
)

@Singleton
class TraktRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookmarkDao: BookmarkDao,
    private val progressDao: ProgressDao,
    private val tmdbRepository: TmdbRepository,
) {
    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val expiresKey = longPreferencesKey("expires_at")
    private val profileKey = stringPreferencesKey("profile")
    private val playbackImportedKey = booleanPreferencesKey("playback_imported")
    private val gson = Gson()
    private val http = OkHttpClient()
    private val requestMutex = Mutex()
    // A stray exception here (expired token, DNS hiccup, non-2xx Trakt response) must never take
    // down the whole app -- this scope lives for the app's lifetime, outside any UI lifecycle, so
    // an uncaught exception on it kills the process instead of just failing a screen.
    private val exceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
        android.util.Log.e("TraktRepository", "Unhandled error in background sync", error)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val _state = MutableStateFlow(TraktState())
    val state: StateFlow<TraktState> = _state
    private var lastRequestAt = 0L
    private var lastScrobblePlaying = false
    private var lastScrobbleAt = 0L
    private var authorizationJob: Job? = null

    init {
        scope.launch {
            val prefs = context.traktStore.data.first()
            if (prefs[accessKey] != null) _state.value = TraktState(connected = true)
            prefs[profileKey]?.let { profile -> applyProfile(JsonParser.parseString(profile).asJsonObject) }
        }
    }

    fun start() {
        scope.launch {
            while (true) {
                if (isAuthenticated()) syncWatchlist()
                delay(5 * 60_000L)
            }
        }
        scope.launch {
            if (isAuthenticated()) {
                runCatching {
                    pullHistory()
                    pullPlaybackProgress()
                    importPlaybackProgressOnce()
                }.onFailure { android.util.Log.w("TraktRepository", "Initial Trakt pull failed", it) }
            }
            while (true) {
                delay(15 * 60_000L)
                if (isAuthenticated()) syncHistory()
            }
        }
    }

    suspend fun beginDeviceAuthorization(): String = try {
        val body = JsonObject().apply { addProperty("client_id", TraktCredentials.CLIENT_ID) }
        val request = Request.Builder().url("https://api.trakt.tv/oauth/device/code")
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()
        val authorization = withContext(Dispatchers.IO) { http.newCall(request).execute() }.use {
            if (!it.isSuccessful) throw IllegalStateException("Trakt authorization failed (${it.code})")
            gson.fromJson(it.body?.string().orEmpty(), DeviceAuthorization::class.java)
        }
        _state.value = _state.value.copy(syncing = true, activationCode = authorization.userCode, lastError = null)
        authorizationJob?.cancel()
        authorizationJob = scope.launch { pollDeviceAuthorization(authorization) }
        authorization.verificationUrl
    } catch (error: Exception) {
        _state.value = _state.value.copy(syncing = false, activationCode = null, lastError = error.message)
        throw error
    }

    private suspend fun completeAuthorization(token: TokenResponse) {
        context.traktStore.edit {
            it[accessKey] = token.accessToken
            it[refreshKey] = token.refreshToken
            it[expiresKey] = System.currentTimeMillis() + token.expiresIn * 1000
        }
        _state.value = TraktState(connected = true, syncing = true)
        runCatching { api("/users/settings/full").asJsonObject }
            .onSuccess { profile ->
                context.traktStore.edit { it[profileKey] = gson.toJson(profile) }
                applyProfile(profile)
            }
            .onFailure { _state.value = _state.value.copy(syncing = false, lastError = it.message) }
        syncWatchlist()
        syncHistory()
        importPlaybackProgressOnce()
    }

    suspend fun disconnect() {
        authorizationJob?.cancel()
        context.traktStore.edit { it.clear() }
        _state.value = TraktState()
    }

    suspend fun exportSession(): TraktSessionExport? {
        val prefs = context.traktStore.data.first()
        val accessToken = prefs[accessKey] ?: return null
        val refreshToken = prefs[refreshKey] ?: return null
        return TraktSessionExport(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = prefs[expiresKey] ?: 0L,
            profileJson = prefs[profileKey],
            playbackImported = prefs[playbackImportedKey] ?: false,
        )
    }

    suspend fun importSession(session: TraktSessionExport) {
        authorizationJob?.cancel()
        context.traktStore.edit {
            it[accessKey] = session.accessToken
            it[refreshKey] = session.refreshToken
            it[expiresKey] = session.expiresAt
            if (session.profileJson != null) it[profileKey] = session.profileJson
            it[playbackImportedKey] = session.playbackImported
        }
        session.profileJson
            ?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
            ?.let(::applyProfile)
            ?: run { _state.value = TraktState(connected = true) }
        syncWatchlist()
        syncHistory()
    }

    fun updateWatchlist(bookmark: BookmarkEntity, add: Boolean) {
        scope.launch {
            if (!isAuthenticated()) return@launch
            runCatching {
                api(if (add) "/sync/watchlist" else "/sync/watchlist/remove", "POST", mediaPayload(bookmark.type, bookmark.tmdbId))
            }.onFailure { _state.value = _state.value.copy(lastError = it.message) }
        }
    }

    suspend fun syncWatchlist() = sync("Watchlist sync failed") {
        bookmarkDao.getAllSync().forEach { bookmark ->
            api("/sync/watchlist", "POST", mediaPayload(bookmark.type, bookmark.tmdbId))
        }
        val updates = mutableListOf<BookmarkEntity>()
        api("/sync/watchlist?limit=100").asJsonArray.forEach { item ->
            val obj = item.asJsonObject
            val media = obj.getAsJsonObject("movie") ?: obj.getAsJsonObject("show") ?: return@forEach
            val id = media.getAsJsonObject("ids")?.get("tmdb")?.asString ?: return@forEach
            val existing = bookmarkDao.getByTmdbId(id)
            if (existing?.posterPath == null) {
                val type = if (obj.has("movie")) "movie" else "show"
                val metadata = tmdbMetadata(type, id)
                updates += existing?.copy(
                    title = metadata?.first ?: existing.title,
                    posterPath = metadata?.second,
                ) ?: BookmarkEntity(
                    tmdbId = id,
                    title = metadata?.first ?: media.get("title")?.asString.orEmpty(),
                    year = media.get("year")?.asInt,
                    type = type,
                    posterPath = metadata?.second,
                )
            }
        }
        if (updates.isNotEmpty()) bookmarkDao.insertAll(updates)
    }

    suspend fun syncHistory() = sync("History sync failed") {
        pullHistory()
        pullPlaybackProgress()
        progressDao.getAllSync().filter { it.duration > 0 && it.watched.toDouble() / it.duration >= .25 }.forEach { progress ->
            buildTraktHistoryPayload(progress)?.let { api("/sync/history", "POST", it) }
        }
    }

    private suspend fun importPlaybackProgressOnce() {
        if (context.traktStore.data.first()[playbackImportedKey] == true) return
        progressDao.getAllSync().forEach { progress ->
            val percent = traktPlaybackPercent(progress) ?: return@forEach
            api(
                "/scrobble/pause",
                "POST",
                scrobblePayload(progress.type, progress.tmdbId, progress.seasonNumber, progress.episodeNumber, percent),
            )
        }
        context.traktStore.edit { it[playbackImportedKey] = true }
    }

    suspend fun pullHistory() {
        if (!isAuthenticated()) return
        api("/users/me/history?limit=100").asJsonArray.forEach { element ->
            val item = element.asJsonObject
            val movie = item.getAsJsonObject("movie")
            val episode = item.getAsJsonObject("episode")
            val show = item.getAsJsonObject("show")
            val media = movie ?: show ?: return@forEach
            val showId = media.getAsJsonObject("ids")?.get("tmdb")?.asString ?: return@forEach
            val season = episode?.get("season")?.asInt
            val number = episode?.get("number")?.asInt
            val id = ProgressEntity.computeId(showId, season, number)
            val existing = progressDao.getAllByTmdbId(showId).firstOrNull { it.id == id }
            if (existing?.posterPath == null) {
                val type = if (movie != null) "movie" else "show"
                val metadata = tmdbMetadata(type, showId)
                progressDao.insert(existing?.copy(
                    title = metadata?.first ?: existing.title,
                    posterPath = metadata?.second,
                ) ?: ProgressEntity(
                    id = id,
                    tmdbId = showId,
                    title = metadata?.first ?: media.get("title")?.asString.orEmpty(),
                    year = media.get("year")?.asInt,
                    type = type,
                    watched = 0,
                    duration = 1,
                    updatedAt = item.get("watched_at")?.asString?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: System.currentTimeMillis(),
                    episodeId = episode?.getAsJsonObject("ids")?.get("tmdb")?.asString,
                    seasonNumber = season,
                    episodeNumber = number,
                    posterPath = metadata?.second,
                ))
            }
        }
    }

    private suspend fun pullPlaybackProgress() {
        if (!isAuthenticated()) return
        api("/sync/playback").asJsonArray.forEach { element ->
            val item = element.asJsonObject
            val movie = item.getAsJsonObject("movie")
            val episode = item.getAsJsonObject("episode")
            val show = item.getAsJsonObject("show")
            val media = movie ?: show ?: return@forEach
            val tmdbId = media.getAsJsonObject("ids")?.get("tmdb")?.asString ?: return@forEach
            val season = episode?.get("season")?.asInt
            val number = episode?.get("number")?.asInt
            val id = ProgressEntity.computeId(tmdbId, season, number)
            val existing = progressDao.getAllByTmdbId(tmdbId).firstOrNull { it.id == id }

            // ZStream progress has real seconds; Trakt history placeholders use 0/1.
            if (existing != null && existing.watched > 0 && existing.duration > 1) return@forEach

            val progress = item.get("progress")?.asDouble?.coerceIn(0.0, 100.0) ?: return@forEach
            if (progress <= 0.0 || progress >= 95.0) return@forEach
            val type = if (movie != null) "movie" else "show"
            val metadata = tmdbMetadata(type, tmdbId)
            progressDao.insert(existing?.copy(
                title = metadata?.first ?: existing.title,
                posterPath = metadata?.second ?: existing.posterPath,
                watched = (progress * 100).roundToInt(),
                duration = 10_000,
                updatedAt = item.get("paused_at")?.asString?.let {
                    runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
                } ?: System.currentTimeMillis(),
            ) ?: ProgressEntity(
                id = id,
                tmdbId = tmdbId,
                title = metadata?.first ?: media.get("title")?.asString.orEmpty(),
                year = media.get("year")?.asInt,
                posterPath = metadata?.second,
                type = type,
                watched = (progress * 100).roundToInt(),
                duration = 10_000,
                updatedAt = item.get("paused_at")?.asString?.let {
                    runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
                } ?: System.currentTimeMillis(),
                episodeId = episode?.getAsJsonObject("ids")?.get("tmdb")?.asString,
                seasonNumber = season,
                episodeNumber = number,
            ))
        }
    }

    private suspend fun tmdbMetadata(type: String, tmdbId: String): Pair<String, String?>? = runCatching {
        val id = tmdbId.toInt()
        if (type == "movie") tmdbRepository.movieDetail(id).let { it.title to it.posterPath }
        else tmdbRepository.tvDetail(id).let { it.name to it.posterPath }
    }.onFailure {
        android.util.Log.w("TraktRepository", "Failed to fetch TMDB poster for $type $tmdbId", it)
    }.getOrNull()

    fun reportPlayback(type: String, tmdbId: String, season: Int?, episode: Int?, playing: Boolean, positionMs: Long, durationMs: Long) {
        if (!_state.value.connected || durationMs <= 0) return
        val now = System.currentTimeMillis()
        val action = when {
            playing && !lastScrobblePlaying -> "start"
            !playing && lastScrobblePlaying -> "pause"
            playing && now - lastScrobbleAt >= 10_000 -> "start"
            else -> return
        }
        lastScrobblePlaying = playing
        lastScrobbleAt = now
        scope.launch {
            runCatching { api("/scrobble/$action", "POST", scrobblePayload(type, tmdbId, season, episode, positionMs * 100.0 / durationMs)) }
        }
    }

    fun stopPlayback(type: String, tmdbId: String, season: Int?, episode: Int?, positionMs: Long, durationMs: Long) {
        if (!_state.value.connected || durationMs <= 0) return
        lastScrobblePlaying = false
        scope.launch { runCatching { api("/scrobble/stop", "POST", scrobblePayload(type, tmdbId, season, episode, positionMs * 100.0 / durationMs)) } }
    }

    private suspend fun <T> sync(message: String, block: suspend () -> T) {
        if (!isAuthenticated()) return
        _state.value = _state.value.copy(syncing = true, lastError = null)
        runCatching { block() }
            .onFailure { _state.value = _state.value.copy(lastError = it.message ?: message) }
        _state.value = _state.value.copy(syncing = false)
    }

    private suspend fun api(path: String, method: String = "GET", json: JsonObject? = null): com.google.gson.JsonElement = requestMutex.withLock {
        val wait = 500 - (System.currentTimeMillis() - lastRequestAt)
        if (wait > 0) delay(wait)
        val prefs = context.traktStore.data.first()
        var token = prefs[accessKey] ?: throw IllegalStateException("Trakt is not connected")
        if ((prefs[expiresKey] ?: 0) < System.currentTimeMillis() + 60_000) token = refresh()
        fun request() = Request.Builder().url("https://api.trakt.tv$path")
            .header("Authorization", "Bearer $token")
            .header("trakt-api-version", "2")
            .header("trakt-api-key", TraktCredentials.CLIENT_ID)
            .method(method, if (method == "GET") null else gson.toJson(json ?: JsonObject()).toRequestBody("application/json".toMediaType()))
            .build()
        var response = withContext(Dispatchers.IO) { http.newCall(request()).execute() }
        lastRequestAt = System.currentTimeMillis()
        if (response.code == 429) {
            val retrySeconds = response.header("Retry-After")?.toLongOrNull() ?: 1
            response.close()
            delay(retrySeconds * 1000)
            response = withContext(Dispatchers.IO) { http.newCall(request()).execute() }
        }
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw IllegalStateException("Trakt request failed (${it.code})")
            if (body.isBlank()) JsonObject() else JsonParser.parseString(body)
        }
    }

    private suspend fun refresh(): String = try {
        val prefs = context.traktStore.data.first()
        val token = refreshToken(prefs[refreshKey] ?: throw IllegalStateException("Missing Trakt refresh token"))
        context.traktStore.edit {
            it[accessKey] = token.accessToken
            it[refreshKey] = token.refreshToken
            it[expiresKey] = System.currentTimeMillis() + token.expiresIn * 1000
        }
        token.accessToken
    } catch (error: Exception) {
        context.traktStore.edit { it.clear() }
        _state.value = TraktState(lastError = "Trakt session expired")
        throw error
    }

    private fun mediaPayload(type: String, tmdbId: String) = JsonObject().apply {
        val item = JsonObject().apply {
            add("ids", JsonObject().apply { addProperty("tmdb", tmdbId.toIntOrNull()) })
        }
        add(if (type == "movie") "movies" else "shows", com.google.gson.JsonArray().apply { add(item) })
    }

    private fun scrobblePayload(type: String, tmdbId: String, season: Int?, episode: Int?, percent: Double) = JsonObject().apply {
        if (type == "movie") add("movie", JsonObject().apply {
            add("ids", JsonObject().apply { addProperty("tmdb", tmdbId.toIntOrNull()) })
        }) else {
            add("show", JsonObject().apply { add("ids", JsonObject().apply { addProperty("tmdb", tmdbId.toIntOrNull()) }) })
            add("episode", JsonObject().apply {
                addProperty("season", season)
                addProperty("number", episode)
            })
        }
        addProperty("progress", percent.coerceIn(0.0, 100.0))
    }

    private suspend fun isAuthenticated() = context.traktStore.data.first()[accessKey] != null
    private suspend fun refreshToken(refreshToken: String): TokenResponse {
        val body = JsonObject().apply {
            addProperty("refresh_token", refreshToken)
            addProperty("client_id", TraktCredentials.CLIENT_ID)
            addProperty("client_secret", TraktCredentials.CLIENT_SECRET)
            addProperty("redirect_uri", REDIRECT_URI)
            addProperty("grant_type", "refresh_token")
        }
        val request = Request.Builder().url("https://api.trakt.tv/oauth/token")
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()
        return withContext(Dispatchers.IO) { http.newCall(request).execute() }.use {
            val responseBody = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw IllegalStateException("Trakt authorization failed (${it.code})")
            gson.fromJson(responseBody, TokenResponse::class.java)
        }
    }

    private suspend fun pollDeviceAuthorization(authorization: DeviceAuthorization) {
        var intervalSeconds = authorization.interval.coerceAtLeast(1)
        val expiresAt = SystemClock.elapsedRealtime() + authorization.expiresIn * 1000L
        try {
            while (SystemClock.elapsedRealtime() < expiresAt) {
                delay(intervalSeconds * 1000L)
                val body = JsonObject().apply {
                    addProperty("code", authorization.deviceCode)
                    addProperty("client_id", TraktCredentials.CLIENT_ID)
                    addProperty("client_secret", TraktCredentials.CLIENT_SECRET)
                }
                val request = Request.Builder().url("https://api.trakt.tv/oauth/device/token")
                    .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
                    .build()
                val response = try {
                    withContext(Dispatchers.IO) { http.newCall(request).execute() }
                } catch (_: IOException) {
                    continue
                }
                response.use {
                    when (devicePollAction(it.code)) {
                        DevicePollAction.COMPLETE -> {
                            completeAuthorization(gson.fromJson(it.body?.string().orEmpty(), TokenResponse::class.java))
                            return
                        }
                        DevicePollAction.WAIT -> Unit
                        DevicePollAction.SLOW_DOWN -> intervalSeconds++
                        DevicePollAction.FAIL -> throw IllegalStateException("Trakt authorization failed (${it.code})")
                    }
                }
            }
            throw IllegalStateException("Trakt authorization expired")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.value = TraktState(lastError = error.message)
        }
    }
    private fun applyProfile(profile: JsonObject) {
        val user = profile.getAsJsonObject("user") ?: profile
        _state.value = TraktState(
            connected = true,
            name = user.get("name")?.asString,
            username = user.get("username")?.asString,
            avatar = user.getAsJsonObject("images")?.getAsJsonObject("avatar")?.get("full")?.asString,
        )
    }

    private data class TokenResponse(
        @com.google.gson.annotations.SerializedName("access_token") val accessToken: String,
        @com.google.gson.annotations.SerializedName("refresh_token") val refreshToken: String,
        @com.google.gson.annotations.SerializedName("expires_in") val expiresIn: Long,
    )

    private data class DeviceAuthorization(
        @com.google.gson.annotations.SerializedName("device_code") val deviceCode: String,
        @com.google.gson.annotations.SerializedName("user_code") val userCode: String,
        @com.google.gson.annotations.SerializedName("verification_url") val verificationUrl: String,
        @com.google.gson.annotations.SerializedName("expires_in") val expiresIn: Long,
        val interval: Long,
    )

    private companion object { const val REDIRECT_URI = "https://zstream.mov" }
}

internal enum class DevicePollAction { COMPLETE, WAIT, SLOW_DOWN, FAIL }

internal fun devicePollAction(status: Int) = when (status) {
    200 -> DevicePollAction.COMPLETE
    400 -> DevicePollAction.WAIT
    429 -> DevicePollAction.SLOW_DOWN
    else -> DevicePollAction.FAIL
}

internal fun buildTraktHistoryPayload(progress: ProgressEntity): JsonObject? {
    val watchedAt = java.time.Instant.ofEpochMilli(progress.updatedAt).toString()
    val ids = JsonObject().apply { addProperty("tmdb", progress.tmdbId.toIntOrNull()) }
    val item = JsonObject().apply {
        add("ids", ids)
        if (progress.type == "movie") addProperty("watched_at", watchedAt)
    }
    if (progress.type == "movie") return JsonObject().apply {
        add("movies", com.google.gson.JsonArray().apply { add(item) })
    }
    val season = progress.seasonNumber ?: return null
    val episode = progress.episodeNumber ?: return null
    item.add("seasons", com.google.gson.JsonArray().apply {
        add(JsonObject().apply {
            addProperty("number", season)
            add("episodes", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("number", episode)
                    addProperty("watched_at", watchedAt)
                })
            })
        })
    })
    return JsonObject().apply { add("shows", com.google.gson.JsonArray().apply { add(item) }) }
}

internal fun traktPlaybackPercent(progress: ProgressEntity): Double? {
    if (progress.duration <= 0 || progress.type != "movie" && (progress.seasonNumber == null || progress.episodeNumber == null)) return null
    return (progress.watched * 100.0 / progress.duration).takeIf { it in 0.01..<95.0 }
}

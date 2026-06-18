package com.zstream.android.data.remote

import com.google.gson.annotations.SerializedName
import com.zstream.android.Urls
import retrofit2.http.*

val BACKEND_URL get() = Urls.BACKEND

// ── Request bodies ────────────────────────────────────────────────────────────

data class LoginStartBody(val publicKey: String)
data class ChallengeResponse(val challenge: String)

data class ChallengePayload(val code: String, val signature: String)
data class LoginCompleteBody(
    val publicKey: String,
    val challenge: ChallengePayload,
    val device: String,
    val namespace: String = "movie-web",
)

data class RegisterStartBody(val captchaToken: String? = null)
data class RegisterCompleteBody(
    val publicKey: String,
    val challenge: ChallengePayload,
    val device: String,
    val profile: ProfileBody,
    val namespace: String = "movie-web",
)
data class ProfileBody(val colorA: String = "purple", val colorB: String = "indigo", val icon: String = "userdefault")

// ── Response types ────────────────────────────────────────────────────────────

data class SessionResponse(
    val id: String,
    @SerializedName("userId", alternate = ["user_id", "user"]) val userId: String,
    val device: String,
)
data class LoginResponse(val token: String, val session: SessionResponse, val user: UserResponse?)
data class UserProfile(val colorA: String, val colorB: String, val icon: String)
data class UserResponse(
    val id: String,
    val nickname: String,
    val profile: UserProfile,
    val permissions: List<String>,
)
data class UserWithSession(val user: UserResponse, val session: SessionResponse)
data class RegisterResponse(val token: String, val session: SessionResponse, val user: UserResponse)

data class MetaSeasonEpisode(val id: String? = null, val number: Int? = null)
data class ProgressMeta(val title: String, val year: Int?, val poster: String?, val type: String)
data class ProgressResponse(
    val tmdbId: String,
    val season: MetaSeasonEpisode,
    val episode: MetaSeasonEpisode,
    val meta: ProgressMeta,
    val duration: String,
    val watched: String,
    val updatedAt: String,
)

data class BookmarkMeta(val title: String, val year: Int?, val poster: String?, val type: String)
data class BookmarkResponse(
    val tmdbId: String,
    val meta: BookmarkMeta,
    val group: List<String>? = null,
    val favoriteEpisodes: List<String>? = null,
    val updatedAt: String
)

data class ProgressInput(
    val tmdbId: String,
    val watched: Int,
    val duration: Int,
    val meta: ProgressMeta,
    val seasonId: String? = null,
    val episodeId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

data class BookmarkInput(
    val tmdbId: String,
    val meta: BookmarkMeta,
    val group: List<String>? = null,
    val favoriteEpisodes: List<String>? = null
)

data class SettingsResponse(
    val applicationTheme: String? = null,
    val applicationLanguage: String? = null,
    val defaultSubtitleLanguage: String? = null,
    val proxyUrls: List<String>? = null,
    val febboxKey: String? = null,
    val debridToken: String? = null,
    val debridService: String? = null,
    val tidbKey: String? = null,
    val enableThumbnails: Boolean? = null,
    val enableAutoplay: Boolean? = null,
    val enableSkipCredits: Boolean? = null,
    val enableAutoSkipSegments: Boolean? = null,
    val enableDiscover: Boolean? = null,
    val enableFeatured: Boolean? = null,
    val enableDetailsModal: Boolean? = null,
    val enableImageLogos: Boolean? = null,
    val enableCarouselView: Boolean? = null,
    val enableMinimalCards: Boolean? = null,
    val forceCompactEpisodeView: Boolean? = null,
    val lastSuccessfulSource: String? = null,
    val enableLastSuccessfulSource: Boolean? = null,
    val enableLowPerformanceMode: Boolean? = null,
    val enableNativeSubtitles: Boolean? = null,
    val enableHoldToBoost: Boolean? = null,
    val manualSourceSelection: Boolean? = null,
    val enableDoubleClickToSeek: Boolean? = null,
    val enableAutoResumeOnPlaybackError: Boolean? = null,
    val enablePauseOverlay: Boolean? = null,
    val enableNumberKeySeeking: Boolean? = null,
    val sourceOrder: List<String>? = null,
    val enableSourceOrder: Boolean? = null,
    val embedOrder: List<String>? = null,
    val enableEmbedOrder: Boolean? = null,
    val proxyTmdb: Boolean? = null,
    val homeSectionOrder: List<String>? = null
)

// ── Retrofit service ──────────────────────────────────────────────────────────

interface BackendApi {
    @POST("auth/login/start")
    suspend fun loginStart(@Body body: LoginStartBody): ChallengeResponse

    @POST("auth/login/complete")
    suspend fun loginComplete(@Body body: LoginCompleteBody): LoginResponse

    @POST("auth/register/start")
    suspend fun registerStart(@Body body: RegisterStartBody): ChallengeResponse

    @POST("auth/register/complete")
    suspend fun registerComplete(@Body body: RegisterCompleteBody): RegisterResponse

    @GET("users/@me")
    suspend fun getMe(@Header("Authorization") auth: String): UserWithSession

    @GET("users/{id}/settings")
    suspend fun getSettings(@Path("id") userId: String, @Header("Authorization") auth: String): SettingsResponse

    @PUT("users/{id}/settings")
    suspend fun updateSettings(@Path("id") userId: String, @Header("Authorization") auth: String, @Body body: SettingsResponse): SettingsResponse

    @GET("users/{id}/progress")
    suspend fun getProgress(@Path("id") userId: String, @Header("Authorization") auth: String): List<ProgressResponse>

    @PUT("users/{id}/progress/{tmdbId}")
    suspend fun setProgress(@Path("id") userId: String, @Path("tmdbId") tmdbId: String,
                            @Header("Authorization") auth: String, @Body body: ProgressInput): ProgressResponse

    @DELETE("users/{id}/progress/{tmdbId}")
    suspend fun removeProgress(@Path("id") userId: String, @Path("tmdbId") tmdbId: String,
                               @Header("Authorization") auth: String)

    @GET("users/{id}/bookmarks")
    suspend fun getBookmarks(@Path("id") userId: String, @Header("Authorization") auth: String): List<BookmarkResponse>

    @POST("users/{id}/bookmarks/{tmdbId}")
    suspend fun addBookmark(@Path("id") userId: String, @Path("tmdbId") tmdbId: String,
                            @Header("Authorization") auth: String, @Body body: BookmarkInput): BookmarkResponse

    @DELETE("users/{id}/bookmarks/{tmdbId}")
    suspend fun removeBookmark(@Path("id") userId: String, @Path("tmdbId") tmdbId: String,
                               @Header("Authorization") auth: String)
}

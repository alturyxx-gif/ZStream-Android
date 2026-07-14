package com.zstream.android.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class RybbitTrackRequest(
    @SerializedName("site_id") val siteId: String,
    val type: String,
    val pathname: String? = null,
    val hostname: String? = null,
    @SerializedName("page_title") val pageTitle: String? = null,
    val referrer: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("event_name") val eventName: String? = null,
    /** JSON-encoded string, per Rybbit's API (not a nested object). */
    val properties: String? = null,
    val language: String? = null,
)

interface RybbitApi {
    @POST("track")
    suspend fun track(@Body body: RybbitTrackRequest): Response<Unit>
}

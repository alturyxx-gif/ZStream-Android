package com.zstream.android.plugin

import android.util.Log
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PluginUpdateChecker"

/**
 * Fetches and parses the plugin manifest from the CDN.
 * No side effects — just fetches and returns. All decisions are made by PluginManager.
 */
@Singleton
class PluginUpdateChecker @Inject constructor(
    private val httpClient: OkHttpClient,
) {

    data class PluginManifest(
        val latestVersion: Int,
        val minAppVersion: Int,
        val downloadUrl: String,
        val hash: String, // "sha256:<hex>"
        val displayVersion: String,
    )

    /**
     * Fetches the manifest from [PluginConstants.MANIFEST_URL].
     * Throws on network failure or malformed JSON.
     */
    fun fetchManifest(): PluginManifest {
        val request = Request.Builder()
            .url(PluginConstants.MANIFEST_URL)
            .cacheControl(CacheControl.Builder().noCache().noStore().build())
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string()
            ?: throw IllegalStateException("Empty manifest response")

        if (!response.isSuccessful) {
            throw IllegalStateException("Manifest fetch failed: HTTP ${response.code}")
        }

        return try {
            val json = JSONObject(body)
            val latestVersion = json.getInt("latestVersion")
            PluginManifest(
                latestVersion  = latestVersion,
                minAppVersion  = json.optInt("minAppVersion", 1),
                downloadUrl    = json.getString("downloadUrl"),
                hash           = json.getString("hash"),
                displayVersion = json.optString("displayVersion", latestVersion.toString()),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Manifest parse failed: ${e.message}")
            throw IllegalStateException("Malformed plugin manifest: ${e.message}", e)
        }
    }
}

package com.zstream.android.data

import android.util.Log
import com.zstream.android.data.local.preferences.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

enum class AuroraKeyStatus { CHECKING, VALID, INVALID, API_DOWN, UNSET }

data class AuroraKeyInfo(
    val key: String,
    val status: AuroraKeyStatus,
    val usedLabel: String? = null,
    val limitLabel: String? = null,
    val resetLabel: String? = null,
    val remainingBytes: Long? = null,
    val totalBytes: Long? = null,
) {
    // If we can't parse the usage numbers we fail open (don't treat as exhausted) so a
    // formatting change on the Aurora side never locks working keys out of rotation.
    val exhausted: Boolean get() = remainingBytes != null && remainingBytes <= 0L

    /** 0-100, or null if we couldn't parse usage/limit for this key. */
    val percentRemaining: Int?
        get() {
            val remaining = remainingBytes ?: return null
            val total = totalBytes ?: return null
            if (total <= 0L) return null
            return ((remaining.toDouble() / total) * 100).toInt().coerceIn(0, 100)
        }
}

/**
 * Validates Aurora (Febbox) API keys and rotates between multiple user-supplied keys as their
 * daily bandwidth allowance is used up. Reference: /p-stream/src/pages/parts/settings/SetupPart.tsx
 * (testFebboxKey / fetchFebboxQuota).
 *
 * The /traffic endpoint 403s ("forbidden") any request whose Referer/Origin isn't one of its
 * allowlisted deployment domains — confirmed by curl: adding
 * Referer/Origin: https://zstream.mov flips a 403 into a real response, while other domains
 * (including p-stream.org) still get rejected. It has nothing to do with TLS fingerprinting or
 * request signing (those are only required by the /search stream-resolve endpoint).
 */
@Singleton
class AuroraKeyManager @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settingsPrefs: SettingsPreferences,
) {
    suspend fun checkKey(key: String): AuroraKeyInfo = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext AuroraKeyInfo(key, AuroraKeyStatus.UNSET)
        runCatching {
            val request = Request.Builder()
                .url("https://aurora.fontaine.lol/traffic?ui=$key")
                .header("Referer", "https://zstream.mov/")
                .header("Origin", "https://zstream.mov")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val status = if (response.code == 503 || response.code == 502) {
                        AuroraKeyStatus.API_DOWN
                    } else {
                        AuroraKeyStatus.INVALID
                    }
                    Log.w("AuroraKeyManager", "checkKey: HTTP ${response.code} body=${response.body?.string()}")
                    return@use AuroraKeyInfo(key, status)
                }
                val body = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                if (json == null || json.hasTruthyError()) {
                    Log.w("AuroraKeyManager", "checkKey: rejected body=$body")
                    return@use AuroraKeyInfo(key, AuroraKeyStatus.INVALID)
                }
                val used = json.opt("traffic_today_usage")?.toString()
                val limit = json.opt("traffic_limit")?.toString()
                val resetAt = json.opt("reset_at")?.toString()
                AuroraKeyInfo(
                    key = key,
                    status = AuroraKeyStatus.VALID,
                    usedLabel = used,
                    limitLabel = limit,
                    resetLabel = resetAt,
                    remainingBytes = remainingBytes(used, limit),
                    totalBytes = parseSize(limit),
                )
            }
        }.getOrElse {
            Log.w("AuroraKeyManager", "checkKey: request failed", it)
            AuroraKeyInfo(key, AuroraKeyStatus.API_DOWN)
        }
    }

    suspend fun checkKeys(keys: List<String>): List<AuroraKeyInfo> =
        keys.filter { it.isNotBlank() }.map { checkKey(it) }

    /**
     * Looks for a usable key other than [excluding] without touching the active key in
     * preferences — used to check "is there anywhere to switch to" before committing to a
     * mid-playback re-resolve (see PlayerViewModel's exhausted-key countdown).
     */
    suspend fun findFreshKey(keys: List<String>, excluding: String?): String? =
        keys.filter { it.isNotBlank() && it != excluding }.firstNotNullOfOrNull { key ->
            val info = checkKey(key)
            key.takeIf { info.status == AuroraKeyStatus.VALID && !info.exhausted }
        }

    /**
     * Re-checks the configured keys and, if the currently active key is invalid or has
     * exhausted its daily bandwidth, rotates to the next key in the list that still has
     * bandwidth available. Safe to call before every scrape/download attempt.
     */
    suspend fun ensureActiveKey(): String? {
        val current = settingsPrefs.settings.first()
        val keys = current.febboxKeys.filter { it.isNotBlank() }
        if (keys.isEmpty()) return current.febboxKey

        val active = current.febboxKey
        if (!active.isNullOrBlank()) {
            val activeInfo = checkKey(active)
            if (activeInfo.status == AuroraKeyStatus.VALID && !activeInfo.exhausted) return active
        }

        val next = keys.firstNotNullOfOrNull { key ->
            val info = checkKey(key)
            key.takeIf { info.status == AuroraKeyStatus.VALID && !info.exhausted }
        }

        if (next != null && next != active) {
            settingsPrefs.updateSettings(current.copy(febboxKey = next), syncToRemote = false)
        }
        return next ?: active
    }

    private fun remainingBytes(used: String?, limit: String?): Long? {
        val usedBytes = parseSize(used) ?: return null
        val limitBytes = parseSize(limit) ?: return null
        return (limitBytes - usedBytes).coerceAtLeast(0L)
    }

    private fun parseSize(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("([\\d.]+)\\s*(B|KB|MB|GB|TB)?", RegexOption.IGNORE_CASE)
            .find(raw.trim()) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].ifBlank { "B" }.uppercase()
        val multiplier = when (unit) {
            "B" -> 1.0
            "KB" -> 1024.0
            "MB" -> 1024.0 * 1024
            "GB" -> 1024.0 * 1024 * 1024
            "TB" -> 1024.0 * 1024 * 1024 * 1024
            else -> 1.0
        }
        return (value * multiplier).toLong()
    }

    /**
     * Mirrors the web client's `data?.error` truthy check (SetupPart.tsx testFebboxKey) instead
     * of just checking key presence — the Aurora API can return an "error" key set to false/null
     * on a perfectly valid response, which a plain has("error") check would misreport as invalid.
     */
    private fun JSONObject.hasTruthyError(): Boolean {
        if (!has("error")) return false
        return when (val err = opt("error")) {
            null, JSONObject.NULL -> false
            is Boolean -> err
            is String -> err.isNotEmpty()
            is Number -> err.toDouble() != 0.0
            else -> true
        }
    }
}

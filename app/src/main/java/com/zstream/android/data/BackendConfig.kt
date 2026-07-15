package com.zstream.android.data

import android.content.Context
import com.zstream.android.Urls
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-configurable override for the account/sync backend host (login, progress, bookmarks,
 * settings sync). Read fresh from SharedPreferences on every access (mirrors
 * [com.zstream.android.data.adb.ReleaseUpdateManager]'s pattern) so a change takes effect on the
 * very next request without needing an app restart -- see [BackendUrlInterceptor], which is the
 * mechanism that actually applies this at request time since Retrofit's own `baseUrl` is fixed at
 * construction. Does not affect TMDB, IMDb, the plugin manifest CDN, or Artemis (which is
 * hardcoded plugin-side and unrelated to this backend).
 */
@Singleton
class BackendConfig @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The backend URL actually in effect: the custom override if set and valid, else [Urls.BACKEND]. */
    val baseUrl: String get() = customUrl ?: Urls.BACKEND

    val customUrl: String? get() = prefs.getString(KEY_CUSTOM_URL, null)?.takeIf { it.isNotBlank() }

    val isCustom: Boolean get() = customUrl != null

    /** Sets a custom backend URL. Pass blank/null to reset to the default. Returns false if [url] is non-blank but not a valid URL. */
    fun setCustomUrl(url: String?): Boolean {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_CUSTOM_URL).apply()
            return true
        }
        if (trimmed.toHttpUrlOrNull() == null) return false
        prefs.edit().putString(KEY_CUSTOM_URL, trimmed).apply()
        return true
    }

    fun reset() {
        prefs.edit().remove(KEY_CUSTOM_URL).apply()
    }

    companion object {
        private const val PREFS = "backend_config"
        private const val KEY_CUSTOM_URL = "custom_backend_url"
    }
}

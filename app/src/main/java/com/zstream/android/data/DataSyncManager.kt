package com.zstream.android.data

import android.util.Log
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.data.local.preferences.UserPreferences
import com.zstream.android.data.remote.BackendApi
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DataSyncManager"

@Singleton
class DataSyncManager @Inject constructor(
    private val accountRepo: AccountRepository,
    private val progressRepo: ProgressRepository,
    private val bookmarkRepo: BookmarkRepository,
    private val settingsPrefs: SettingsPreferences,
    private val userPrefs: UserPreferences,
    private val api: BackendApi,
) {
    /**
     * Perform a full sync from remote to local.
     *
     * @param pushLocalSettingsFirst For a brand-new account the backend has no settings yet,
     * so pulling would stomp whatever the user already had configured on this device with
     * empty/hardcoded defaults. Pass true (on registration) to seed the backend with the
     * device's current settings instead of pulling.
     */
    suspend fun syncAllFromRemote(pushLocalSettingsFirst: Boolean = false) {
        val session = accountRepo.currentSession ?: run {
            Log.w(TAG, "No active session for sync")
            return
        }

        Log.d(TAG, "Starting full sync for user ${session.userId}")

        // Parallel sync (optional, but keep it simple for now)
        try {
            if (pushLocalSettingsFirst) {
                Log.d(TAG, "New account -- pushing local settings to remote instead of pulling")
                syncSettingsToRemote()
            } else {
                settingsPrefs.syncFromRemote(session.userId, "Bearer ${session.token}", api)
            }
            progressRepo.syncFromRemote()
            bookmarkRepo.syncFromRemote()
            Log.d(TAG, "Full sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during full sync", e)
        }
    }

    /**
     * Clear local user-specific data (on logout)
     * App settings are preserved locally.
     */
    suspend fun clearAllLocalData() {
        Log.d(TAG, "Clearing local user-specific data (progress, bookmarks, and user prefs)")
        progressRepo.clearProgress()
        bookmarkRepo.clearBookmarks()
        userPrefs.clear()
    }

    /**
     * Push current local settings to the remote backend.
     * Only includes fields the backend knows about — subtitle styling is
     * local-only and is NOT sent.
     */
    suspend fun syncSettingsToRemote() {
        val session = accountRepo.currentSession ?: return
        val settings = settingsPrefs.settings.first().let { it.copy(subtitleVerticalPosition = it.subtitleVerticalPosition.coerceAtLeast(0f)) }
        val body = settings.toSyncableJsonBody()
        Log.d(TAG, "Pushing settings to remote")
        val response = api.updateSettingsRaw(session.userId, "Bearer ${session.token}", body)
        val responseBody = response.body()?.string()
        Log.d(TAG, "Settings push response: ${response.code()} body=$responseBody")
    }
}

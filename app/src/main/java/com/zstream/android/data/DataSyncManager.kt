package com.zstream.android.data

import android.util.Log
import com.zstream.android.data.local.preferences.SettingsPreferences
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
    private val api: BackendApi,
) {
    /**
     * Perform a full sync from remote to local
     */
    suspend fun syncAllFromRemote() {
        val session = accountRepo.currentSession ?: run {
            Log.w(TAG, "No active session for sync")
            return
        }

        Log.d(TAG, "Starting full sync for user ${session.userId}")
        
        // Parallel sync (optional, but keep it simple for now)
        try {
            progressRepo.syncFromRemote()
            bookmarkRepo.syncFromRemote()
            settingsPrefs.syncFromRemote(session.userId, "Bearer ${session.token}", api)
            Log.d(TAG, "Full sync completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during full sync", e)
        }
    }

    /**
     * Clear all local data (on logout)
     */
    suspend fun clearAllLocalData() {
        Log.d(TAG, "Clearing all local cached data")
        progressRepo.clearProgress()
        bookmarkRepo.clearBookmarks()
        settingsPrefs.clear()
    }

    /**
     * Push current local settings to the remote backend.
     * Only includes fields the backend knows about — subtitle styling is
     * local-only and is NOT sent.
     */
    suspend fun syncSettingsToRemote() {
        val session = accountRepo.currentSession ?: return
        val settings = settingsPrefs.settings.first()
        val body = settings.toSyncableJsonBody()
        Log.d(TAG, "Pushing settings to remote")
        val response = api.updateSettingsRaw(session.userId, "Bearer ${session.token}", body)
        val responseBody = response.body()?.string()
        Log.d(TAG, "Settings push response: ${response.code()} body=$responseBody")
    }
}

package com.zstream.android.data

import android.util.Log
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.data.remote.BackendApi
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
}

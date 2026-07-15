package com.zstream.android.plugin

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zstream.android.di.PluginDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_SOURCE_ORDER = stringPreferencesKey("source_order_json")
private val KEY_DOWNLOAD_SOURCE_ORDER = stringPreferencesKey("download_source_order_json")

/**
 * Persistence-only: stores the raw id list from the user's manual drag-reorder (if any) and
 * asks the plugin to compute the actual order. Default priority, VIP-key preference, and
 * download-priority logic all live in the plugin (Entry.orderedSourcesJson /
 * downloadOrderedSourcesJson) — not here — so they can be retuned via a plugin update without
 * an app release.
 */
@Singleton
class SourceOrderStore @Inject constructor(
    @PluginDataStore private val dataStore: DataStore<Preferences>,
    private val pluginManager: PluginManager,
) {

    suspend fun getOrderedSources(
        hasArtemisVipKey: Boolean = false,
        hasAuroraKey: Boolean = false,
    ): List<SourceInfo> {
        val stored = readStoredOrder(KEY_SOURCE_ORDER)
        return pluginManager.orderedSources(stored, hasArtemisVipKey, hasAuroraKey)
    }

    suspend fun saveOrder(sourceIds: List<String>) {
        dataStore.edit { prefs -> prefs[KEY_SOURCE_ORDER] = JSONArray(sourceIds).toString() }
    }

    suspend fun getDownloadOrder(hasArtemisVipKey: Boolean = false): List<SourceInfo> {
        val stored = readStoredOrder(KEY_DOWNLOAD_SOURCE_ORDER)
        return pluginManager.downloadOrderedSources(stored, hasArtemisVipKey)
    }

    suspend fun saveDownloadOrder(sourceIds: List<String>) {
        dataStore.edit { prefs -> prefs[KEY_DOWNLOAD_SOURCE_ORDER] = JSONArray(sourceIds).toString() }
    }

    /** Clears the saved manual order so [getOrderedSources] falls back to the plugin's default priority. */
    suspend fun clearOrder() {
        dataStore.edit { prefs -> prefs.remove(KEY_SOURCE_ORDER) }
    }

    /** Clears the saved manual download order so [getDownloadOrder] falls back to the plugin's default priority. */
    suspend fun clearDownloadOrder() {
        dataStore.edit { prefs -> prefs.remove(KEY_DOWNLOAD_SOURCE_ORDER) }
    }

    /** Raw saved id list (no plugin merge/defaults applied) — for backup/export. */
    suspend fun getSavedOrderIds(): List<String> = readStoredOrder(KEY_SOURCE_ORDER)

    /** Raw saved download-order id list (no plugin merge/defaults applied) — for backup/export. */
    suspend fun getSavedDownloadOrderIds(): List<String> = readStoredOrder(KEY_DOWNLOAD_SOURCE_ORDER)

    private suspend fun readStoredOrder(key: androidx.datastore.preferences.core.Preferences.Key<String>): List<String> {
        val json = dataStore.data.first()[key] ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

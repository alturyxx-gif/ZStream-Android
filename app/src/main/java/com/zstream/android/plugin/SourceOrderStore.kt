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
private val DOWNLOAD_PRIORITY_SOURCE_IDS = listOf("stellar", "nesterov")

@Singleton
class SourceOrderStore @Inject constructor(
    @PluginDataStore private val dataStore: DataStore<Preferences>,
) {

    suspend fun getOrderedSources(pluginSources: List<SourceInfo>): List<SourceInfo> {
        val stored = readStoredOrder(KEY_SOURCE_ORDER)
        if (stored.isEmpty()) return pluginSources
        return mergeOrder(stored, pluginSources)
    }

    suspend fun saveOrder(sourceIds: List<String>) {
        dataStore.edit { prefs -> prefs[KEY_SOURCE_ORDER] = JSONArray(sourceIds).toString() }
    }

    suspend fun getDownloadOrder(pluginSources: List<SourceInfo>): List<SourceInfo> {
        val stored = readStoredOrder(KEY_DOWNLOAD_SOURCE_ORDER)
        if (stored.isEmpty()) {
            val priority = DOWNLOAD_PRIORITY_SOURCE_IDS.mapNotNull { id -> pluginSources.firstOrNull { it.id.equals(id, ignoreCase = true) } }
            val rest = pluginSources.filterNot { source -> priority.any { it.id == source.id } }
            return priority + rest
        }
        return mergeOrder(stored, pluginSources)
    }

    suspend fun saveDownloadOrder(sourceIds: List<String>) {
        dataStore.edit { prefs -> prefs[KEY_DOWNLOAD_SOURCE_ORDER] = JSONArray(sourceIds).toString() }
    }

    private fun mergeOrder(stored: List<String>, pluginSources: List<SourceInfo>): List<SourceInfo> {
        val pluginMap = pluginSources.associateBy { it.id }
        val ordered = stored.mapNotNull { pluginMap[it] }.toMutableList()
        val seenIds = ordered.map { it.id }.toSet()
        pluginSources.forEach { source ->
            if (source.id !in seenIds) ordered.add(source)
        }
        return ordered
    }

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


package com.zstream.android.plugin

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zstream.android.di.PluginDataStore
import com.zstream.plugin.api.SourceInfo
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_SOURCE_ORDER = stringPreferencesKey("source_order_json")

/**
 * Persists the user's preferred source ordering as an ordered JSON array of source IDs.
 * Shares the same "plugin_prefs" DataStore instance as PluginManager (injected via PluginModule).
 *
 * A plain StringSet would lose ordering, so we store JSON instead.
 *
 * Merge semantics (applied in [getOrderedSources]):
 *  - Sources in user's stored order that still exist in [pluginSources] are kept in order.
 *  - Sources removed from the plugin are pruned.
 *  - New sources not yet in the stored order are appended to the end.
 */
@Singleton
class SourceOrderStore @Inject constructor(
    @PluginDataStore private val dataStore: DataStore<Preferences>,
) {

    /**
     * Returns the effective ordered source list by merging the stored user preference
     * with the live [pluginSources] from the loaded plugin.
     *
     * If no preference is stored, returns [pluginSources] in the plugin's default order.
     */
    suspend fun getOrderedSources(pluginSources: List<SourceInfo>): List<SourceInfo> {
        val stored = readStoredOrder()
        if (stored.isEmpty()) return pluginSources

        val pluginMap = pluginSources.associateBy { it.id }

        // Keep stored order, prune removed sources
        val ordered = stored.mapNotNull { pluginMap[it] }.toMutableList()

        // Append new sources not yet in the stored order
        val seenIds = ordered.map { it.id }.toSet()
        pluginSources.forEach { source ->
            if (source.id !in seenIds) ordered.add(source)
        }

        return ordered
    }

    /** Persists a new source ordering chosen by the user. */
    suspend fun saveOrder(sourceIds: List<String>) {
        val json = JSONArray(sourceIds).toString()
        dataStore.edit { prefs -> prefs[KEY_SOURCE_ORDER] = json }
    }

    private suspend fun readStoredOrder(): List<String> {
        val json = dataStore.data.first()[KEY_SOURCE_ORDER] ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

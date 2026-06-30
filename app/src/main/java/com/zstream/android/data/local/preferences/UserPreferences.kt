package com.zstream.android.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.readNotificationStore by preferencesDataStore("read_notifications")
private val Context.layoutDataStore by preferencesDataStore("layout_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val READ_GUIDS_KEY = stringPreferencesKey("read_guids")
    private val SECTION_ORDER_KEY = stringPreferencesKey("section_order")
    private val WATCHING_SORT_KEY = stringPreferencesKey("watching_sort")
    private val BOOKMARKS_SORT_KEY = stringPreferencesKey("bookmarks_sort")

    val readNotificationGuids: Flow<Set<String>> = context.readNotificationStore.data.map { prefs ->
        prefs[READ_GUIDS_KEY]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    }

    suspend fun markNotificationRead(guid: String) {
        context.readNotificationStore.edit { prefs ->
            val existing = prefs[READ_GUIDS_KEY]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
            prefs[READ_GUIDS_KEY] = (existing + guid).joinToString(",")
        }
    }

    suspend fun markAllNotificationsRead(guids: List<String>) {
        context.readNotificationStore.edit { prefs ->
            prefs[READ_GUIDS_KEY] = guids.joinToString(",")
        }
    }

    fun sectionOrderFlow(defaultOrder: List<String>): Flow<List<String>> =
        context.layoutDataStore.data.map { prefs ->
            prefs[SECTION_ORDER_KEY]?.split(",")?.filter { it.isNotBlank() }
                ?: defaultOrder
        }

    suspend fun saveSectionOrder(order: List<String>) {
        context.layoutDataStore.edit { prefs ->
            prefs[SECTION_ORDER_KEY] = order.joinToString(",")
        }
    }

    val watchingSort: Flow<String> = context.layoutDataStore.data.map { it[WATCHING_SORT_KEY] ?: "date" }
    val bookmarksSort: Flow<String> = context.layoutDataStore.data.map { it[BOOKMARKS_SORT_KEY] ?: "date" }

    suspend fun saveWatchingSort(value: String) = context.layoutDataStore.edit { it[WATCHING_SORT_KEY] = value }
    suspend fun saveBookmarksSort(value: String) = context.layoutDataStore.edit { it[BOOKMARKS_SORT_KEY] = value }

    suspend fun clear() {
        context.readNotificationStore.edit { it.clear() }
        context.layoutDataStore.edit { it.clear() }
    }
}

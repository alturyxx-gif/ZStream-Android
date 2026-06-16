package com.zstream.android.theme

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Application.themeDataStore by preferencesDataStore("theme")
private val THEME_ID_KEY = stringPreferencesKey("active_theme")

@HiltViewModel
class ThemeViewModel @Inject constructor(
    application: Application,
) : AndroidViewModel(application) {

    private val _currentTheme = mutableStateOf(DefaultTheme.create())
    val currentTheme: State<ZStreamTheme> = _currentTheme

    init {
        viewModelScope.launch {
            getApplication<Application>().themeDataStore.data
                .map { prefs ->
                    val themeId = prefs[THEME_ID_KEY] ?: "default"
                    getThemeById(themeId) ?: DefaultTheme.create()
                }
                .collect { theme ->
                    _currentTheme.value = theme
                }
        }
    }

    fun setTheme(theme: ZStreamTheme) {
        viewModelScope.launch {
            _currentTheme.value = theme
            getApplication<Application>().themeDataStore.edit { prefs ->
                prefs[THEME_ID_KEY] = theme.id
            }
        }
    }

    private fun getThemeById(id: String): ZStreamTheme? {
        return when (id) {
            "default" -> DefaultTheme.create()
            else -> null
        }
    }
}

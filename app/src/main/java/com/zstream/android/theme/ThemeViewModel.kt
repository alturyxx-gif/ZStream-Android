package com.zstream.android.theme

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.local.preferences.SettingsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    application: Application,
    private val settingsPreferences: SettingsPreferences,
) : AndroidViewModel(application) {

    private val _currentTheme = mutableStateOf(ThemeRegistry.defaultTheme)
    val currentTheme: State<ZStreamTheme> = _currentTheme

    private val _currentFont = mutableStateOf("onest")
    val currentFont: State<String> = _currentFont

    init {
        viewModelScope.launch {
            settingsPreferences.settings
                .map { settings ->
                    ThemeRegistry.getThemeById(settings.applicationTheme)
                }
                .collect { theme ->
                    _currentTheme.value = theme
                }
        }
        viewModelScope.launch {
            settingsPreferences.settings
                .map { settings -> settings.applicationFont }
                .collect { font ->
                    _currentFont.value = font
                }
        }
    }

    fun setTheme(theme: ZStreamTheme) {
        viewModelScope.launch {
            _currentTheme.value = theme
            settingsPreferences.setApplicationTheme(theme.id)
        }
    }
}

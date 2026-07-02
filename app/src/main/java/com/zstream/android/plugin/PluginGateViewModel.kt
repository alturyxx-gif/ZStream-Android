package com.zstream.android.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the plugin install gate screen (PluginInstallScreen).
 * Thin wrapper over PluginManager — owns the install progress and error state.
 */
@HiltViewModel
class PluginGateViewModel @Inject constructor(
    private val pluginManager: PluginManager,
) : ViewModel() {

    val pluginState: StateFlow<PluginState> = pluginManager.pluginState

    private val _installProgress = MutableStateFlow<Float?>(null)
    val installProgress: StateFlow<Float?> = _installProgress.asStateFlow()

    private val _installError = MutableStateFlow<String?>(null)
    val installError: StateFlow<String?> = _installError.asStateFlow()

    /** Called when the user taps "Install Plugin". */
    fun install() {
        viewModelScope.launch {
            _installProgress.value = 0f
            _installError.value = null
            runCatching {
                pluginManager.manualInstall { progress ->
                    _installProgress.value = progress
                }
            }.onSuccess {
                _installProgress.value = null
            }.onFailure { e ->
                _installError.value = e.message ?: "Install failed"
                _installProgress.value = null
            }
        }
    }

    /** Called from Settings to manually trigger an update check. */
    fun checkForUpdate() {
        viewModelScope.launch {
            runCatching { pluginManager.checkForUpdate() }
                .onFailure { _installError.value = it.message ?: "Update check failed" }
        }
    }

    /** DEBUG only — loads plugin APK directly from a file path, no download/verification. */
    fun debugSideload(path: String) {
        viewModelScope.launch {
            _installError.value = null
            runCatching { pluginManager.debugSideload(path) }
                .onFailure { _installError.value = it.message ?: "Sideload failed" }
        }
    }
}

package com.zstream.android.plugin

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val appContext: Context,
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
                Log.e("PluginGateViewModel", "Plugin installation failed", e)
                _installError.value = appContext.getString(R.string.tv_update_install_failed)
                _installProgress.value = null
            }
        }
    }

    /** Called from Settings to manually trigger an update check. */
    fun checkForUpdate() {
        viewModelScope.launch {
            runCatching { pluginManager.checkForUpdate() }
                .onFailure {
                    Log.e("PluginGateViewModel", "Plugin update check failed", it)
                    _installError.value = appContext.getString(R.string.settings_update_check_failed_prefix)
                }
        }
    }

    /** DEBUG only — loads plugin APK directly from a file path, no download/verification. */
    fun debugSideload(path: String) {
        viewModelScope.launch {
            _installError.value = null
            runCatching { pluginManager.debugSideload(path) }
                .onFailure {
                    Log.e("PluginGateViewModel", "Plugin sideload failed", it)
                    _installError.value = appContext.getString(R.string.tv_update_install_failed)
                }
        }
    }
}

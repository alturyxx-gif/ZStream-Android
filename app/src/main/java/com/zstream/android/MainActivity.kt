package com.zstream.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import com.zstream.android.provider.ProviderEngine
import com.zstream.android.ui.navigation.NavGraph
import com.zstream.android.ui.theme.ZStreamTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.zstream.android.ui.screens.MediaCardStandard
import com.zstream.android.ui.screens.MediaCardMinimal
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import com.zstream.android.data.local.preferences.SettingsPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.zstream.android.ui.screens.LocalMediaCard
import dagger.hilt.android.lifecycle.HiltViewModel
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var providerEngine: ProviderEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.BLACK
        providerEngine.init(this)

        setContent {
            val chromeVm: AppChromeViewModel = hiltViewModel()
            val useMinimalCards by chromeVm.useMinimalCards.collectAsStateWithLifecycle()
            val mediaCard = if (useMinimalCards) ::MediaCardMinimal else ::MediaCardStandard
            CompositionLocalProvider(LocalMediaCard provides mediaCard) {
                ZStreamTheme {
                    NavGraph()
                }
            }
        }
    }
}


@HiltViewModel
class AppChromeViewModel @Inject constructor(
    settingsPreferences: SettingsPreferences,
) : ViewModel() {
    val useMinimalCards: StateFlow<Boolean> = settingsPreferences.settings
        .map { it.enableMinimalCards }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )
}
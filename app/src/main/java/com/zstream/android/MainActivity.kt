package com.zstream.android

import android.app.Activity
import android.app.UiModeManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.zstream.android.data.WatchPartyAction
import com.zstream.android.data.WatchPartyManager
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.provider.ProviderEngine
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.navigation.NavGraph
import com.zstream.android.ui.screens.LocalMediaCard
import com.zstream.android.ui.screens.MediaCardComponent
import com.zstream.android.ui.screens.MediaCardMinimal
import com.zstream.android.ui.screens.MediaCardStandard
import com.zstream.android.ui.theme.ZStreamTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var providerEngine: ProviderEngine
    @Inject lateinit var watchPartyManager: WatchPartyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        providerEngine.init(this)

        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        val isTv = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        setContent {
            val navController = rememberNavController()
            val chromeVm: AppChromeViewModel = hiltViewModel()
            val useMinimalCards by chromeVm.useMinimalCards.collectAsStateWithLifecycle()
            val mediaCard: MediaCardComponent = if (useMinimalCards) {
                @Composable { media, onClick, percentage, seriesLabel, width, height, editOverlay ->
                    MediaCardMinimal(media, onClick, percentage, seriesLabel, width, height)
                }
            } else {
                @Composable { media, onClick, percentage, seriesLabel, width, height, editOverlay ->
                    MediaCardStandard(media, onClick, percentage, seriesLabel, width, height, editOverlay)
                }
            }
            
            AppBehaviorEffect(navController, isTv)
            WatchPartyGlobalEffect(navController, watchPartyManager)
            
            CompositionLocalProvider(
                LocalMediaCard provides mediaCard,
                LocalIsTv provides isTv
            ) {
                ZStreamTheme {
                    NavGraph(navController)
                }
            }
        }
    }

}

@Composable
fun AppBehaviorEffect(navController: NavController, isTv: Boolean) {
    val context = LocalContext.current
    DisposableEffect(navController, isTv) {
        if (isTv) return@DisposableEffect onDispose {}

        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val activity = context as? Activity ?: return@OnDestinationChangedListener
            val route = destination.route ?: ""
            if (route.startsWith("player")) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }
}

@Composable
fun WatchPartyGlobalEffect(navController: NavController, manager: WatchPartyManager) {
    LaunchedEffect(manager) {
        manager.actions.collect { action ->
            if (action is WatchPartyAction.Navigate) {
                val tmdbId = action.tmdbId.toIntOrNull() ?: return@collect
                val encodedTitle = Uri.encode(action.title)
                val encodedPoster = Uri.encode(action.poster ?: "")
                val season = action.season ?: -1
                val episode = action.episode ?: -1
                val sId = action.seasonId ?: ""
                val eId = action.episodeId ?: ""
                val year = action.year ?: 0
                navController.navigate("player/${action.mediaType}/$tmdbId?season=$season&episode=$episode&seasonId=$sId&episodeId=$eId&title=$encodedTitle&year=$year&poster=$encodedPoster") {
                    popUpTo("home")
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

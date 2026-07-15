package com.zstream.android

import android.app.Activity
import android.app.UiModeManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.zstream.android.data.OPEN_TRACKED_RELEASE_MEDIA_TYPE_EXTRA
import com.zstream.android.data.OPEN_TRACKED_RELEASE_TMDB_ID_EXTRA
import com.zstream.android.data.OpenTrackedReleaseNavigation
import com.zstream.android.data.WatchPartyAction
import com.zstream.android.data.WatchPartyManager
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.data.adb.OPEN_TV_INSTALLER_EXTRA
import com.zstream.android.data.adb.RELEASE_UPDATE_EXTRA
import com.zstream.android.data.adb.ReleaseUpdateNavigation
import com.zstream.android.data.adb.ReleaseUpdateManager
import com.zstream.android.download.OpenDownloadsNavigation
import com.zstream.android.player.PlayerBackgroundController
import com.zstream.android.plugin.PLUGIN_UPDATE_EXTRA
import com.zstream.android.plugin.PluginGateViewModel
import com.zstream.android.plugin.PluginManager
import com.zstream.android.plugin.PluginState
import com.zstream.android.plugin.PluginUpdateNavigation
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.navigation.NavGraph
import com.zstream.android.ui.screens.LocalMediaCard
import com.zstream.android.ui.screens.MediaCardComponent
import com.zstream.android.ui.screens.MediaCardMinimal
import com.zstream.android.ui.screens.MediaCardStandard
import com.zstream.android.ui.screens.PluginInstallScreen
import com.zstream.android.ui.screens.PluginLoadingScreen
import com.zstream.android.ui.theme.ZStreamTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var watchPartyManager: WatchPartyManager
    @Inject lateinit var releaseUpdateManager: ReleaseUpdateManager
    @Inject lateinit var pluginManager: PluginManager
    @Inject lateinit var tvSyncRepository: com.zstream.android.data.TvSyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.breadcrumb("MainActivity", "onCreate start")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // Kick off plugin load + background update check
        pluginManager.initialize()
        CrashLog.breadcrumb("MainActivity", "pluginManager.initialize() dispatched")
        handleReleaseUpdateIntent(intent)
        handlePluginUpdateIntent(intent)
        handleOpenDownloadsIntent(intent)
        handleOpenTrackedReleaseIntent(intent)
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        val isTv = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        if (
            !isTv &&
            releaseUpdateManager.enabled.value &&
            android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 4102)
        }

        setContent {
            val pluginGateVm: PluginGateViewModel = hiltViewModel()
            val pluginState by pluginGateVm.pluginState.collectAsStateWithLifecycle()
            var debugGateDismissed by rememberSaveable { mutableStateOf(false) }
            val showDebugSideloadGate = BuildConfig.DEBUG && !debugGateDismissed &&
                (pluginState is PluginState.Ready || pluginState is PluginState.UpdateAvailable)

            LaunchedEffect(pluginState::class) {
                CrashLog.breadcrumb("MainActivity", "pluginState=${pluginState::class.simpleName}")
            }

            LaunchedEffect(Unit) {
                pluginManager.pluginUpdateError.collect { message ->
                    if (message != null) {
                        android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }

            ZStreamTheme {
                when {
                    pluginState is PluginState.NotInstalled ||
                        pluginState is PluginState.Failed -> PluginInstallScreen(pluginGateVm)

                    pluginState is PluginState.Loading -> PluginLoadingScreen()

                    showDebugSideloadGate -> PluginInstallScreen(
                        vm = pluginGateVm,
                        onContinue = { debugGateDismissed = true },
                    )

                    pluginState is PluginState.Ready ||
                        pluginState is PluginState.UpdateAvailable -> {
                        // Plugin is present — initialize the normal app chrome
                        CrashLog.breadcrumb("MainActivity", "entering app chrome (plugin ready)")
                        val navController = rememberNavController()
                        val chromeVm: AppChromeViewModel = hiltViewModel()
                        val useMinimalCards by chromeVm.useMinimalCards.collectAsStateWithLifecycle()
                        val mediaCard: MediaCardComponent = if (useMinimalCards) {
                            @Composable { media, onClick, percentage, seriesLabel, width, height, editOverlay ->
                                MediaCardMinimal(media, onClick, percentage, seriesLabel, width, height, editOverlay)
                            }
                        } else {
                            @Composable { media, onClick, percentage, seriesLabel, width, height, editOverlay ->
                                MediaCardStandard(media, onClick, percentage, seriesLabel, width, height, editOverlay)
                            }
                        }

                        AppBehaviorEffect(navController, isTv)
                        WatchPartyGlobalEffect(navController, watchPartyManager)
                        OpenDownloadsGlobalEffect(navController)
                        OpenTrackedReleaseGlobalEffect(navController)
                        TvCastGlobalEffect(navController, tvSyncRepository, isTv)
                        ProfilePickerGlobalEffect(navController, isTv)

                        CompositionLocalProvider(
                            LocalMediaCard provides mediaCard,
                            LocalIsTv provides isTv
                        ) {
                            NavGraph(navController)
                            com.zstream.android.ui.screens.DownloadDestinationPrompt()
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PlayerBackgroundController.onUserLeaveHint?.invoke()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReleaseUpdateIntent(intent)
        handlePluginUpdateIntent(intent)
        handleOpenDownloadsIntent(intent)
        handleOpenTrackedReleaseIntent(intent)
    }

    private fun handleReleaseUpdateIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(RELEASE_UPDATE_EXTRA, false) == true) {
            ReleaseUpdateNavigation.dispatch(intent.getBooleanExtra(OPEN_TV_INSTALLER_EXTRA, false))
            intent.removeExtra(RELEASE_UPDATE_EXTRA)
            intent.removeExtra(OPEN_TV_INSTALLER_EXTRA)
        }
    }

    private fun handlePluginUpdateIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(PLUGIN_UPDATE_EXTRA, false) == true) {
            PluginUpdateNavigation.dispatch()
            intent.removeExtra(PLUGIN_UPDATE_EXTRA)
        }
    }

    private fun handleOpenDownloadsIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(com.zstream.android.download.OPEN_DOWNLOADS_EXTRA, false) == true) {
            OpenDownloadsNavigation.dispatch()
            intent.removeExtra(com.zstream.android.download.OPEN_DOWNLOADS_EXTRA)
        }
    }

    private fun handleOpenTrackedReleaseIntent(intent: Intent?) {
        val mediaType = intent?.getStringExtra(OPEN_TRACKED_RELEASE_MEDIA_TYPE_EXTRA) ?: return
        val tmdbId = intent.getIntExtra(OPEN_TRACKED_RELEASE_TMDB_ID_EXTRA, -1)
        if (tmdbId == -1) return
        OpenTrackedReleaseNavigation.dispatch(mediaType, tmdbId)
        intent.removeExtra(OPEN_TRACKED_RELEASE_MEDIA_TYPE_EXTRA)
        intent.removeExtra(OPEN_TRACKED_RELEASE_TMDB_ID_EXTRA)
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
            if (route.startsWith("player") || route.startsWith("localPlayer") || route.startsWith("localFilePlayer")) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else if (route.startsWith("trailer")) {
                // Trailers follow whichever orientation the phone is physically held in,
                // instead of being forced landscape like the main player.
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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


/**
 * TV-only: shows the Netflix-style "who's watching" picker once on every launch, instead of
 * silently resuming whichever profile was last active.
 */
@Composable
fun ProfilePickerGlobalEffect(
    navController: NavController,
    isTv: Boolean,
) {
    var shown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isTv) {
        if (!isTv || shown) return@LaunchedEffect
        shown = true
        // Always show the picker on TV launch (Netflix-style "who's watching"), even with zero
        // cached profiles — ProfileSwitcherScreen has a "Continue without profile" card so this
        // never traps a profile-less user, it's just the consistent landing screen.
        navController.navigate("profileSwitcher") {
            popUpTo("home") { inclusive = true }
        }
    }
}

/**
 * TV-only: keeps the pairing receiver listening for as long as the TV app is open (not just while
 * the "Sync from phone" screen is visible), and reacts to an incoming cast command by stopping
 * whatever is currently playing and clearing the back stack down to Home before opening the new
 * content -- so pressing back from the cast player lands on Home, not on whatever was playing before.
 */
@Composable
fun TvCastGlobalEffect(
    navController: NavController,
    tvSyncRepository: com.zstream.android.data.TvSyncRepository,
    isTv: Boolean,
) {
    LaunchedEffect(isTv) {
        if (!isTv) return@LaunchedEffect
        if (!tvSyncRepository.receiverState.value.active) tvSyncRepository.startReceiver()
    }
    LaunchedEffect(isTv, tvSyncRepository) {
        if (!isTv) return@LaunchedEffect
        tvSyncRepository.pendingCast.collect { pending ->
            val cast = pending ?: return@collect
            tvSyncRepository.consumeCast()
            val encodedTitle = Uri.encode(cast.title)
            val encodedPoster = Uri.encode(cast.poster ?: "")
            val season = cast.season ?: -1
            val episode = cast.episode ?: -1
            val sId = cast.seasonId ?: ""
            val eId = cast.episodeId ?: ""
            val encodedSourceId = Uri.encode(cast.sourceId)
            val encodedVariantId = Uri.encode(cast.variantId ?: "")
            navController.navigate(
                "player/${cast.mediaType}/${cast.tmdbId}?season=$season&episode=$episode&seasonId=$sId&episodeId=$eId" +
                    "&title=$encodedTitle&year=${cast.year}&poster=$encodedPoster" +
                    "&castSourceId=$encodedSourceId&castVariantId=$encodedVariantId&castProgressSec=${cast.progressSec}"
            ) {
                popUpTo("home")
            }
        }
    }
}

@Composable
fun OpenDownloadsGlobalEffect(navController: NavController) {
    val shouldOpen by OpenDownloadsNavigation.open.collectAsStateWithLifecycle()
    LaunchedEffect(shouldOpen) {
        if (shouldOpen) {
            navController.navigate("downloads")
            OpenDownloadsNavigation.consume()
        }
    }
}

@Composable
fun OpenTrackedReleaseGlobalEffect(navController: NavController) {
    val target by OpenTrackedReleaseNavigation.target.collectAsStateWithLifecycle()
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        navController.navigate("detail/${t.mediaType}/${t.tmdbId}")
        OpenTrackedReleaseNavigation.consume()
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

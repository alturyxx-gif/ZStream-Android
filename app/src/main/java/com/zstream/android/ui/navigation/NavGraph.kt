package com.zstream.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zstream.android.ui.screens.*

@Composable
fun NavGraph(nav: NavHostController) {
    NavHost(nav, startDestination = "home") {
        composable("home") { HomeScreen(nav) }
        composable("search") { SearchScreen(nav) }
        composable("more/{source}?group={group}") { MoreScreen(nav) }
        dialog(
            "detail/{mediaType}/{id}?season={season}&episode={episode}",
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("id") { type = NavType.IntType },
                navArgument("season") { type = NavType.IntType; defaultValue = -1 },
                navArgument("episode") { type = NavType.IntType; defaultValue = -1 },
            ),
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) { DetailScreen(nav) }
        composable(
            "player/{mediaType}/{id}?season={season}&episode={episode}&seasonId={seasonId}&episodeId={episodeId}&title={title}&year={year}&poster={poster}&autoplay={autoplay}&castSourceId={castSourceId}&castVariantId={castVariantId}&castProgressSec={castProgressSec}",
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("id") { type = NavType.IntType },
                navArgument("season") { type = NavType.IntType; defaultValue = -1 },
                navArgument("episode") { type = NavType.IntType; defaultValue = -1 },
                navArgument("seasonId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("episodeId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("year") { type = NavType.IntType; defaultValue = 0 },
                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
                navArgument("autoplay") { type = NavType.BoolType; defaultValue = false },
                // Populated only when this screen was opened by an incoming TV cast command (see
                // TvCastGlobalEffect) -- tells PlayerViewModel to resolve this exact source/variant
                // directly instead of trying every source in order, and to resume at this progress.
                navArgument("castSourceId") { type = NavType.StringType; defaultValue = "" },
                navArgument("castVariantId") { type = NavType.StringType; defaultValue = "" },
                navArgument("castProgressSec") { type = NavType.LongType; defaultValue = 0L },
            )
        ) { PlayerScreen(nav) }
        dialog(
            "trailer?url={url}&title={title}",
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) { TrailerPlayerScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable("login") { LoginScreen(nav) }
        composable("watchHistory") { WatchHistoryScreen(nav) }
        composable("downloads") { DownloadsScreen(nav) }
        composable(
            "localPlayer/{downloadId}",
            arguments = listOf(navArgument("downloadId") { type = NavType.StringType }),
        ) { LocalPlayerScreen(nav) }
        composable(
            "localFilePlayer/{localMediaId}",
            arguments = listOf(navArgument("localMediaId") { type = NavType.StringType }),
        ) { LocalPlayerScreen(nav) }
        composable("tvSync") { TvSyncScreen(nav) }
        composable(
            "tvSyncPair?tvId={tvId}&host={host}&port={port}&tvName={tvName}",
            arguments = listOf(
                navArgument("tvId") { type = NavType.StringType; defaultValue = "" },
                // Populated only when pairing a freshly-discovered (not-yet-paired) TV from Manage TVs.
                navArgument("host") { type = NavType.StringType; defaultValue = "" },
                navArgument("port") { type = NavType.IntType; defaultValue = 0 },
                navArgument("tvName") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { TvSyncPairScreen(nav) }
        composable("tvInstaller") { TvInstallerScreen(onDismiss = { nav.popBackStack() }) }
        composable("profileSwitcher") { ProfileSwitcherScreen(nav) }
        composable("devVideo") { DevVideoScreen(nav) }
        composable(
            "devPlayer?url={url}&type={type}&headers={headers}",
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("type") { type = NavType.StringType; defaultValue = "mp4" },
                navArgument("headers") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { DevPlayerScreen(nav) }
    }
}

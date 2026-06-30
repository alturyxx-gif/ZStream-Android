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
            "detail/{mediaType}/{id}",
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("id") { type = NavType.IntType },
            ),
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) { DetailScreen(nav) }
        composable(
            "player/{mediaType}/{id}?season={season}&episode={episode}&seasonId={seasonId}&episodeId={episodeId}&title={title}&year={year}&poster={poster}&autoplay={autoplay}",
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
            )
        ) { PlayerScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable("login") { LoginScreen(nav) }
        composable("watchHistory") { WatchHistoryScreen(nav) }
    }
}

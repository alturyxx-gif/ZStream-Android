package com.zstream.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zstream.android.ui.screens.*

@Composable
fun NavGraph() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") { HomeScreen(nav) }
        composable("search") { SearchScreen(nav) }
        composable(
            "detail/{mediaType}/{id}",
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("id") { type = NavType.IntType },
            )
        ) { DetailScreen(nav) }
        composable(
            "player/{mediaType}/{id}",
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("id") { type = NavType.IntType },
            )
        ) { PlayerScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
    }
}

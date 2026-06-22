package com.zstream.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun rememberSafeNavigateBack(
    navController: NavController,
    scope: CoroutineScope,
    debounceMs: Long = 300L,
): () -> Unit {
    var backInFlight by remember(navController) { mutableStateOf(false) }

    return remember(navController, scope, debounceMs) {
        fun() {
            if (backInFlight) return
            if (navController.previousBackStackEntry == null) return

            backInFlight = true
            navController.popBackStack()
            scope.launch {
                delay(debounceMs)
                backInFlight = false
            }
        }
    }
}

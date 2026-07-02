package com.zstream.android.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zstream.android.BuildConfig
import com.zstream.android.plugin.PluginGateViewModel
import com.zstream.android.plugin.PluginState
import com.zstream.android.ui.components.themed.ZsButton

/**
 * Full-screen gate shown when the plugin is not installed or failed to load.
 * No NavGraph is initialized while this screen is active.
 * The user can background the app via "Go to Home Screen" but cannot proceed to content.
 */
@Composable
fun PluginInstallScreen(vm: PluginGateViewModel) {
    val pluginState by vm.pluginState.collectAsStateWithLifecycle()
    val progress by vm.installProgress.collectAsStateWithLifecycle()
    val error by vm.installError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            // Icon
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = "Content Plugin Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Body
            Text(
                text = "ZStream requires a small content plugin to resolve streams. " +
                    "The plugin is distributed separately so it can be updated " +
                    "independently without requiring an app update.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Progress bar (shown while downloading / verifying)
            AnimatedVisibility(
                visible = progress != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val p = progress ?: 0f
                    if (p >= 1f) {
                        // Verifying phase — indeterminate
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Verifying...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(p * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Error message
            AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Install / Retry button
            ZsButton(
                text = when {
                    pluginState is PluginState.Failed -> "Retry"
                    error != null -> "Retry"
                    else -> "Install Plugin"
                },
                onClick = { vm.install() },
                enabled = progress == null,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // Debug sideload button — only visible in debug builds
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(12.dp))
                ZsButton(
                    text = "Dev: Sideload plugin",
                    onClick = {
                        val path = context.getExternalFilesDir(null)
                            ?.resolve("plugin-debug.apk")?.absolutePath
                            ?: "/sdcard/Android/data/com.zstream.android/files/plugin-debug.apk"
                        vm.debugSideload(path)
                    },
                    enabled = progress == null,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/** Minimal full-screen loading indicator shown during the plugin startup check. */
@Composable
fun PluginLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

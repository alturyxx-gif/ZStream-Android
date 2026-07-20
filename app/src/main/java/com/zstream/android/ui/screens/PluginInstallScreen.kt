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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zstream.android.BuildConfig
import com.zstream.android.R
import com.zstream.android.plugin.PluginGateViewModel
import com.zstream.android.plugin.PluginState
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.ui.components.themed.ZsButton
import com.zstream.android.ui.components.themed.ZsButtonVariant
import com.zstream.android.ui.components.themed.ZsCard
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant
import com.zstream.android.ui.components.themed.ZsTextButton

@Composable
private fun pluginScreenBackground(accent: androidx.compose.ui.graphics.Color, base: androidx.compose.ui.graphics.Color) =
    Brush.radialGradient(
        colors = listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0.05f), base),
        center = Offset(0.5f, 0.05f),
        radius = 1100f,
    )

/**
 * Full-screen gate shown when the plugin is not installed or failed to load.
 * No NavGraph is initialized while this screen is active.
 * The user can background the app via "Go to Home Screen" but cannot proceed to content.
 */
@Composable
fun PluginInstallScreen(vm: PluginGateViewModel, onContinue: (() -> Unit)? = null) {
    val pluginState by vm.pluginState.collectAsStateWithLifecycle()
    val progress by vm.installProgress.collectAsStateWithLifecycle()
    val error by vm.installError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val theme = LocalZStreamTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pluginScreenBackground(theme.colors.type.logo, theme.colors.background.main)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(theme.colors.type.logo.copy(alpha = 0.35f), theme.colors.type.logo.copy(alpha = 0.08f)),
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = theme.colors.type.logo,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.plugin_install_one_more_step),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = theme.colors.type.emphasis,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.plugin_install_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = theme.colors.type.secondary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(36.dp))

            AnimatedVisibility(visible = progress != null, enter = fadeIn(), exit = fadeOut()) {
                ZsCard(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val p = progress ?: 0f
                        if (p >= 1f) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = theme.colors.type.logo,
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.plugin_install_verifying),
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.colors.type.secondary,
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { p },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = theme.colors.type.logo,
                                trackColor = theme.colors.type.divider.copy(alpha = 0.2f),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.plugin_install_downloading, (p * 100).toInt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.colors.type.secondary,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                    ZsStatusBanner(
                        message = error.orEmpty(),
                        variant = ZsStatusBannerVariant.Error,
                    )
                }
            }

            ZsButton(
                text = when {
                    pluginState is PluginState.Failed -> stringResource(R.string.plugin_install_retry)
                    error != null -> stringResource(R.string.plugin_install_retry)
                    else -> stringResource(R.string.plugin_install_download)
                },
                onClick = { vm.install() },
                enabled = progress == null,
                variant = ZsButtonVariant.Purple,
                modifier = Modifier.fillMaxWidth(),
                buttonModifier = Modifier.fillMaxWidth().height(52.dp),
            )

            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(12.dp))
                ZsTextButton(
                    text = stringResource(R.string.plugin_install_dev_sideload),
                    onClick = {
                        val path = context.getExternalFilesDir(null)
                            ?.resolve("plugin-debug.apk")?.absolutePath
                            ?: "/sdcard/Android/data/com.zstream.android/files/plugin-debug.apk"
                        vm.debugSideload(path)
                        onContinue?.invoke()
                    },
                    enabled = progress == null,
                )
            }
        }
    }
}

/** Minimal full-screen loading indicator shown during the plugin startup check. */
@Composable
fun PluginLoadingScreen() {
    val theme = LocalZStreamTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pluginScreenBackground(theme.colors.type.logo, theme.colors.background.main)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = theme.colors.type.logo)
    }
}

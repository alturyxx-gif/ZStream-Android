package com.zstream.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.zstream.android.BuildConfig
import com.zstream.android.Urls
import com.zstream.android.data.AccountSession
import com.zstream.android.data.local.entity.SettingsEntity
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.theme.ZStreamTheme

@Composable
fun SettingsScreen(
    nav: NavController,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val activity = LocalActivity.current as ComponentActivity
    val accountVm: AccountViewModel = hiltViewModel(activity)
    val theme = LocalZStreamTheme.current
    val session by accountVm.session.collectAsState()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val currentTab by vm.currentTab.collectAsStateWithLifecycle()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val tabs = listOf("Account", "Preferences", "Appearance", "Subtitles", "Connections")

    Box(Modifier.fillMaxSize().background(theme.colors.background.main)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = if (dirty) 72.dp else 0.dp)
        ) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Text("Settings", color = theme.colors.type.emphasis, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            // Tab bar
            TabBar(tabs, currentTab, theme) { vm.setTab(it) }

            // Content
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (currentTab) {
                    0 -> AccountSection(session, theme, vm, accountVm, showLogoutConfirm, { showLogoutConfirm = it }, nav)
                    1 -> PreferencesSection(settings, theme, vm)
                    2 -> AppearanceSection(settings, theme, vm)
                    3 -> SubtitlesSection(settings, theme, vm)
                    4 -> ConnectionsSection(settings, theme, vm)
                }
            }
        }

        // Save bar
        AnimatedVisibility(
            visible = dirty,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SaveBar(theme, vm)
        }
    }
}


@Composable
private fun TabBar(tabs: List<String>, selected: Int, theme: ZStreamTheme, onSelect: (Int) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = selected,
        containerColor = Color.Transparent,
        contentColor = theme.colors.global.accentA,
        edgePadding = 16.dp,
        indicator = {},
        divider = {},
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = index == selected,
                onClick = { onSelect(index) },
                text = {
                    Text(
                        title,
                        fontSize = 13.sp,
                        fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (index == selected) theme.colors.type.emphasis else theme.colors.type.dimmed,
                    )
                },
            )
        }
    }
    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
}

//  Reusable Components 

@Composable
private fun SectionLabel(text: String, theme: ZStreamTheme) {
    Text(
        text.uppercase(),
        color = theme.colors.type.secondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsCard(theme: ZStreamTheme, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
            .background(theme.colors.settings.card.background),
        content = content
    )
}

@Composable
private fun SettingsRow(label: String, value: String, theme: ZStreamTheme) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = theme.colors.type.text, fontSize = 14.sp)
        Text(value, color = theme.colors.type.secondary, fontSize = 13.sp, maxLines = 1, modifier = Modifier.widthIn(max = 200.dp))
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String? = null,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    disabled: Boolean = false,
    indent: Boolean = false,
    theme: ZStreamTheme,
    notice: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !disabled) { onToggle(!enabled) }
            .padding(start = if (indent) 32.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            .then(if (disabled) Modifier.alpha(0.5f) else Modifier)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, color = theme.colors.type.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (description != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(description, color = theme.colors.type.dimmed, fontSize = 11.sp, lineHeight = 16.sp)
                }
                if (notice != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(notice, color = theme.colors.type.danger, fontSize = 10.sp)
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = { if (!disabled) onToggle(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = theme.colors.global.accentA,
                    uncheckedThumbColor = theme.colors.type.dimmed,
                    uncheckedTrackColor = theme.colors.background.secondary,
                ),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

//  Save Bar 

@Composable
private fun SaveBar(theme: ZStreamTheme, vm: SettingsViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(theme.colors.settings.saveBar.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Unsaved changes", color = theme.colors.type.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = vm::resetToLocal) {
                Text("Reset", color = theme.colors.type.secondary, fontSize = 13.sp)
            }
            Button(
                onClick = vm::saveToRemote,
                colors = ButtonDefaults.buttonColors(containerColor = theme.colors.global.accentA),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Save", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

//  Account Section 

@Composable
private fun AccountSection(
    session: com.zstream.android.data.AccountSession?,
    theme: ZStreamTheme,
    vm: SettingsViewModel,
    accountVm: AccountViewModel,
    showLogoutConfirm: Boolean,
    onLogoutConfirmChange: (Boolean) -> Unit,
    nav: NavController,
) {
    var nickname by remember(session) { mutableStateOf(session?.nickname ?: "") }
    var deviceName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.padding(bottom = 32.dp)) {
        Spacer(Modifier.height(8.dp))
        SectionLabel("Account", theme)

        if (session != null) {
            SettingsCard(theme) {
                SettingsRow("Nickname", nickname.ifEmpty { "—" }, theme)
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                SettingsRow("User ID", session.userId, theme)
            }
            Spacer(Modifier.height(12.dp))
            SettingsCard(theme) {
                TextButton(
                    onClick = { onLogoutConfirmChange(true) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Text("Log out", color = theme.colors.buttons.danger, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            SettingsCard(theme) {
                TextButton(
                    onClick = { nav.navigate("login") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Text("Log in / Register", color = theme.colors.type.link, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("App Info", theme)
        SettingsCard(theme) {
            SettingsRow("Backend URL", com.zstream.android.Urls.BACKEND.removePrefix("https://").removePrefix("http://"), theme)
            HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
            SettingsRow("App Version", com.zstream.android.BuildConfig.VERSION_NAME, theme)
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { onLogoutConfirmChange(false) },
            containerColor = theme.colors.modal.background,
            title = { Text("Log out?", color = theme.colors.type.emphasis) },
            text = { Text("You will be signed out of your account.", color = theme.colors.type.text) },
            confirmButton = {
                TextButton(onClick = {
                    accountVm.logout()
                    onLogoutConfirmChange(false)
                }) { Text("Log out", color = theme.colors.buttons.danger) }
            },
            dismissButton = {
                TextButton(onClick = { onLogoutConfirmChange(false) }) {
                    Text("Cancel", color = theme.colors.type.secondary)
                }
            }
        )
    }
}

//  Preferences Section 

@Composable
private fun PreferencesSection(settings: SettingsEntity, theme: ZStreamTheme, vm: SettingsViewModel) {
    Column(Modifier.padding(bottom = 32.dp)) {
        Spacer(Modifier.height(8.dp))
        SectionLabel("Language", theme)
        SettingsCard(theme) {
            SettingsRow("Application Language", settings.applicationLanguage.uppercase(), theme)
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Playback", theme)
        SettingsCard(theme) {
            ToggleRow("Autoplay", "Automatically play the next episode", settings.enableAutoplay, vm::setEnableAutoplay, theme = theme, disabled = settings.enableLowPerformanceMode)
            if (settings.enableAutoplay && !settings.enableLowPerformanceMode) {
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                ToggleRow("Skip Credits", "Auto-skip opening credits", settings.enableSkipCredits, vm::setEnableSkipCredits, theme = theme, indent = true)
            }
            HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
            ToggleRow("Low Performance Mode", "Disable animations and visual effects", settings.enableLowPerformanceMode, vm::setEnableLowPerformanceMode, theme = theme)
            HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
            ToggleRow("Native Subtitles", "Use native subtitle renderer", settings.enableNativeSubtitles, vm::setEnableNativeSubtitles, theme = theme)
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Player Controls", theme)
        SettingsCard(theme) {
            ToggleRow("Hold to Boost", "Hold to increase playback speed", settings.enableHoldToBoost, vm::setEnableHoldToBoost, theme = theme)
            HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
            ToggleRow("Double-click to Seek", "Double-tap to seek forward/backward", settings.enableDoubleClickToSeek, vm::setEnableDoubleClickToSeek, theme = theme)
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Sources", theme)
        SettingsCard(theme) {
            ToggleRow("Manual Source Selection", "Choose sources manually", settings.manualSourceSelection, vm::setManualSourceSelection, theme = theme)
            HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
            ToggleRow("Auto-resume on Error", "Automatically try next source on playback error", settings.enableAutoResumeOnPlaybackError, vm::setEnableAutoResumeOnPlaybackError, theme = theme)
        }
    }
}

//  Appearance Section

@Composable
private fun AppearanceSection(settings: SettingsEntity, theme: ZStreamTheme, vm: SettingsViewModel) {
    Column(Modifier.padding(bottom = 32.dp)) {
        Spacer(Modifier.height(8.dp))
        SectionLabel("Home Page", theme)
        SettingsCard(theme) {
            ToggleRow("Discover", "Show discover section on home", settings.enableDiscover, vm::setEnableDiscover, theme = theme, disabled = settings.enableLowPerformanceMode)
            if (settings.enableDiscover && !settings.enableLowPerformanceMode) {
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                ToggleRow("Featured", "Show featured content in discover", settings.enableFeatured, { vm.setEnableFeatured(it); if (!it) vm.setEnableFeatured(false) }, theme = theme, indent = true)
            }
            HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
            ToggleRow("Details Modal", "Show details in a modal instead of full page", settings.enableDetailsModal, vm::setEnableDetailsModal, theme = theme, disabled = settings.enableLowPerformanceMode)
            HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
            ToggleRow("Image Logos", "Show logo images for media", settings.enableImageLogos, vm::setEnableImageLogos, theme = theme, disabled = settings.enableLowPerformanceMode)
            HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
            ToggleRow("Carousel View", "Horizontal carousel for media rows", settings.enableCarouselView, vm::setEnableCarouselView, theme = theme)
            HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
            ToggleRow("Minimal Cards", "Compact card display", settings.enableMinimalCards, vm::setEnableMinimalCards, theme = theme)
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Player UI", theme)
        SettingsCard(theme) {
            ToggleRow("Pause Overlay", "Show overlay when paused", settings.enablePauseOverlay, vm::setEnablePauseOverlay, theme = theme, disabled = settings.enableLowPerformanceMode)
            HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
            ToggleRow("Compact Episode View", "Compact episode list in player", settings.forceCompactEpisodeView, vm::setForceCompactEpisodeView, theme = theme, disabled = settings.enableLowPerformanceMode)
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Theme", theme)
        SettingsCard(theme) {
            SettingsRow("Active Theme", settings.applicationTheme.replaceFirstChar { it.uppercase() }, theme)
        }
    }
}

//  Subtitles Section 

@Composable
private fun SubtitlesSection(settings: SettingsEntity, theme: ZStreamTheme, vm: SettingsViewModel) {
    Column(Modifier.padding(bottom = 32.dp)) {
        Spacer(Modifier.height(8.dp))
        SectionLabel("Subtitles", theme)
        SettingsCard(theme) {
            ToggleRow("Native Subtitles", "Use system subtitle renderer", settings.enableNativeSubtitles, vm::setEnableNativeSubtitles, theme = theme)
            HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Default Language", color = theme.colors.type.text, fontSize = 14.sp)
                Text(settings.defaultSubtitleLanguage ?: "None", color = theme.colors.type.secondary, fontSize = 13.sp)
            }
        }
    }
}

//  Connections Section 

@Composable
private fun ConnectionsSection(settings: SettingsEntity, theme: ZStreamTheme, vm: SettingsViewModel) {
    Column(Modifier.padding(bottom = 32.dp)) {
        Spacer(Modifier.height(8.dp))
        SectionLabel("Proxy", theme)
        SettingsCard(theme) {
            ToggleRow("Proxy TMDB", "Route TMDB requests through proxy", settings.proxyTmdb, vm::setProxyTmdb, theme = theme)
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("External Services", theme)
        SettingsCard(theme) {
            SettingsRow("Debrid Service", settings.debridService.replaceFirstChar { it.uppercase() }, theme)
            if (settings.debridToken != null) {
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                SettingsRow("Debrid Token", "••••••••", theme)
            }
            HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
            if (settings.febboxKey != null) {
                SettingsRow("Febbox Key", "••••••••", theme)
            } else {
                SettingsRow("Febbox", "Not configured", theme)
            }
            HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
            if (settings.tidbKey != null) {
                SettingsRow("TheIntroDB Key", "••••••••", theme)
            } else {
                SettingsRow("TheIntroDB", "Not configured", theme)
            }
        }
    }
}

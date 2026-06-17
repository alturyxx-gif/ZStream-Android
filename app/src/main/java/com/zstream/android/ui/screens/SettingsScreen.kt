package com.zstream.android.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zstream.android.BuildConfig
import com.zstream.android.Urls
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.theme.ZStreamTheme

@Composable
fun SettingsScreen(nav: NavController) {
    val activity = LocalActivity.current as ComponentActivity
    val accountVm: AccountViewModel = hiltViewModel(activity)
    val theme = LocalZStreamTheme.current
    val session by accountVm.session.collectAsState()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background.main)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
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

        // ── Account section ────────────────────────────────────────────────
        SectionLabel("Account", theme)

        if (session != null) {
            SettingsCard(theme) {
                SettingsRow("Nickname", session!!.nickname.ifEmpty { "—" }, theme)
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.3f))
                SettingsRow("User ID", session!!.userId, theme)
            }
            Spacer(Modifier.height(12.dp))
            SettingsCard(theme) {
                TextButton(
                    onClick = { showLogoutConfirm = true },
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

        // ── App info section ───────────────────────────────────────────────
        SectionLabel("App Info", theme)

        SettingsCard(theme) {
            SettingsRow("Backend URL", Urls.BACKEND.removePrefix("https://").removePrefix("http://"), theme)
            HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.3f))
            SettingsRow("App Version", BuildConfig.VERSION_NAME, theme)
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = theme.colors.modal.background,
            title = { Text("Log out?", color = theme.colors.type.emphasis) },
            text = { Text("You will be signed out of your account.", color = theme.colors.type.text) },
            confirmButton = {
                TextButton(onClick = {
                    accountVm.logout()
                    showLogoutConfirm = false
                }) { Text("Log out", color = theme.colors.buttons.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel", color = theme.colors.type.secondary)
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String, theme: ZStreamTheme) {
    Text(
        text.uppercase(),
        color = theme.colors.type.secondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsCard(theme: ZStreamTheme, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
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
        Text(label, color = theme.colors.type.text, fontSize = 15.sp)
        Text(
            value,
            color = theme.colors.type.secondary,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

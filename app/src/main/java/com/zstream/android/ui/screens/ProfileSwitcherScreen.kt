package com.zstream.android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.zstream.android.data.SavedProfile
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.ui.components.themed.ZsButton
import com.zstream.android.ui.components.themed.ZsCard
import com.zstream.android.ui.components.themed.ZsCardVariant
import com.zstream.android.ui.components.themed.ZsIconButton
import com.zstream.android.ui.components.themed.ZsIconButtonVariant
import com.zstream.android.ui.components.themed.ZsButtonVariant
import com.zstream.android.ui.navigation.rememberSafeNavigateBack

/** TV-only: lets a shared TV switch between multiple cached logins without re-authenticating each time. */
@Composable
fun ProfileSwitcherScreen(nav: NavController, vm: AccountViewModel = hiltViewModel()) {
    val theme = LocalZStreamTheme.current
    val scope = rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val session by vm.session.collectAsStateWithLifecycle()
    val savedProfiles by vm.savedProfiles.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<String?>(null) }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(theme.colors.background.main)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZsIconButton(onClick = onBack, icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", variant = ZsIconButtonVariant.Ghost)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Switch Profile", color = theme.colors.type.emphasis, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Pick who's watching on this TV", color = theme.colors.type.secondary, fontSize = 13.sp)
                }
            }

            if (savedProfiles.isEmpty()) {
                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Add, null, tint = theme.colors.global.accentA, modifier = Modifier.size(40.dp))
                        Text("No saved profiles yet", color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Sync to cloud to add the first profile on this TV.", color = theme.colors.type.secondary, fontSize = 13.sp)
                    }
                }
            }

            savedProfiles.sortedByDescending { it.lastActiveAt }.forEach { profile ->
                key(profile.id) {
                    val isActive = profile.userId == session?.userId
                    ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isActive) { vm.switchProfile(profile.id) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.global.accentA.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    (profile.nickname.ifBlank { profile.deviceName }.ifBlank { "?" }).take(1).uppercase(),
                                    color = theme.colors.global.accentA,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    profile.nickname.ifBlank { profile.deviceName.ifBlank { "Profile" } },
                                    color = theme.colors.type.emphasis,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                )
                                if (isActive) {
                                    Text("Currently watching", color = theme.colors.global.accentA, fontSize = 12.sp)
                                }
                            }
                            if (isActive) {
                                Icon(Icons.Default.CheckCircle, null, tint = theme.colors.type.success, modifier = Modifier.size(20.dp))
                            }
                            ZsIconButton(
                                onClick = { removing = profile.id },
                                icon = Icons.Default.Close,
                                contentDescription = "Remove profile",
                                variant = ZsIconButtonVariant.Ghost,
                            )
                        }
                    }
                    if (removing == profile.id) {
                        ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "Remove \"${profile.nickname.ifBlank { profile.deviceName }}\" from this TV?",
                                    color = theme.colors.type.emphasis,
                                    fontSize = 14.sp,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ZsButton(text = "Cancel", onClick = { removing = null }, variant = ZsButtonVariant.Secondary)
                                    ZsButton(text = "Remove", onClick = { vm.removeProfile(profile.id); removing = null })
                                }
                            }
                        }
                    }
                }
            }

            ZsButton(
                text = "Add Profile",
                leadingIcon = Icons.Default.Add,
                onClick = { nav.navigate("login") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

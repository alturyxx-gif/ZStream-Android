package com.zstream.android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.zstream.android.data.SavedProfile
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.ui.components.themed.ZsIconButton
import com.zstream.android.ui.components.themed.ZsIconButtonVariant
import com.zstream.android.ui.components.themed.ZsOutlinedWrapper
import com.zstream.android.ui.navigation.rememberSafeNavigateBack

private val PROFILE_CARD_WIDTH = 160.dp

/** TV-only: Netflix-style "who's watching" picker for switching between cached logins on this TV. */
@Composable
fun ProfileSwitcherScreen(nav: NavController, vm: AccountViewModel = hiltViewModel()) {
    val theme = LocalZStreamTheme.current
    val scope = rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val session by vm.session.collectAsStateWithLifecycle()
    val savedProfiles by vm.savedProfiles.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<String?>(null) }
    val firstProfileFocusRequester = remember { FocusRequester() }
    val profiles = savedProfiles.sortedByDescending { it.lastActiveAt }

    LaunchedEffect(profiles) {
        firstProfileFocusRequester.requestFocus()
    }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(theme.colors.background.main)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 40.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Who's watching?",
                color = theme.colors.type.emphasis,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
            ) {
                items(profiles) { profile ->
                    val isActive = profile.userId == session?.userId
                    ProfileCard(
                        profile = profile,
                        isActive = isActive,
                        removing = removing == profile.id,
                        onClick = {
                            if (!isActive) vm.switchProfile(profile.id)
                            onBack()
                        },
                        onRequestRemove = { removing = profile.id },
                        onConfirmRemove = { vm.removeProfile(profile.id); removing = null },
                        onCancelRemove = { removing = null },
                        theme = theme,
                        modifier = if (profile == profiles.firstOrNull()) Modifier.focusRequester(firstProfileFocusRequester) else Modifier,
                    )
                }
                item {
                    AddProfileCard(
                        theme = theme,
                        onClick = { nav.navigate("login") },
                        modifier = if (profiles.isEmpty()) Modifier.focusRequester(firstProfileFocusRequester) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: SavedProfile,
    isActive: Boolean,
    removing: Boolean,
    onClick: () -> Unit,
    onRequestRemove: () -> Unit,
    onConfirmRemove: () -> Unit,
    onCancelRemove: () -> Unit,
    theme: com.zstream.android.theme.ZStreamTheme,
    modifier: Modifier = Modifier,
) {
    val displayName = profile.nickname.ifBlank { profile.deviceName.ifBlank { "Profile" } }
    val initial = displayName.take(1).uppercase()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = Modifier.width(PROFILE_CARD_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ZsOutlinedWrapper(
            visible = isFocused,
            shape = RoundedCornerShape(10.dp),
            outlineColor = theme.colors.global.accentA,
            gap = 3.dp,
        ) {
        Box(
            modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.colors.global.accentA.copy(alpha = if (isActive) 0.45f else 0.22f))
                .clickable(interactionSource = interactionSource, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initial,
                color = theme.colors.global.accentA,
                fontWeight = FontWeight.Bold,
                fontSize = 64.sp,
            )
            ZsIconButton(
                onClick = onRequestRemove,
                icon = Icons.Default.Close,
                contentDescription = "Remove profile",
                variant = ZsIconButtonVariant.Ghost,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
            if (removing) {
                Box(
                    Modifier.fillMaxSize().background(theme.colors.background.main.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Remove?", color = theme.colors.type.emphasis, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(theme.colors.type.danger)
                                    .clickable { onConfirmRemove() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) { Text("Yes", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(theme.colors.background.secondary)
                                    .clickable { onCancelRemove() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) { Text("Cancel", color = theme.colors.type.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            displayName,
            color = if (isActive) theme.colors.type.emphasis else theme.colors.type.secondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (isActive) {
            Text("Currently watching", color = theme.colors.global.accentA, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (profile.kidsModeEnabled) {
            Text("Kids Profile", color = theme.colors.type.secondary, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AddProfileCard(theme: com.zstream.android.theme.ZStreamTheme, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Column(
        modifier = Modifier.width(PROFILE_CARD_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ZsOutlinedWrapper(
            visible = isFocused,
            shape = RoundedCornerShape(10.dp),
            outlineColor = theme.colors.global.accentA,
            gap = 3.dp,
        ) {
        Box(
            modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.colors.background.secondary)
                .clickable(interactionSource = interactionSource, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(theme.colors.background.main),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, null, tint = theme.colors.type.secondary, modifier = Modifier.size(24.dp))
            }
        }
        }
        Spacer(Modifier.height(10.dp))
        Text("Add Profile", color = theme.colors.type.secondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

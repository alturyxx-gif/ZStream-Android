package com.zstream.android.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.window.Dialog
import com.zstream.android.plugin.SourceInfo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.zstream.android.BuildConfig
import com.zstream.android.Urls
import com.zstream.android.data.AccountSession
import com.zstream.android.data.TraktState
import com.zstream.android.data.adb.ReleaseCheckInterval
import com.zstream.android.data.local.entity.BookmarkEntity
import com.zstream.android.data.local.entity.SettingsEntity
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.theme.ThemeRegistry
import com.zstream.android.theme.ZStreamTheme
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.components.themed.ZsBottomSheetSectionCard
import com.zstream.android.ui.components.themed.ZsDropdownRow
import com.zstream.android.ui.components.themed.ZsButton
import com.zstream.android.ui.components.themed.ZsButtonVariant
import com.zstream.android.ui.components.themed.ZsIconButton
import com.zstream.android.ui.components.themed.ZsIconButtonVariant
import com.zstream.android.ui.components.themed.ZsOutlinedWrapper
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant
import com.zstream.android.ui.components.themed.ZsSwitchRow
import com.zstream.android.ui.components.themed.ZsTextButton
import com.zstream.android.ui.components.themed.ZsTextField
import com.zstream.android.ui.components.themed.ZsThemePreviewCard
import com.zstream.android.ui.navigation.rememberSafeNavigateBack
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

private fun parseGroupLabel(group: String): Pair<String, String> {
    val match = Regex("^\\[([A-Za-z0-9_]+)](.*)$").find(group)
    return if (match != null) {
        val icon = match.groupValues.getOrNull(1).orEmpty().uppercase()
        val name = match.groupValues.getOrNull(2).orEmpty().trim()
        icon to name.ifBlank { group }
    } else {
        "" to group
    }
}

private sealed interface TokenValidationState {
    data object Idle : TokenValidationState
    data object Checking : TokenValidationState
    data object Valid : TokenValidationState
    data class Invalid(val message: String) : TokenValidationState
}

@Composable
fun SettingsScreen(
    nav: NavController,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val isTv = LocalIsTv.current
    if (isTv) {
        TvSettingsScreen(nav = nav, vm = vm)
    } else {
        PhoneSettingsScreen(nav = nav, vm = vm)
    }
}

// ─── TV Settings Layout ───────────────────────────────────────────────────────

@Composable
private fun TvSettingsScreen(
    nav: NavController,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val activity = LocalActivity.current as ComponentActivity
    val accountVm: AccountViewModel = hiltViewModel(activity)
    val theme = LocalZStreamTheme.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val session by accountVm.session.collectAsState()
    val bookmarks by accountVm.bookmarks.collectAsState()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val currentTab by vm.currentTab.collectAsStateWithLifecycle()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                val json = vm.exportDataJson()
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(json.toByteArray())
                }
            }
        }
    }

    val tabs = listOf("Account", "Preferences", "Appearance", "Player", "Subtitles", "Connections")
    val tabFocusRequesters = remember { List(tabs.size) { FocusRequester() } }
    val contentFirstItemFocusRequester = remember { FocusRequester() }
    var isContentFocused by remember { mutableStateOf(false) }

    BackHandler(enabled = isContentFocused) {
        tabFocusRequesters[currentTab].requestFocus()
    }

    LaunchedEffect(Unit) {
        tabFocusRequesters[0].requestFocus()
    }

    Box(Modifier
        .fillMaxSize()
        .background(theme.colors.background.main)) {
        Row(Modifier.fillMaxSize()) {
            // ── Left side-nav ──────────────────────────────────────────────
            Column(
                Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .focusRestorer(tabFocusRequesters[currentTab])
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                theme.colors.background.secondary,
                                theme.colors.background.main,
                            )
                        )
                    )
                    .padding(top = 32.dp, bottom = 24.dp)
            ) {
                // Back button
                var backFocused by remember { mutableStateOf(false) }
                ZsOutlinedWrapper(
                    shape = RoundedCornerShape(10.dp),
                    outlineColor = Color.White,
                    outlineWidth = 2.dp,
                    horizontal = (-18).dp,
                    vertical = 0.dp,
                    visible = backFocused,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .onFocusChanged { backFocused = it.isFocused }
                            .clickable { onBack() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = if (backFocused) Color.White else theme.colors.type.secondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "Back",
                            color = if (backFocused) Color.White else theme.colors.type.secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "SETTINGS",
                    color = theme.colors.type.dimmed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )

                // Tab items
                tabs.forEachIndexed { index, title ->
                    TvSettingsTabItem(
                        title = title,
                        selected = currentTab == index,
                        theme = theme,
                        onSelect = { vm.setTab(index) },
                        focusRequester = tabFocusRequesters[index],
                        contentFocusRequester = contentFirstItemFocusRequester,
                    )
                }

                Spacer(Modifier.weight(1f))
            }

            // Divider
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(theme.colors.utils.divider.copy(alpha = 0.15f))
            )

            // ── Right content area ─────────────────────────────────────────
            val subtitlesSettingsTabIndex = 4
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged { isContentFocused = it.hasFocus }
            ) {
                if (currentTab == subtitlesSettingsTabIndex) {
                    Column(Modifier.fillMaxSize()) {
                        SubtitlePreview(settings, theme, vm, isTv = true)
                        Column(Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp)) {
                            SubtitlesSettingsContent(
                                settings = settings,
                                theme = theme,
                                vm = vm,
                                isTv = true,
                                firstItemFocusRequester = contentFirstItemFocusRequester
                            )
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (currentTab) {
                            0 -> AccountSection(
                                session, theme, vm, accountVm, showLogoutConfirm,
                                { showLogoutConfirm = it }, nav,
                                onExport = { exportLauncher.launch("zstream_backup_${System.currentTimeMillis()}.json") },
                                isTv = true,
                                firstItemFocusRequester = contentFirstItemFocusRequester,
                            )
                            1 -> PreferencesSection(settings, bookmarks, theme, vm, isTv = true, firstItemFocusRequester = contentFirstItemFocusRequester)
                            2 -> AppearanceSection(settings, theme, vm, nav, isTv = true, firstItemFocusRequester = contentFirstItemFocusRequester)
                            3 -> PlayerSection(settings, theme, vm, isTv = true, firstItemFocusRequester = contentFirstItemFocusRequester)
                            5 -> ConnectionsSection(settings, theme, vm, isTv = true, firstItemFocusRequester = contentFirstItemFocusRequester)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntegrationActionOutline(
    theme: ZStreamTheme,
    content: @Composable () -> Unit,
) {
    ZsOutlinedWrapper(
        shape = RoundedCornerShape(12.dp),
        outlineColor = theme.colors.type.divider.copy(alpha = 0.35f),
        outlineWidth = 1.dp,
        gap = 2.dp,
        content = content,
    )
}

@Composable
private fun WyzieIntegrationCard(
    settings: SettingsEntity,
    theme: ZStreamTheme,
    vm: SettingsViewModel,
    context: android.content.Context,
) {
    SettingsCard(theme) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Wyzie Subtitles", color = theme.colors.type.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Optional API key for better-synced subtitles. Get one at store.wyzie.io/redeem.",
                color = theme.colors.type.dimmed,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text("API Key", color = theme.colors.type.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            var tokenVisible by remember { mutableStateOf(false) }
            var wyzieInput by remember(settings.wyzieKey) { mutableStateOf(settings.wyzieKey.orEmpty()) }
            var validationState by remember { mutableStateOf<TokenValidationState>(TokenValidationState.Idle) }

            LaunchedEffect(wyzieInput) {
                if (wyzieInput.isBlank()) {
                    validationState = TokenValidationState.Idle
                    vm.setWyzieKey(null)
                    return@LaunchedEffect
                }
                validationState = TokenValidationState.Checking
                delay(600)
                validationState = vm.validateWyzieKey(wyzieInput).fold(
                    onSuccess = { valid ->
                        if (valid) {
                            vm.setWyzieKey(wyzieInput)
                            TokenValidationState.Valid
                        } else {
                            vm.setWyzieKey(null)
                            TokenValidationState.Invalid("Key was rejected by Wyzie.")
                        }
                    },
                    onFailure = { TokenValidationState.Invalid(it.message ?: "Validation failed.") },
                )
            }
            BasicTextField(
                value = wyzieInput,
                onValueChange = { wyzieInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.colors.background.secondary)
                    .border(1.dp, theme.colors.type.divider.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                textStyle = TextStyle(color = theme.colors.type.text, fontSize = 12.sp),
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (wyzieInput.isEmpty()) {
                            Text("wyzie-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", color = theme.colors.type.dimmed, fontSize = 12.sp)
                        }
                        innerTextField()
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IntegrationActionOutline(theme) {
                    TextButton(onClick = { tokenVisible = !tokenVisible }) {
                        Text(if (tokenVisible) "Hide" else "Show", color = theme.colors.global.accentA, fontSize = 11.sp)
                    }
                }
                IntegrationActionOutline(theme) {
                    TextButton(
                        onClick = {
                            wyzieInput = ""
                            vm.setWyzieKey(null)
                        },
                        enabled = wyzieInput.isNotEmpty(),
                    ) {
                        Text(
                            "Clear",
                            color = if (wyzieInput.isNotEmpty()) theme.colors.buttons.danger else theme.colors.type.dimmed,
                            fontSize = 11.sp,
                        )
                    }
                }
                IntegrationActionOutline(theme) {
                    TextButton(onClick = {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://store.wyzie.io/redeem"),
                            )
                        )
                    }) {
                        Text("Get API key", color = theme.colors.global.accentA, fontSize = 11.sp)
                    }
                }
            }
            when (val state = validationState) {
                TokenValidationState.Idle -> Unit
                TokenValidationState.Checking -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = theme.colors.global.accentA)
                    Spacer(Modifier.width(8.dp))
                    Text("Checking key…", color = theme.colors.type.dimmed, fontSize = 11.sp)
                }
                TokenValidationState.Valid -> ZsStatusBanner(
                    message = "Key accepted by Wyzie.",
                    variant = ZsStatusBannerVariant.Success,
                    modifier = Modifier.padding(top = 4.dp),
                )
                is TokenValidationState.Invalid -> ZsStatusBanner(
                    message = state.message,
                    variant = ZsStatusBannerVariant.Error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TraktIntegrationCard(
    trakt: TraktState,
    theme: ZStreamTheme,
    vm: SettingsViewModel,
    context: android.content.Context,
) {
    SettingsCard(theme) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Trakt", color = theme.colors.type.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Sync your watchlist and history with Trakt.", color = theme.colors.type.dimmed, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Syncing might take a few minutes to complete. Changes on Trakt may conflict with local changes, so local items are prioritized.",
                color = theme.colors.type.dimmed,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            when {
                trakt.activationCode != null -> Text("Enter code ${trakt.activationCode} at trakt.tv/activate", color = theme.colors.type.text, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                trakt.syncing -> Text("Syncing…", color = theme.colors.type.dimmed, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                trakt.lastError != null -> Text(trakt.lastError.orEmpty(), color = theme.colors.buttons.danger, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(10.dp))
            if (trakt.connected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        trakt.avatar?.let {
                            coil.compose.AsyncImage(
                                model = it,
                                contentDescription = "Trakt profile avatar",
                                modifier = Modifier.size(28.dp).clip(CircleShape),
                            )
                        }
                        Text(trakt.name ?: trakt.username ?: "Connected", color = theme.colors.type.text, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntegrationActionOutline(theme) {
                            ZsButton(
                                text = "Sync now",
                                onClick = vm::syncTrakt,
                                enabled = !trakt.syncing,
                                loading = trakt.syncing,
                                variant = ZsButtonVariant.Secondary,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                        IntegrationActionOutline(theme) {
                            ZsButton(
                                text = "Disconnect",
                                onClick = vm::disconnectTrakt,
                                variant = ZsButtonVariant.Danger,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            } else {
                IntegrationActionOutline(theme) {
                    ZsButton(
                        text = "Connect Trakt",
                        onClick = { vm.connectTrakt(context) },
                        variant = ZsButtonVariant.Purple,
                        loading = trakt.syncing,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SimklIntegrationCard(theme: ZStreamTheme) {
    SettingsCard(theme) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Simkl", color = theme.colors.type.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Sync your watchlist and history with Simkl.", color = theme.colors.type.dimmed, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            UnsupportedIntegrationStatus(theme)
        }
    }
}

@Composable
private fun UnsupportedIntegrationStatus(theme: ZStreamTheme) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(theme.colors.background.secondary).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text("Not available on Android yet", color = theme.colors.type.dimmed, fontSize = 11.sp)
    }
}

/** Vertical tab item for the TV side-nav. */
@Composable
private fun TvSettingsTabItem(
    title: String,
    selected: Boolean,
    theme: ZStreamTheme,
    onSelect: () -> Unit,
    focusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        when {
            selected -> theme.colors.global.accentA.copy(alpha = 0.25f)
            isFocused -> theme.colors.background.secondary.copy(alpha = 0.6f)
            else -> Color.Transparent
        }
    )
    val textColor by animateColorAsState(
        if (selected || isFocused) theme.colors.type.emphasis else theme.colors.type.dimmed
    )

    // Vertical Tab Item
    ZsOutlinedWrapper(
        shape = RoundedCornerShape(14.dp),
        outlineColor = Color.White,
        outlineWidth = 2.dp,
        gap = 4.dp,
        visible = isFocused,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (contentFocusRequester != null) Modifier.focusProperties {
                right = contentFocusRequester
            } else Modifier),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .then(
                    if (selected) Modifier.drawBehind {
                        drawRoundRect(
                            color = theme.colors.global.accentA,
                            size = androidx.compose.ui.geometry.Size(
                                4.dp.toPx(),
                                size.height * 0.6f
                            ),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                x = 0f,
                                y = size.height * 0.2f,
                            ),
                        )
                    } else Modifier
                )
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused && !selected) {
                        onSelect()
                    }
                }
                .clickable { onSelect() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            if (selected) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(theme.colors.global.accentA)
                )
            }
        }
    }
}

// ─── Phone Settings Layout ────────────────────────────────────────────────────

@Composable
private fun PhoneSettingsScreen(
    nav: NavController,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val activity = LocalActivity.current as ComponentActivity
    val accountVm: AccountViewModel = hiltViewModel(activity)
    val theme = LocalZStreamTheme.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val session by accountVm.session.collectAsState()
    val bookmarks by accountVm.bookmarks.collectAsState()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val currentTab by vm.currentTab.collectAsStateWithLifecycle()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                val json = vm.exportDataJson()
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(json.toByteArray())
                }
            }
        }
    }

    val tabs = listOf("Account", "Preferences", "Appearance", "Player", "Subtitles", "Connections")
    val tabFocusRequesters = remember { List(tabs.size) { FocusRequester() } }
    val contentFirstItemFocusRequester = remember { FocusRequester() }
    var isContentFocused by remember { mutableStateOf(false) }

    BackHandler(enabled = isContentFocused) {
        tabFocusRequesters[currentTab].requestFocus()
    }

    LaunchedEffect(Unit) {
        tabFocusRequesters[0].requestFocus()
    }

    Box(Modifier
        .fillMaxSize()
        .background(theme.colors.background.main)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Text("Settings", color = theme.colors.type.emphasis, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            // Tab bar
            TabBar(tabs, currentTab, theme) { vm.setTab(it) }

            // Content
            val subtitlesSettingsTabIndex = 4
            if (currentTab == subtitlesSettingsTabIndex) {
                Column(Modifier.fillMaxSize()) {
                    SubtitlePreview(settings, theme, vm, isTv = false)
                    Column(Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())) {
                        SubtitlesSettingsContent(settings, theme, vm)
                    }
                }
            } else {
                Box(Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())) {
                    when (currentTab) {
                        0 -> AccountSection(session, theme, vm, accountVm, showLogoutConfirm, { showLogoutConfirm = it }, nav, onExport = {
                            exportLauncher.launch("zstream_backup_${System.currentTimeMillis()}.json")
                        })
                        1 -> PreferencesSection(settings, bookmarks, theme, vm)
                        2 -> AppearanceSection(settings, theme, vm, nav)
                        3 -> PlayerSection(settings, theme, vm)
                        5 -> ConnectionsSection(settings, theme, vm)
                    }
                }
            }
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
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.colors.settings.card.background),
        content = content
    )
}

@Composable
private fun PluginVersionSection(vm: SettingsViewModel, theme: ZStreamTheme, isTv: Boolean) {
    val pluginState by vm.pluginState.collectAsStateWithLifecycle()
    val updateMessage by vm.pluginUpdateMessage.collectAsStateWithLifecycle()

    val versionLabel = when (pluginState) {
        is com.zstream.android.plugin.PluginState.Ready ->
            com.zstream.android.plugin.pluginVersionLabel((pluginState as com.zstream.android.plugin.PluginState.Ready).displayVersion)
        is com.zstream.android.plugin.PluginState.UpdateAvailable -> {
            val s = pluginState as com.zstream.android.plugin.PluginState.UpdateAvailable
            "${com.zstream.android.plugin.pluginVersionLabel(s.currentDisplayVersion)} (${com.zstream.android.plugin.pluginVersionLabel(s.stagedDisplayVersion)} ready)"
        }
        else -> "Not installed"
    }

    if (isTv) {
        TvSettingsRow(theme) {
            Text("Plugin Version", color = theme.colors.type.text, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
            Text(versionLabel, color = theme.colors.type.secondary, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
        }
        TvSettingsRow(theme, onActivate = { vm.checkPluginUpdate() }) {
            Text("Check for Plugin Update", color = theme.colors.type.link, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
        }
    } else {
        SettingsRow("Plugin Version", versionLabel, theme)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { vm.checkPluginUpdate() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Check for Plugin Update", color = theme.colors.type.link, fontSize = 14.sp)
        }
    }

    updateMessage?.takeIf { it.isNotBlank() }?.let { msg ->
        Text(
            msg,
            color = if (msg.startsWith("Update check failed")) theme.colors.type.danger else theme.colors.type.secondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String, theme: ZStreamTheme, onClick: (() -> Unit)? = null) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = theme.colors.type.text, fontSize = 14.sp)
        Text(value, color = theme.colors.type.secondary, fontSize = 13.sp, maxLines = 1, modifier = Modifier.widthIn(max = 200.dp))
    }
}

/**
 * TV-only wrapper that makes a settings row focusable with a visible outline.
 * On TV, each logical row is wrapped in this to support D-pad navigation and
 * show a white focus ring around the currently selected item.
 *
 * [onActivate] is called when the user presses the D-pad center / Enter key.
 */
@Composable
private fun TvSettingsRow(
    theme: ZStreamTheme,
    onActivate: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val bgColor by animateColorAsState(
        if (isFocused) theme.colors.background.secondary.copy(alpha = 0.5f) else Color.Transparent
    )
    ZsOutlinedWrapper(
        shape = RoundedCornerShape(12.dp),
        outlineColor = Color.White,
        outlineWidth = 2.dp,
        horizontal = 11.dp,
        vertical = 1.dp,
        visible = isFocused,
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .then(modifier)
            .then(
                if (onActivate != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = { onActivate() },
                    )
                } else {
                    Modifier.focusable()

                }
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
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
    onExport: () -> Unit,
    isTv: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
) {
    var nickname by remember(session) { mutableStateOf(session?.nickname ?: "") }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var versionTapCount by remember { mutableStateOf(0) }
    var lastVersionTapAt by remember { mutableStateOf(0L) }
    val onVersionTap: () -> Unit = onVersionTap@{
        val now = System.currentTimeMillis()
        versionTapCount = if (now - lastVersionTapAt <= 1500L) versionTapCount + 1 else 1
        lastVersionTapAt = now
        if (versionTapCount < 6) return@onVersionTap
        versionTapCount = 0
        Toast.makeText(context, "started with a spark now we're on fire", Toast.LENGTH_LONG).show()
    }

    Column(Modifier
        .padding(horizontal = if (isTv) 16.dp else 0.dp)
        .padding(bottom = 32.dp, top = if (isTv) 16.dp else 0.dp)) {
        Spacer(Modifier.height(8.dp))
        SectionLabel("Account", theme)

        if (session != null) {
            if (isTv) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.colors.settings.card.background)
                ) {
                    TvSettingsRow(
                        theme = theme,
                        modifier = if (firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                    ) {
                        Text("Nickname", color = theme.colors.type.text, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                        Text(nickname.ifEmpty { "—" }, color = theme.colors.type.secondary, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                    }
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    TvSettingsRow(theme) {
                        Text("Device Name", color = theme.colors.type.text, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                        Text(session.deviceName.ifEmpty { "Android" }, color = theme.colors.type.secondary, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                    }
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    TvSettingsRow(theme) {
                        Text("User ID", color = theme.colors.type.text, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                        Text(session.userId, color = theme.colors.type.secondary, fontSize = 13.sp, maxLines = 1, modifier = Modifier
                            .widthIn(max = 200.dp)
                            .padding(end = 12.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                // No export data button on TV
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.colors.settings.card.background)
                ) {
                    TvSettingsRow(theme, onActivate = { nav.navigate("tvSync") }) {
                        Text("Sync from phone", color = theme.colors.type.link, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 14.dp))
                    }
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    TvSettingsRow(theme, onActivate = { onLogoutConfirmChange(true) }) {
                        Text("Log out", color = theme.colors.buttons.danger, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 14.dp))
                    }
                }
            } else {
                SettingsCard(theme) {
                    SettingsRow("Nickname", nickname.ifEmpty { "—" }, theme)
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    SettingsRow("Device Name", session.deviceName.ifEmpty { "Android" }, theme)
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    SettingsRow("User ID", session.userId, theme)
                }
                Spacer(Modifier.height(12.dp))
                SettingsCard(theme) {
                    TextButton(
                        onClick = onExport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        Text("Export Data", color = theme.colors.type.link, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                SettingsCard(theme) {
                    TextButton(
                        onClick = { onLogoutConfirmChange(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        Text("Log out", color = theme.colors.buttons.danger, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            if (isTv) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.colors.settings.card.background)
                ) {
                    TvSettingsRow(
                        theme = theme,
                        onActivate = { nav.navigate("login") },
                        modifier = if (firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                    ) {
                        Text("Log in / Register", color = theme.colors.type.link, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 14.dp))
                    }
                }
            } else {
                SettingsCard(theme) {
                    TextButton(
                        onClick = { nav.navigate("login") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        Text("Log in / Register", color = theme.colors.type.link, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("App Info", theme)
        if (isTv) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.settings.card.background)
            ) {
                TvSettingsRow(theme) {
                    Text("Backend URL", color = theme.colors.type.text, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                    Text(com.zstream.android.Urls.BACKEND.removePrefix("https://").removePrefix("http://"), color = theme.colors.type.secondary, fontSize = 13.sp, maxLines = 1, modifier = Modifier
                        .widthIn(max = 260.dp)
                        .padding(end = 12.dp))
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                TvSettingsRow(theme, onActivate = onVersionTap) {
                    Text("App Version", color = theme.colors.type.text, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                    Text(com.zstream.android.BuildConfig.VERSION_NAME, color = theme.colors.type.secondary, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                PluginVersionSection(vm = vm, theme = theme, isTv = true)
            }
        } else {
            SettingsCard(theme) {
                SettingsRow("Backend URL", com.zstream.android.Urls.BACKEND.removePrefix("https://").removePrefix("http://"), theme)
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                SettingsRow("App Version", com.zstream.android.BuildConfig.VERSION_NAME, theme, onClick = onVersionTap)
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                PluginVersionSection(vm = vm, theme = theme, isTv = false)
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { onLogoutConfirmChange(false) },
            containerColor = theme.colors.modal.background,
            title = { Text("Log out?", color = theme.colors.type.emphasis) },
            text = { Text("You will be signed out of your account. Local user data (watch history and bookmarks) will be deleted, but your app settings will be preserved. Would you like to export your data first?", color = theme.colors.type.text) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        onExport()
                        onLogoutConfirmChange(false)
                    }) {
                        Text("Export", color = theme.colors.global.accentA)
                    }
                    TextButton(onClick = {
                        accountVm.logout()
                        onLogoutConfirmChange(false)
                    }) {
                        Text("Yes, Log out", color = theme.colors.buttons.danger)
                    }
                }
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
private fun PreferencesSection(
    settings: SettingsEntity, 
    bookmarks: List<BookmarkEntity>,
    theme: ZStreamTheme, 
    vm: SettingsViewModel,
    isTv: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
) {
    var showSourceOrderDialog by remember { mutableStateOf(false) }
    var showDownloadSourceOrderDialog by remember { mutableStateOf(false) }
    val sourceOrder by vm.sourceOrder.collectAsState()
    val downloadSourceOrder by vm.downloadSourceOrder.collectAsState()

    Column(Modifier
        .padding(bottom = 32.dp, top = if (isTv) 16.dp else 0.dp).padding(horizontal = if (isTv) 16.dp else 0.dp)) {
        Spacer(Modifier.height(8.dp))
        SectionLabel("Language", theme)
        if (isTv) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.settings.card.background)
            ) {
                TvSettingsRow(
                    theme = theme,
                    modifier = if (firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                ) {
                    Text("Application Language", color = theme.colors.type.text, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                    Text(settings.applicationLanguage.uppercase(), color = theme.colors.type.secondary, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                }
            }
        } else {
            SettingsCard(theme) {
                SettingsRow("Application Language", settings.applicationLanguage.uppercase(), theme)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Playback", theme)
        if (isTv) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.settings.card.background)
            ) {
                TvSettingsRow(theme, onActivate = { vm.setEnableAutoplay(!settings.enableAutoplay) }) {
                    TvSwitchContent("Autoplay", "Automatically play the next episode", settings.enableAutoplay, enabled = !settings.enableLowPerformanceMode)
                }
                if (settings.enableAutoplay && !settings.enableLowPerformanceMode) {
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    TvSettingsRow(theme, onActivate = { vm.setEnableSkipCredits(!settings.enableSkipCredits) }) {
                        TvSwitchContent("Skip Credits", "Auto-skip opening credits", settings.enableSkipCredits, indent = true)
                    }
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                TvSettingsRow(theme, onActivate = { vm.setEnableLowPerformanceMode(!settings.enableLowPerformanceMode) }) {
                    TvSwitchContent("Low Performance Mode", "Disable animations and visual effects", settings.enableLowPerformanceMode)
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                TvSettingsRow(theme, onActivate = { vm.setEnableNativeSubtitles(!settings.enableNativeSubtitles) }) {
                    TvSwitchContent("Native Subtitles", "Use native subtitle renderer", settings.enableNativeSubtitles)
                }
            }
        } else {
            SettingsCard(theme) {
                ZsSwitchRow(
                    title = "Autoplay",
                    subtitle = "Automatically play the next episode",
                    checked = settings.enableAutoplay,
                    onCheckedChange = vm::setEnableAutoplay,
                    enabled = !settings.enableLowPerformanceMode,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                if (settings.enableAutoplay && !settings.enableLowPerformanceMode) {
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    ZsSwitchRow(
                        title = "Skip Credits",
                        subtitle = "Auto-skip opening credits",
                        checked = settings.enableSkipCredits,
                        onCheckedChange = vm::setEnableSkipCredits,
                        modifier = Modifier.padding(start = 32.dp, end = 16.dp),
                    )
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                ZsSwitchRow(
                    title = "Low Performance Mode",
                    subtitle = "Disable animations and visual effects",
                    checked = settings.enableLowPerformanceMode,
                    onCheckedChange = vm::setEnableLowPerformanceMode,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                ZsSwitchRow(
                    title = "Native Subtitles",
                    subtitle = "Use native subtitle renderer",
                    checked = settings.enableNativeSubtitles,
                    onCheckedChange = vm::setEnableNativeSubtitles,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                ZsSwitchRow(
                    title = "Play Trailers In App",
                    subtitle = "Off opens trailers in an external video app instead",
                    checked = settings.trailersOpenInApp,
                    onCheckedChange = vm::setTrailersOpenInApp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (isTv) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("Input", theme)

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.settings.card.background)
                ) {
                    TvSettingsRow(theme, onActivate = { vm.setEnableNativeKeyboard(!settings.enableNativeKeyboard) }) {
                        TvSwitchContent("Use Native Keyboard", "Hide virtual keyboard and use system input", settings.enableNativeKeyboard)
                    }
                }
        }

        Spacer(Modifier.height(16.dp))
        if (!isTv) {
            SectionLabel("Player Controls", theme)
            SettingsCard(theme) {
                ZsSwitchRow(
                    title = "Hold to Boost",
                    subtitle = "Hold to increase playback speed",
                    checked = settings.enableHoldToBoost,
                    onCheckedChange = vm::setEnableHoldToBoost,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                ZsSwitchRow(
                    title = "Double-tap to Seek",
                    subtitle = "Double-tap to seek forward/backward",
                    checked = settings.enableDoubleClickToSeek,
                    onCheckedChange = vm::setEnableDoubleClickToSeek,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Sources", theme)
        if (isTv) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.settings.card.background)
            ) {
                TvSettingsRow(theme, onActivate = { vm.setManualSourceSelection(!settings.manualSourceSelection) }) {
                    TvSwitchContent("Manual Source Selection", "Choose sources manually", settings.manualSourceSelection)
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                TvSettingsRow(theme, onActivate = { vm.setEnableAutoResumeOnPlaybackError(!settings.enableAutoResumeOnPlaybackError) }) {
                    TvSwitchContent("Auto-resume on Error", "Automatically try next source on playback error", settings.enableAutoResumeOnPlaybackError)
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                TvSettingsRow(theme, onActivate = { vm.setAllowParallelDownload(!settings.allowParallelDownload) }) {
                    TvSwitchContent(
                        "Allow parallel download",
                        "Allow downloading of multiple media at same time",
                        settings.allowParallelDownload,
                        warning = "This might make downloads unstable",
                    )
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                TvSettingsRow(theme, onActivate = { showSourceOrderDialog = true }) {
                    TvChevronContent("Custom Source Order", "Choose which sources are tried first")
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                TvSettingsRow(theme, onActivate = { showDownloadSourceOrderDialog = true }) {
                    TvChevronContent("Preferred Source Order for Downloads", "Choose which sources downloads try first")
                }
            }
        } else {
            SettingsCard(theme) {
                ZsSwitchRow(
                    title = "Manual Source Selection",
                    subtitle = "Choose sources manually",
                    checked = settings.manualSourceSelection,
                    onCheckedChange = vm::setManualSourceSelection,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                ZsSwitchRow(
                    title = "Auto-resume on Error",
                    subtitle = "Automatically try next source on playback error",
                    checked = settings.enableAutoResumeOnPlaybackError,
                    onCheckedChange = vm::setEnableAutoResumeOnPlaybackError,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                ZsSwitchRow(
                    title = "Allow parallel download",
                    subtitle = "Allow downloading of multiple media at same time",
                    notice = "This might make downloads unstable",
                    checked = settings.allowParallelDownload,
                    onCheckedChange = vm::setAllowParallelDownload,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showSourceOrderDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Custom Source Order", color = theme.colors.type.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Choose which sources are tried first", color = theme.colors.type.dimmed, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = theme.colors.type.dimmed)
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showDownloadSourceOrderDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Preferred Source Order for Downloads", color = theme.colors.type.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Choose which sources downloads try first", color = theme.colors.type.dimmed, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = theme.colors.type.dimmed)
                }
            }
        }

    }

    if (showSourceOrderDialog) {
        SourceOrderDialog(
            sources = sourceOrder,
            theme = theme,
            onReorder = vm::reorderSources,
            onDismiss = { showSourceOrderDialog = false },
        )
    }

    if (showDownloadSourceOrderDialog) {
        SourceOrderDialog(
            sources = downloadSourceOrder,
            theme = theme,
            onReorder = vm::reorderDownloadSources,
            onDismiss = { showDownloadSourceOrderDialog = false },
            title = "Preferred Source Order for Downloads",
            subtitle = "Downloads try sources top to bottom",
        )
    }
}

/** Renders title + subtitle + a Switch, used inside TvSettingsRow. */
@Composable
private fun RowScope.TvSwitchContent(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    indent: Boolean = false,
    warning: String? = null,
) {
    val theme = LocalZStreamTheme.current
    Column(
        Modifier
            .weight(1f)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(start = if (indent) 28.dp else 12.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Text(title, color = theme.colors.type.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = theme.colors.type.dimmed, fontSize = 11.sp, lineHeight = 16.sp)
        }
        if (!warning.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(warning, color = theme.colors.type.danger, fontSize = 10.sp)
        }
    }
    Switch(
        checked = checked,
        onCheckedChange = null, // toggled by TvSettingsRow.onActivate
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = theme.colors.type.emphasis,
            checkedTrackColor = theme.colors.global.accentA,
            uncheckedThumbColor = theme.colors.type.dimmed,
            uncheckedTrackColor = theme.colors.background.secondary,
        ),
        modifier = Modifier.padding(end = 12.dp, top = 2.dp),
    )
}

/** Renders title + subtitle + a chevron, used inside TvSettingsRow for rows that open a dialog/sub-screen. */
@Composable
private fun RowScope.TvChevronContent(title: String, subtitle: String? = null) {
    val theme = LocalZStreamTheme.current
    Column(
        Modifier
            .weight(1f)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Text(title, color = theme.colors.type.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = theme.colors.type.dimmed, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
    Icon(
        Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = theme.colors.type.dimmed,
        modifier = Modifier.padding(end = 12.dp),
    )
}

/**
 * Reorderable list of sources shown top-to-bottom in the order they'll be tried during scraping.
 * Supports both touch drag (long-press the handle) and D-pad-friendly up/down buttons, since a
 * pointer-drag gesture alone isn't usable from a TV remote.
 */
@Composable
private fun SourceOrderDialog(
    sources: List<SourceInfo>,
    theme: ZStreamTheme,
    onReorder: (List<SourceInfo>) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Custom Source Order",
    subtitle: String = "Sources are tried top to bottom",
) {
    val items = remember(sources) { mutableStateListOf(*sources.toTypedArray()) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var draggedOffset by remember { mutableStateOf(0f) }
    val itemHeights = remember { mutableMapOf<Int, Int>() }
    val closeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        closeFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.colors.modal.background)
                .border(1.dp, theme.colors.type.divider.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(title, color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(subtitle, color = theme.colors.type.dimmed, fontSize = 12.sp)
                }
                var closeFocused by remember { mutableStateOf(false) }
                ZsOutlinedWrapper(
                    shape = RoundedCornerShape(8.dp),
                    outlineColor = Color.White,
                    outlineWidth = 2.dp,
                    gap = 2.dp,
                    visible = closeFocused,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .focusRequester(closeFocusRequester)
                            .onFocusChanged { closeFocused = it.isFocused },
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = theme.colors.type.secondary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (items.isEmpty()) {
                Text("No sources available yet.", color = theme.colors.type.dimmed, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            }

            Column(Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, source ->
                    val isDragging = draggedIndex == index
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { itemHeights[index] = it.size.height }
                            .offset { IntOffset(0, if (isDragging) draggedOffset.roundToInt() else 0) }
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDragging) theme.colors.background.secondaryHover.copy(alpha = 0.6f) else Color.Transparent)
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("${index + 1}", color = theme.colors.type.dimmed, fontSize = 12.sp, modifier = Modifier.width(16.dp))
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = theme.colors.type.dimmed,
                                modifier = Modifier
                                    .size(22.dp)
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedIndex = index
                                                draggedOffset = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val idx = draggedIndex
                                                if (idx != null) {
                                                    draggedOffset += dragAmount.y
                                                    val h = (itemHeights[idx] ?: 0).toFloat()
                                                    if (draggedOffset > h / 2 && idx < items.size - 1) {
                                                        items[idx] = items[idx + 1].also { items[idx + 1] = items[idx] }
                                                        draggedIndex = idx + 1
                                                        draggedOffset -= h
                                                    } else if (draggedOffset < -(h / 2) && idx > 0) {
                                                        items[idx] = items[idx - 1].also { items[idx - 1] = items[idx] }
                                                        draggedIndex = idx - 1
                                                        draggedOffset += h
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                onReorder(items.toList())
                                                draggedIndex = null
                                                draggedOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggedIndex = null
                                                draggedOffset = 0f
                                            },
                                        )
                                    }
                            )
                            Text(
                                source.displayName,
                                color = theme.colors.type.text,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            var upFocused by remember { mutableStateOf(false) }
                            ZsOutlinedWrapper(
                                shape = RoundedCornerShape(8.dp),
                                outlineColor = Color.White,
                                outlineWidth = 2.dp,
                                gap = 2.dp,
                                visible = upFocused,
                            ) {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            items[index] = items[index - 1].also { items[index - 1] = items[index] }
                                            onReorder(items.toList())
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .onFocusChanged { upFocused = it.isFocused },
                                ) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", tint = if (index > 0) theme.colors.type.secondary else theme.colors.type.dimmed.copy(alpha = 0.3f))
                                }
                            }
                            var downFocused by remember { mutableStateOf(false) }
                            ZsOutlinedWrapper(
                                shape = RoundedCornerShape(8.dp),
                                outlineColor = Color.White,
                                outlineWidth = 2.dp,
                                gap = 2.dp,
                                visible = downFocused,
                            ) {
                                IconButton(
                                    onClick = {
                                        if (index < items.size - 1) {
                                            items[index] = items[index + 1].also { items[index + 1] = items[index] }
                                            onReorder(items.toList())
                                        }
                                    },
                                    enabled = index < items.size - 1,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .onFocusChanged { downFocused = it.isFocused },
                                ) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", tint = if (index < items.size - 1) theme.colors.type.secondary else theme.colors.type.dimmed.copy(alpha = 0.3f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

//  Appearance Section

@Composable
private fun AppearanceSection(
    settings: SettingsEntity,
    theme: ZStreamTheme,
    vm: SettingsViewModel,
    nav: NavController,
    isTv: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
) {
    val allThemes = ThemeRegistry.allThemes
    val activeTheme = ThemeRegistry.getThemeById(settings.applicationTheme)

    Column(Modifier
        .padding(bottom = 32.dp)
        .padding(top = if (isTv) 16.dp else 0.dp)) {
        Spacer(Modifier.height(8.dp))
        SectionLabel("Home Page", theme)
        if (isTv) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.settings.card.background)
            ) {
                TvSettingsRow(
                    theme = theme,
                    onActivate = { vm.setEnableFeatured(!settings.enableFeatured) },
                    modifier = if (firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                ) {
                    TvSwitchContent("Featured", "Show featured content in discover", settings.enableFeatured, enabled = !settings.enableLowPerformanceMode)
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                TvSettingsRow(theme, onActivate = { vm.setEnableImageLogos(!settings.enableImageLogos) }) {
                    TvSwitchContent("Image Logos", "Show logo images for media", settings.enableImageLogos, enabled = !settings.enableLowPerformanceMode)
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                TvSettingsRow(theme, onActivate = { vm.setEnableCarouselView(!settings.enableCarouselView) }) {
                    TvSwitchContent("Carousel View", "Switch between carousel and grid view", settings.enableCarouselView)
                }
                if (!settings.enableCarouselView) {
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    TvSliderRow(
                        label = "Number of rows in the grid",
                        value = settings.gridRows.toFloat(),
                        rangeStart = 1f,
                        rangeEnd = 8f,
                        steps = 6,
                        display = { it.roundToInt().toString() },
                        onValueChange = { vm.setGridRows(it.roundToInt()) },
                        theme = theme
                    )
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                TvSettingsRow(theme, onActivate = { vm.setEnableMinimalCards(!settings.enableMinimalCards) }) {
                    TvSwitchContent("Minimal Cards", "Compact card display", settings.enableMinimalCards)
                }
                if (settings.enableCarouselView) {
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    TvSliderRow(
                        label = "Carousel item limit",
                        value = settings.homeSectionCarouselLimit.toFloat(),
                        rangeStart = 1f,
                        rangeEnd = 50f,
                        steps = 48,
                        display = { it.roundToInt().toString() },
                        onValueChange = { vm.setHomeSectionCarouselLimit(it.roundToInt()) },
                        theme = theme
                    )
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                TvSettingsRow(theme, onActivate = {
                    HomeLayoutMenuSignal.request()
                    nav.navigate("home")
                }) {
                    Text("Edit home sections", color = theme.colors.type.text, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                }
            }
        } else {
            SettingsCard(theme) {
                ZsSwitchRow(
                    title = "Featured",
                    subtitle = "Show featured content in discover",
                    checked = settings.enableFeatured,
                    onCheckedChange = {
                        vm.setEnableFeatured(it)
                        if (!it) vm.setEnableFeatured(false)
                    },
                    enabled = !settings.enableLowPerformanceMode,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                ZsSwitchRow(
                    title = "Image Logos",
                    subtitle = "Show logo images for media",
                    checked = settings.enableImageLogos,
                    onCheckedChange = vm::setEnableImageLogos,
                    enabled = !settings.enableLowPerformanceMode,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                ZsSwitchRow(
                    title = "Carousel View",
                    subtitle = "Switch between a carousel and grid view for continue watching and bookmarks",
                    checked = settings.enableCarouselView,
                    onCheckedChange = vm::setEnableCarouselView,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                AnimatedVisibility(
                    visible = !settings.enableCarouselView,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                        SliderRow(
                            label = "Number of rows in the grid",
                            value = settings.gridRows.toFloat(),
                            rangeStart = 1f,
                            rangeEnd = 8f,
                            steps = 6,
                            display = { it.roundToInt().toString() },
                            onValueChange = { vm.setGridRows(it.roundToInt()) },
                            theme = theme
                        )
                    }
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                ZsSwitchRow(
                    title = "Minimal Cards",
                    subtitle = "Compact card display",
                    checked = settings.enableMinimalCards,
                    onCheckedChange = vm::setEnableMinimalCards,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                AnimatedVisibility(
                    visible = settings.enableCarouselView,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                        SliderRow(
                            label = "Carousel item limit",
                            value = settings.homeSectionCarouselLimit.toFloat(),
                            rangeStart = 1f,
                            rangeEnd = 50f,
                            steps = 48,
                            display = { it.roundToInt().toString() },
                            onValueChange = { vm.setHomeSectionCarouselLimit(it.roundToInt()) },
                            theme = theme
                        )
                    }
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            HomeLayoutMenuSignal.request()
                            nav.navigate("home")
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Edit home sections", color = theme.colors.type.text, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Player UI", theme)
        if (isTv) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.settings.card.background)
            ) {
                TvSettingsRow(theme, onActivate = { vm.setEnablePauseOverlay(!settings.enablePauseOverlay) }) {
                    TvSwitchContent("Pause Overlay", "Show overlay when paused", settings.enablePauseOverlay, enabled = !settings.enableLowPerformanceMode)
                }
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                TvSettingsRow(theme, onActivate = { vm.setForceCompactEpisodeView(!settings.forceCompactEpisodeView) }) {
                    TvSwitchContent("Compact Episode View", "Compact episode list in player", settings.forceCompactEpisodeView, enabled = !settings.enableLowPerformanceMode)
                }
            }
        } else {
            SettingsCard(theme) {
                ZsSwitchRow(
                    title = "Pause Overlay",
                    subtitle = "Show overlay when paused",
                    checked = settings.enablePauseOverlay,
                    onCheckedChange = vm::setEnablePauseOverlay,
                    enabled = !settings.enableLowPerformanceMode,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                ZsSwitchRow(
                    title = "Compact Episode View",
                    subtitle = "Compact episode list in player",
                    checked = settings.forceCompactEpisodeView,
                    onCheckedChange = vm::setForceCompactEpisodeView,
                    enabled = !settings.enableLowPerformanceMode,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Theme", theme)
        if (isTv) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.settings.card.background)
            ) {
                TvSettingsRow(theme) {
                    Text("Active Theme", color = theme.colors.type.text, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                    Text(activeTheme.name, color = theme.colors.type.secondary, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                }
            }
        } else {
            SettingsCard(theme) {
                SettingsRow("Active Theme", activeTheme.name, theme)
            }
        }

        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            allThemes.forEach { themeOption ->
                ZsThemePreviewCard(
                    themeOption = themeOption,
                    selected = themeOption.id == activeTheme.id,
                    onClick = { vm.setApplicationTheme(themeOption.id) },
                )
            }
        }
    }
}

@Composable
private fun PlayerSection(
    settings: SettingsEntity,
    theme: ZStreamTheme,
    vm: SettingsViewModel,
    isTv: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
) {
    Column(Modifier
        .padding(bottom = 32.dp)
        .padding(top = if (isTv) 16.dp else 0.dp)) {
        Spacer(Modifier.height(8.dp))
        if (isTv) {
            // TV uses its own dedicated in-app PiP mechanism (back button on the player), not
            // the system auto-PiP-on-background behavior this setting controls.
            Text(
                "Nothing to configure here on TV yet.",
                color = theme.colors.type.secondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            SectionLabel("Background Playback", theme)
            SettingsCard(theme) {
                ZsSwitchRow(
                    title = "Auto Picture-in-Picture",
                    subtitle = "When leaving the app while a video is playing: on = automatically enter Picture-in-Picture, off = pause playback.",
                    checked = settings.autoPipEnabled,
                    onCheckedChange = vm::setAutoPipEnabled,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
//  Subtitles Section

@Composable
private fun SubtitlePreview(settings: SettingsEntity, theme: ZStreamTheme, vm: SettingsViewModel, isTv: Boolean = false) {
    val sampleText = "This is a preview of your subtitles\nThis is a preview of your second line."
    val textColor = Color(android.graphics.Color.parseColor(settings.subtitleColor))
    val bgAlpha = settings.subtitleBackgroundOpacity.coerceIn(0f, 1f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val currentPosition = rememberUpdatedState(settings.subtitleVerticalPosition)

    val subtitleShadow = when (settings.subtitleFontStyle) {
        "raised"     -> Shadow(Color.Black.copy(alpha = 0.9f), offset = androidx.compose.ui.geometry.Offset(0f, -3f), blurRadius = 0f)
        "depressed"  -> Shadow(Color.Black.copy(alpha = 0.9f), offset = androidx.compose.ui.geometry.Offset(0f, 3f),  blurRadius = 0f)
        "dropShadow" -> Shadow(Color.Black.copy(alpha = 0.9f), offset = androidx.compose.ui.geometry.Offset(4f, 4f),  blurRadius = 8f)
        else         -> Shadow(Color.Black.copy(alpha = 0.5f), offset = androidx.compose.ui.geometry.Offset(0f, 2f),  blurRadius = 4f)
    }

    val fontSize   = (settings.subtitleSize * 18).sp
    val lineHeight = (fontSize.value * settings.subtitleLineHeight).sp
    val fontFamily = when (settings.subtitleFont) {
        "serif"              -> androidx.compose.ui.text.font.FontFamily.Serif
        "monospace"          -> androidx.compose.ui.text.font.FontFamily.Monospace
        "sans-serif-condensed" -> androidx.compose.ui.text.font.FontFamily(
            android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
        )
        else -> androidx.compose.ui.text.font.FontFamily.SansSerif
    }

    val isBorder = settings.subtitleFontStyle == "Border"
    val borderThick = settings.subtitleBorderThickness.dp

    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .then(
                if (!isTv) Modifier.pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaDp = with(density) { dragAmount.y.toDp() }
                        val deltaVal = -deltaDp.value / 4.5f
                        val newVal = (currentPosition.value + deltaVal).coerceIn(-15f, 30f)
                        vm.setSubtitleVerticalPosition(newVal)
                    }
                } else Modifier
            )
    ) {
        // Background gradient scene
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF3B27B2),
                            Color(0xFF8B5CF6),
                            Color(0xFFEC4899),
                            Color(0xFF3B82F6)
                        )
                    )
                )
        )

        // Backdrop image
        coil.compose.AsyncImage(
            model = "https://image.tmdb.org/t/p/w780/d5iil4xe79avh0cg4ytrusugx2c.jpg",
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Dark scrim
        Box(Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)))

        // Drag tip
        if (!isTv) {
            Text(
                "Drag text to reposition",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )
        }

        //  Subtitle preview (native or custom)
        val previewLabel = if (settings.enableNativeSubtitles)
            "Native subtitle sample text" else sampleText

        // Blur modifier: works on API 31+ via Modifier.blur; gracefully no-ops below.
        val blurMod: Modifier = if (settings.subtitleBackgroundBlurEnabled && settings.subtitleBackgroundBlur > 0f)
            Modifier.blur((settings.subtitleBackgroundBlur * 20f).coerceAtLeast(1f).dp)
        else Modifier

        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .offset(y = (-settings.subtitleVerticalPosition * 4.5f).dp)
                .padding(horizontal = 24.dp)
        ) {
            // Background blur layer (rendered behind text)
            if (bgAlpha > 0f) {
                Box(
                    Modifier
                        .matchParentSize()
                        .then(blurMod)
                        .background(Color.Black.copy(alpha = bgAlpha), RoundedCornerShape(4.dp))
                )
            }
            // Text layer (not blurred)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                if (isBorder) {
                    OutlinedSubtitleText(
                        text = previewLabel,
                        color = textColor,
                        outlineColor = Color.Black,
                        outlineWidth = borderThick.coerceAtLeast(0.5.dp),
                        fontSize = fontSize,
                        fontWeight = if (settings.subtitleBold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = fontFamily,
                        lineHeight = lineHeight,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = previewLabel,
                        color = textColor,
                        fontSize = fontSize,
                        fontWeight = if (settings.subtitleBold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = fontFamily,
                        lineHeight = lineHeight,
                        textAlign = TextAlign.Center,
                        style = TextStyle(shadow = subtitleShadow),
                    )
                }
            }
        }
    }
}

@Composable
private fun OutlinedSubtitleText(
    text: String,
    color: Color,
    outlineColor: Color,
    outlineWidth: Dp,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    fontFamily: FontFamily,
    lineHeight: TextUnit,
    textAlign: TextAlign,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val outlinePx = with(density) { outlineWidth.toPx() }

    val textStyle = TextStyle(
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        lineHeight = lineHeight,
        textAlign = textAlign,
    )

    val layoutResult = remember(text, textStyle) {
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = textStyle,
            constraints = Constraints(),
        )
    }

    val canvasWidth = with(density) { layoutResult.size.width.toDp() }
    val canvasHeight = with(density) { layoutResult.size.height.toDp() }

    Canvas(modifier = Modifier.size(width = canvasWidth, height = canvasHeight)) {
        drawText(
            textLayoutResult = layoutResult,
            color = outlineColor,
            drawStyle = Stroke(width = outlinePx),
        )
        drawText(
            textLayoutResult = layoutResult,
            color = color,
            drawStyle = Fill,
        )
    }
}

@Composable
private fun SubtitlesSettingsContent(
    settings: SettingsEntity, 
    theme: ZStreamTheme, 
    vm: SettingsViewModel, 
    isTv: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
) {
    val currentPreset = remember(settings) {
        when {
            // Netflix: white, no background, drop shadow, not bold, condensed font
            settings.subtitleColor.equals("#ffffff", true) &&
            settings.subtitleBackgroundOpacity == 0.0f &&
            !settings.subtitleBackgroundBlurEnabled &&
            !settings.subtitleBold &&
            settings.subtitleFontStyle == "dropShadow" &&
            settings.subtitleLineHeight == 1.2f &&
            settings.subtitleSize == 1.0f &&
            settings.subtitleFont == "sans-serif-condensed" -> "netflix"

            // YouTube: white, 40% background, no blur, bold, default style, sans-serif
            settings.subtitleColor.equals("#ffffff", true) &&
            settings.subtitleBackgroundOpacity == 0.4f &&
            !settings.subtitleBackgroundBlurEnabled &&
            settings.subtitleBold &&
            settings.subtitleFontStyle == "default" &&
            settings.subtitleLineHeight == 1.3f &&
            settings.subtitleSize == 1.0f &&
            settings.subtitleFont == "sans-serif" -> "youtube"

            // Anime: yellow, no background, border, bold, monospace
            settings.subtitleColor.equals("#e2e535", true) &&
            settings.subtitleBackgroundOpacity == 0.0f &&
            !settings.subtitleBackgroundBlurEnabled &&
            settings.subtitleBold &&
            settings.subtitleFontStyle == "Border" &&
            settings.subtitleBorderThickness == 3.0f &&
            settings.subtitleLineHeight == 1.3f &&
            settings.subtitleSize == 1.0f &&
            settings.subtitleFont == "monospace" -> "anime"

            else -> "custom"
        }
    }

    if (isTv) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Presets", theme)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    "netflix" to "Netflix",
                    "youtube" to "YouTube",
                    "anime" to "Anime",
                    "custom" to "Custom"
                )
                presets.forEach { (key, label) ->
                    var presetFocused by remember { mutableStateOf(false) }
                    val isSelected = currentPreset == key
                    val cardBg = if (isSelected) theme.colors.global.accentA else theme.colors.background.secondary
                    val cardBorder = if (isSelected) Color.Transparent else theme.colors.utils.divider.copy(alpha = 0.2f)
                    val textColor = if (isSelected) Color.White else theme.colors.type.text

                    ZsOutlinedWrapper(
                        shape = RoundedCornerShape(8.dp),
                        outlineColor = Color.White,
                        outlineWidth = 2.dp,
                        gap = 2.dp,
                        visible = presetFocused,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (isSelected && firstItemFocusRequester != null) Modifier.focusRequester(
                                    firstItemFocusRequester
                                ) else Modifier
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(8.dp))
                                .onFocusChanged { presetFocused = it.isFocused }
                                .focusable()
                                .onKeyEvent { event ->
                                    if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                                        if (key == "custom") vm.restoreCustomSubtitleSlot()
                                        else vm.applySubtitlePreset(key)
                                        true
                                    } else false
                                }
                                .clickable {
                                    if (key == "custom") vm.restoreCustomSubtitleSlot()
                                    else vm.applySubtitlePreset(key)
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("Behavior", theme)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.settings.card.background)
            ) {
                TvDropdownRow(
                    "Preferred language",
                    SUBTITLE_LANGUAGES.map { it.second },
                    subtitleLanguageDisplayName(settings.defaultSubtitleLanguage),
                    { display -> vm.setDefaultSubtitleLanguage(subtitleLanguageCodeFor(display)) },
                    theme,
                )
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                TvSettingsRow(theme, onActivate = { vm.setEnableNativeSubtitles(!settings.enableNativeSubtitles) }) {
                    TvSwitchContent("Native Subtitles", "Use system subtitle renderer", settings.enableNativeSubtitles)
                }
            }

            if (!settings.enableNativeSubtitles) {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Background", theme)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.colors.settings.card.background)
                ) {
                    TvSliderRow("Background Opacity", settings.subtitleBackgroundOpacity * 100, 0f, 100f, { "${it.toInt()}%" }, { vm.setSubtitleBackgroundOpacity(it / 100f) }, theme)
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    TvSettingsRow(theme, onActivate = { vm.setSubtitleBackgroundBlurEnabled(!settings.subtitleBackgroundBlurEnabled) }) {
                        TvSwitchContent("Background Blur", "Apply blur effect behind subtitles", settings.subtitleBackgroundBlurEnabled)
                    }
                    if (settings.subtitleBackgroundBlurEnabled) {
                        HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                        TvSliderRow("Blur Amount", settings.subtitleBackgroundBlur * 100, 0f, 100f, { "${it.toInt()}%" }, { vm.setSubtitleBackgroundBlur(it / 100f) }, theme)
                    }
                }

                Spacer(Modifier.height(8.dp))
                SectionLabel("Text", theme)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.colors.settings.card.background)
                ) {
                    TvSliderRow("Text Size", settings.subtitleSize * 100, 1f, 200f, { "${it.toInt()}%" }, { vm.setSubtitleSize(it / 100f) }, theme)
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    TvDropdownRow("Style", listOf("default", "raised", "depressed", "Border", "dropShadow"), settings.subtitleFontStyle, vm::setSubtitleFontStyle, theme)
                    if (settings.subtitleFontStyle == "Border") {
                        HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                        TvSliderRow("Border Thickness", settings.subtitleBorderThickness, 0f, 10f, { "${it.toInt()}px" }, { vm.setSubtitleBorderThickness(it) }, theme)
                    }
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    TvDropdownRow("Font Family", listOf("sans-serif", "sans-serif-condensed", "serif", "monospace"), settings.subtitleFont, vm::setSubtitleFont, theme)
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    TvSettingsRow(theme, onActivate = { vm.setSubtitleBold(!settings.subtitleBold) }) {
                        TvSwitchContent("Bold", null, settings.subtitleBold)
                    }
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))

                    var showColorPicker by remember { mutableStateOf(false) }
                    val currentSubColor = Color(android.graphics.Color.parseColor(settings.subtitleColor))

                    TvSettingsRow(theme, onActivate = { showColorPicker = true }) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Color", color = theme.colors.type.text, fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    settings.subtitleColor.uppercase(),
                                    color = theme.colors.type.secondary,
                                    fontSize = 12.sp
                                )
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(currentSubColor)
                                        .border(
                                            1.dp,
                                            theme.colors.utils.divider.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                )
                            }
                        }
                    }

                    if (showColorPicker) {
                        SubtitleColorPickerDialog(
                            initialColor = currentSubColor,
                            theme = theme,
                            onColorSelected = { hex ->
                                vm.setSubtitleColor(hex)
                                showColorPicker = false
                            },
                            onDismiss = { showColorPicker = false }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                SectionLabel("Layout", theme)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.colors.settings.card.background)
                ) {
                    TvSliderRow("Vertical Position", settings.subtitleVerticalPosition, -15f, 30f, { "${it.toInt()}" }, { vm.setSubtitleVerticalPosition(it) }, theme)
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    TvSliderRow("Line Spacing", settings.subtitleLineHeight * 100, 100f, 250f, { "${it.toInt()}%" }, { vm.setSubtitleLineHeight(it / 100f) }, theme)
                }

                Spacer(Modifier.height(12.dp))
                var resetFocused by remember { mutableStateOf(false) }
                ZsOutlinedWrapper(
                    shape = RoundedCornerShape(8.dp),
                    outlineColor = Color.White,
                    outlineWidth = 2.dp,
                    gap = 3.dp,
                    visible = resetFocused,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.colors.background.secondary)
                            .onFocusChanged { resetFocused = it.isFocused }
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                                    vm.resetSubtitleStyling()
                                    true
                                } else false
                            }
                            .clickable { vm.resetSubtitleStyling() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp), tint = theme.colors.type.secondary)
                            Text("Reset", color = theme.colors.type.secondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.padding(bottom = 32.dp)) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Presets", theme)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    "netflix" to "Netflix",
                    "youtube" to "YouTube",
                    "anime" to "Anime",
                    "custom" to "Custom"
                )
                presets.forEach { (key, label) ->
                    val isSelected = currentPreset == key
                    val cardBg = if (isSelected) theme.colors.global.accentA else theme.colors.background.secondary
                    val cardBorder = if (isSelected) Color.Transparent else theme.colors.utils.divider.copy(alpha = 0.2f)
                    val textColor = if (isSelected) Color.White else theme.colors.type.text

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                if (key == "custom") vm.restoreCustomSubtitleSlot()
                                else vm.applySubtitlePreset(key)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("Behavior", theme)
            SettingsCard(theme) {
                ZsDropdownRow(
                    "Preferred language",
                    SUBTITLE_LANGUAGES.map { it.second },
                    subtitleLanguageDisplayName(settings.defaultSubtitleLanguage),
                    { display -> vm.setDefaultSubtitleLanguage(subtitleLanguageCodeFor(display)) },
                )
                HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                ZsSwitchRow(
                    title = "Native Subtitles",
                    subtitle = "Use system subtitle renderer",
                    checked = settings.enableNativeSubtitles,
                    onCheckedChange = vm::setEnableNativeSubtitles,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (!settings.enableNativeSubtitles) {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Background", theme)
                SettingsCard(theme) {
                    SliderRow("Background Opacity", settings.subtitleBackgroundOpacity * 100, 0f, 100f, { "${it.toInt()}%" }, { vm.setSubtitleBackgroundOpacity(it / 100f) }, theme)
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    ZsSwitchRow(
                        title = "Background Blur",
                        subtitle = "Apply blur effect behind subtitles",
                        checked = settings.subtitleBackgroundBlurEnabled,
                        onCheckedChange = vm::setSubtitleBackgroundBlurEnabled,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    if (settings.subtitleBackgroundBlurEnabled) {
                        HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                        SliderRow("Blur Amount", settings.subtitleBackgroundBlur * 100, 0f, 100f, { "${it.toInt()}%" }, { vm.setSubtitleBackgroundBlur(it / 100f) }, theme)
                    }
                }

                Spacer(Modifier.height(8.dp))
                SectionLabel("Text", theme)
                SettingsCard(theme) {
                    SliderRow("Text Size", settings.subtitleSize * 100, 1f, 200f, { "${it.toInt()}%" }, { vm.setSubtitleSize(it / 100f) }, theme)
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    ZsDropdownRow("Style", listOf("default", "raised", "depressed", "Border", "dropShadow"), settings.subtitleFontStyle, vm::setSubtitleFontStyle)
                    if (settings.subtitleFontStyle == "Border") {
                        HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                        SliderRow("Border Thickness", settings.subtitleBorderThickness, 0f, 10f, { "${it.toInt()}px" }, { vm.setSubtitleBorderThickness(it) }, theme)
                    }
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    ZsDropdownRow("Font Family", listOf("sans-serif", "sans-serif-condensed", "serif", "monospace"), settings.subtitleFont, vm::setSubtitleFont)
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    ZsSwitchRow(
                        title = "Bold",
                        checked = settings.subtitleBold,
                        onCheckedChange = vm::setSubtitleBold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))

                    //  Color picker
                    var showColorPicker by remember { mutableStateOf(false) }
                    val currentSubColor = Color(android.graphics.Color.parseColor(settings.subtitleColor))

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showColorPicker = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Color", color = theme.colors.type.text, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                settings.subtitleColor.uppercase(),
                                color = theme.colors.type.secondary,
                                fontSize = 12.sp
                            )
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(currentSubColor)
                                    .border(
                                        1.dp,
                                        theme.colors.utils.divider.copy(alpha = 0.4f),
                                        CircleShape
                                    )
                            )
                        }
                    }

                    if (showColorPicker) {
                        SubtitleColorPickerDialog(
                            initialColor = currentSubColor,
                            theme = theme,
                            onColorSelected = { hex ->
                                vm.setSubtitleColor(hex)
                                showColorPicker = false
                            },
                            onDismiss = { showColorPicker = false }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                SectionLabel("Layout", theme)
                SettingsCard(theme) {
                    SliderRow("Vertical Position", settings.subtitleVerticalPosition, -15f, 30f, { "${it.toInt()}" }, { vm.setSubtitleVerticalPosition(it) }, theme)
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    SliderRow("Line Spacing", settings.subtitleLineHeight * 100, 100f, 250f, { "${it.toInt()}%" }, { vm.setSubtitleLineHeight(it / 100f) }, theme)
                }

                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { vm.resetSubtitleStyling() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = theme.colors.type.secondary)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset", fontSize = 12.sp)
                }
            }
        }
    }
}

private val SUBTITLE_LANGUAGES: List<Pair<String, String>> = listOf(
    "en" to "English",
    "es" to "Spanish",
    "fr" to "French",
    "de" to "German",
    "pt" to "Portuguese",
    "it" to "Italian",
    "nl" to "Dutch",
    "ja" to "Japanese",
    "ko" to "Korean",
    "zh" to "Chinese",
    "ar" to "Arabic",
    "hi" to "Hindi",
    "ru" to "Russian",
    "tr" to "Turkish",
)

private fun subtitleLanguageDisplayName(code: String?): String =
    SUBTITLE_LANGUAGES.firstOrNull { it.first == code }?.second ?: "English"

private fun subtitleLanguageCodeFor(displayName: String): String =
    SUBTITLE_LANGUAGES.firstOrNull { it.second == displayName }?.first ?: "en"

private val subtitleColorGrid = listOf(
    "#ffffff", "#f0f0f0", "#d4d4d4", "#b0b0b0",
    "#80b1fa", "#60a0f0", "#4090e0", "#1e6fc9",
    "#e2e535", "#d0c820", "#c0b810", "#a8a000",
    "#10B239", "#209a30", "#308040", "#3a6b40",
    "#ff6b6b", "#e05050", "#c04040", "#a03030",
    "#f0a030", "#e09020", "#c08010", "#a07000",
    "#d070d0", "#c060c0", "#a050a0", "#804080",
    "#000000", "#333333", "#666666", "#999999",
)

@Composable
private fun SubtitleColorPickerDialog(
    initialColor: Color,
    theme: ZStreamTheme,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHex = remember {
        String.format("#%06X", 0xFFFFFF and initialColor.toArgb())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.colors.background.secondary,
        title = { Text("Pick a Color", color = theme.colors.type.emphasis) },
        text = {
            Column {
                Text("Select a color for your subtitles.", color = theme.colors.type.text, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    subtitleColorGrid.forEach { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = hex.equals(initialHex, ignoreCase = true)
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                                .clickable { onColorSelected(hex) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.colors.global.accentA)
            }
        },
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    rangeStart: Float,
    rangeEnd: Float,
    display: (Float) -> String,
    onValueChange: (Float) -> Unit,
    theme: ZStreamTheme,
    steps: Int = 0) {
    var sliderValue by remember { mutableFloatStateOf(value) }
    var pendingValue by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(value) {
        val pending = pendingValue
        if (pending == null || abs(value - pending) < 0.0001f) {
            sliderValue = value
            pendingValue = null
        }
    }
    Column(Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, color = theme.colors.type.text, fontSize = 13.sp)
            Text(display(sliderValue), color = theme.colors.type.secondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        // Real‑time slider updates – call vm.setSubtitleVerticalPosition directly on value change
        // also reused for gridRows
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                pendingValue = it
                onValueChange(it)
            },
            onValueChangeFinished = { /* no‑op */ },
            valueRange = rangeStart..rangeEnd,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = theme.colors.global.accentA,
                inactiveTrackColor = theme.colors.utils.divider,
            ),
        )
    }
}

@Composable
private fun TvSliderRow(
    label: String,
    value: Float,
    rangeStart: Float,
    rangeEnd: Float,
    display: (Float) -> String,
    onValueChange: (Float) -> Unit,
    theme: ZStreamTheme,
    steps: Int = 0,
) {
    var isFocused by remember { mutableStateOf(false) }
    var isAdjusting by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(value) }
    val stepAmount = if (steps > 0) (rangeEnd - rangeStart) / (steps + 1) else (rangeEnd - rangeStart) * 0.05f

    fun stepValue(direction: Int): Float {
        val tick = ((sliderValue - rangeStart) / stepAmount).roundToInt() + direction
        return (rangeStart + tick * stepAmount).coerceIn(rangeStart, rangeEnd)
    }

    LaunchedEffect(value) { if (!isAdjusting) sliderValue = value }

    if (isAdjusting) {
        BackHandler { isAdjusting = false }
    }

    val bgColor by animateColorAsState(
        if (isAdjusting) theme.colors.global.accentA.copy(alpha = 0.2f)
        else if (isFocused) theme.colors.background.secondary.copy(alpha = 0.5f)
        else Color.Transparent
    )

    ZsOutlinedWrapper(
        shape = RoundedCornerShape(8.dp),
        outlineColor = Color.White,
        outlineWidth = 2.dp,
        horizontal = 4.dp,
        vertical = 2.dp,
        visible = isFocused || isAdjusting,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (!it.isFocused) isAdjusting = false
                }
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    if (isAdjusting) {
                        when (event.key) {
                            Key.DirectionRight -> {
                                val newVal = stepValue(1)
                                sliderValue = newVal
                                onValueChange(newVal)
                                true
                            }

                            Key.DirectionLeft -> {
                                val newVal = stepValue(-1)
                                sliderValue = newVal
                                onValueChange(newVal)
                                true
                            }

                            Key.DirectionCenter, Key.Enter, Key.Back -> {
                                isAdjusting = false
                                true
                            }

                            Key.DirectionUp, Key.DirectionDown -> {
                                true // Consume navigation keys while adjusting
                            }

                            else -> false
                        }
                    } else {
                        if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                            isAdjusting = true
                            true
                        } else false
                    }
                }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Text(label, color = theme.colors.type.text, fontSize = 13.sp)
                Text(display(sliderValue), color = theme.colors.type.secondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it; onValueChange(it) },
                onValueChangeFinished = { },
                valueRange = rangeStart..rangeEnd,
                steps = steps,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp)
                    .height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = if (isAdjusting) Color.White else theme.colors.type.dimmed,
                    activeTrackColor = if (isAdjusting) theme.colors.global.accentA else theme.colors.global.accentA.copy(alpha = 0.6f),
                    inactiveTrackColor = theme.colors.utils.divider.copy(alpha = 0.2f),
                    disabledThumbColor = theme.colors.type.dimmed,
                    disabledActiveTrackColor = theme.colors.global.accentA.copy(alpha = 0.4f),
                ),
                enabled = isAdjusting,
            )
        }
    }
}

@Composable
private fun TvDropdownRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    theme: ZStreamTheme,
) {
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    ZsOutlinedWrapper(
        shape = RoundedCornerShape(8.dp),
        outlineColor = Color.White,
        outlineWidth = 2.dp,
        horizontal = 4.dp,
        vertical = 2.dp,
        visible = isFocused,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .onFocusChanged { isFocused = it.isFocused }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                        if (!expanded) expanded = true
                        true
                    } else false
                }
                .focusable()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            Text(title, color = theme.colors.type.text, fontSize = 13.sp)
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selected.replaceFirstChar { it.uppercase() }, color = theme.colors.type.secondary, fontSize = 12.sp)
                    Icon(Icons.Default.ArrowDropDown, null, tint = theme.colors.type.dimmed, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(theme.colors.background.secondary)
                ) {
                    options.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.replaceFirstChar { it.uppercase() }, color = if (opt == selected) theme.colors.global.accentA else theme.colors.type.text, fontSize = 13.sp) },
                            onClick = { onSelect(opt); expanded = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    visualTransformation: VisualTransformation,
    theme: ZStreamTheme,
    modifier: Modifier = Modifier,
) {
    var isEditing by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val textFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditing) {
        if (isEditing) textFieldFocusRequester.requestFocus()
    }

    if (isEditing) {
        BackHandler { isEditing = false }
    }

    ZsOutlinedWrapper(
        shape = RoundedCornerShape(8.dp),
        outlineColor = Color.White,
        outlineWidth = 2.dp,
        horizontal = 4.dp,
        vertical = 2.dp,
        visible = isFocused || isEditing,
    ) {
        Box(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(theme.colors.background.secondary)
                .border(
                    1.dp,
                    theme.colors.type.divider.copy(alpha = 0.3f),
                    RoundedCornerShape(8.dp)
                )
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = { isEditing = true },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(textFieldFocusRequester)
                    .onFocusChanged {
                        if (!it.isFocused) isEditing = false
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when {
                            !isEditing && (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                                isEditing = true
                                true
                            }

                            isEditing && (event.key == Key.Back) -> {
                                isEditing = false
                                true
                            }

                            isEditing && (event.key == Key.DirectionUp || event.key == Key.DirectionDown) -> true
                            else -> false
                        }
                    },
                textStyle = TextStyle(color = theme.colors.type.text, fontSize = 12.sp),
                visualTransformation = visualTransformation,
                readOnly = !isEditing,
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(placeholder, color = theme.colors.type.dimmed, fontSize = 12.sp)
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

private val subtitleColorPresets = listOf("#ffffff", "#80b1fa", "#e2e535", "#10B239")

@Composable
private fun ColorRow(title: String, value: String, onChange: (String) -> Unit, theme: ZStreamTheme) {
    var showCustom by remember { mutableStateOf(false) }
    var hexInput by remember { mutableStateOf("") }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically
    ) {
        Text(title, color = theme.colors.type.text, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            subtitleColorPresets.forEach { color ->
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(color)))
                        .border(
                            if (value.equals(color, true)) 2.dp else 0.dp,
                            Color.White,
                            CircleShape
                        )
                        .clickable { onChange(color) }
                )
            }
            if (showCustom) {
                BasicTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it.take(6).filter { c -> c.isDigit() || (c in 'a'..'f') || (c in 'A'..'F') } },
                    modifier = Modifier
                        .width(60.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(theme.colors.background.secondary)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    textStyle = TextStyle(color = theme.colors.type.text, fontSize = 11.sp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
                if (hexInput.length == 6) {
                    onChange("#$hexInput")
                    showCustom = false
                }
                Spacer(Modifier.width(4.dp))
                Box(Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (hexInput.length == 6) Color(android.graphics.Color.parseColor("#$hexInput")) else Color.Transparent)
                    .border(1.dp, theme.colors.utils.divider, CircleShape))
            } else {
                IconButton(onClick = { showCustom = true; hexInput = "" }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Brush, null, tint = theme.colors.type.dimmed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

//  Connections Section 

@Composable
private fun ConnectionsSection(
    settings: SettingsEntity, 
    theme: ZStreamTheme, 
    vm: SettingsViewModel, 
    isTv: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
) {
    val trakt by vm.traktState.collectAsStateWithLifecycle()
    val releaseChecksEnabled by vm.releaseChecksEnabled.collectAsStateWithLifecycle()
    val releaseCheckInterval by vm.releaseCheckInterval.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var notificationGranted by remember {
        mutableStateOf(
            android.os.Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationGranted = it
    }
    if (isTv) {
        Column(Modifier
            .padding(bottom = 32.dp)
            .padding(top = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Trakt", theme)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.settings.card.background).padding(16.dp)
            ) {
                Text(if (trakt.connected) "Connected${trakt.name?.let { " as $it" } ?: ""}" else "Not connected",
                    color = theme.colors.type.text, fontWeight = FontWeight.Bold)
                Text("Trakt connections are managed from a mobile device.", color = theme.colors.type.dimmed, fontSize = 11.sp)
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("TMDB API Key", theme)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.settings.card.background)
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("TMDB Token (optional)", color = theme.colors.type.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add your TMDB API key for direct TMDB-backed features.",
                        color = theme.colors.type.dimmed, fontSize = 11.sp, lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    var tokenVisible by remember { mutableStateOf(false) }
                    var tmdbInput by remember { mutableStateOf(settings.tmdbApiKey ?: "") }
                    var tmdbValidationState by remember { mutableStateOf<TokenValidationState>(TokenValidationState.Idle) }

                    LaunchedEffect(tmdbInput) {
                        if (tmdbInput.isBlank()) {
                            tmdbValidationState = TokenValidationState.Idle
                            vm.setTmdbApiKey(null)
                            return@LaunchedEffect
                        }
                        tmdbValidationState = TokenValidationState.Checking
                        delay(600)
                        val result = vm.validateTmdbKey(tmdbInput)
                        tmdbValidationState = result.fold(
                            onSuccess = { isValid ->
                                if (isValid) {
                                    vm.setTmdbApiKey(tmdbInput)
                                    TokenValidationState.Valid
                                } else {
                                    vm.setTmdbApiKey(null) // Prevent saving invalid key
                                    TokenValidationState.Invalid("Key was rejected by TMDB.")
                                }
                            },
                            onFailure = { TokenValidationState.Invalid(it.message ?: "Validation failed.") }
                        )
                    }

                    TvTextField(
                        value = tmdbInput,
                        onValueChange = { tmdbInput = it },
                        placeholder = "eyJ0eX...",
                        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        theme = theme,
                        modifier = if (firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        var showFocused by remember { mutableStateOf(false) }
                        ZsOutlinedWrapper(
                            shape = RoundedCornerShape(6.dp),
                            outlineColor = Color.White,
                            outlineWidth = 2.dp,
                            gap = 2.dp,
                            visible = showFocused,
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .onFocusChanged { showFocused = it.isFocused }
                                    .focusable()
                                    .onKeyEvent { event ->
                                        if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                                            tokenVisible = !tokenVisible
                                            true
                                        } else false
                                    }
                                    .clickable { tokenVisible = !tokenVisible }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Text(if (tokenVisible) "Hide" else "Show",
                                    color = theme.colors.global.accentA, fontSize = 11.sp)
                            }
                        }
                        var clearFocused by remember { mutableStateOf(false) }
                        ZsOutlinedWrapper(
                            shape = RoundedCornerShape(6.dp),
                            outlineColor = Color.White,
                            outlineWidth = 2.dp,
                            gap = 2.dp,
                            visible = clearFocused,
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .onFocusChanged { clearFocused = it.isFocused }
                                    .focusable()
                                    .onKeyEvent { event ->
                                        if ((event.key == Key.DirectionCenter || event.key == Key.Enter) && tmdbInput.isNotEmpty()) {
                                            tmdbInput = ""
                                            vm.setTmdbApiKey(null)
                                            true
                                        } else false
                                    }
                                    .clickable {
                                        tmdbInput = ""
                                        vm.setTmdbApiKey(null)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Text("Clear",
                                    color = if (tmdbInput.isNotEmpty()) theme.colors.buttons.danger else theme.colors.type.dimmed,
                                    fontSize = 11.sp)
                            }
                        }
                    }
                    when (val state = tmdbValidationState) {
                        TokenValidationState.Idle -> Unit
                        TokenValidationState.Checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = theme.colors.global.accentA,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Checking key…", color = theme.colors.type.dimmed, fontSize = 11.sp)
                            }
                        }
                        TokenValidationState.Valid -> {
                            ZsStatusBanner(
                                message = "Key accepted by TMDB.",
                                variant = ZsStatusBannerVariant.Success,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        is TokenValidationState.Invalid -> {
                            ZsStatusBanner(
                                message = state.message,
                                variant = ZsStatusBannerVariant.Error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Febbox / Aurora API", theme)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.colors.settings.card.background)
            ) {
                var showInstructions by remember { mutableStateOf(settings.febboxKey != null) }
                var instrFocused by remember { mutableStateOf(false) }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier
                        .weight(1f)
                        .padding(end = 12.dp)) {
                        Text("Aurora API (4K) (unimplemented)", color = theme.colors.type.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Bring your own FREE Febbox account to unlock Aurora API — the best sources with 4K quality, Dolby Atmos, and the fastest load times.",
                            color = theme.colors.type.dimmed, fontSize = 11.sp, lineHeight = 16.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Your token is never stored on our servers — it is sent directly from your device to Febbox.",
                            color = theme.colors.type.dimmed, fontSize = 10.sp, lineHeight = 14.sp,
                        )
                    }
                    ZsOutlinedWrapper(
                        shape = CircleShape,
                        outlineColor = Color.White,
                        outlineWidth = 2.dp,
                        gap = 2.dp,
                        visible = instrFocused,
                    ) {
                    Switch(
                        checked = showInstructions,
                        onCheckedChange = { showInstructions = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.colors.type.emphasis,
                            checkedTrackColor = theme.colors.global.accentA,
                            uncheckedThumbColor = theme.colors.type.dimmed,
                            uncheckedTrackColor = theme.colors.background.secondary,
                        ),
                        modifier = Modifier
                            .onFocusChanged { instrFocused = it.isFocused }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Switch,
                                onClick = { showInstructions = !showInstructions },
                            ),
                    )
                }
                }

                if (showInstructions) {
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))

                    ZsBottomSheetSectionCard(
                        title = "To get your Febbox token",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        val steps = listOf(
                            "Go to febbox.com and log in with Google (use a fresh account!)",
                            "Open DevTools or inspect the page",
                            "Go to Application tab → Cookies",
                            "Copy the 'ui' cookie's value.",
                            "Close the tab, but do NOT logout!",
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            steps.forEachIndexed { i, step ->
                                Text(
                                    "${i + 1}. $step",
                                    color = theme.colors.type.text, fontSize = 11.sp, lineHeight = 16.sp,
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))

                    ZsBottomSheetSectionCard(
                        title = "Token",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        var tokenVisible by remember { mutableStateOf(false) }
                        val febboxValue = settings.febboxKey ?: ""
                        TvTextField(
                            value = febboxValue,
                            onValueChange = { vm.setFebboxKey(it.ifEmpty { null }) },
                            placeholder = "eyJ0eXAiOiJKV1QiLCJhbGciOi...",
                            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            theme = theme,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            var showFocused by remember { mutableStateOf(false) }
                            ZsOutlinedWrapper(
                                shape = RoundedCornerShape(6.dp),
                                outlineColor = Color.White,
                                outlineWidth = 2.dp,
                                gap = 2.dp,
                                visible = showFocused,
                            ) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .onFocusChanged { showFocused = it.isFocused }
                                        .focusable()
                                        .onKeyEvent { event ->
                                            if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                                                tokenVisible = !tokenVisible
                                                true
                                            } else false
                                        }
                                        .clickable { tokenVisible = !tokenVisible }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Text(if (tokenVisible) "Hide" else "Show",
                                        color = theme.colors.global.accentA, fontSize = 11.sp)
                                }
                            }
                            var clearFocused by remember { mutableStateOf(false) }
                            ZsOutlinedWrapper(
                                shape = RoundedCornerShape(6.dp),
                                outlineColor = Color.White,
                                outlineWidth = 2.dp,
                                gap = 2.dp,
                                visible = clearFocused,
                            ) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .onFocusChanged { clearFocused = it.isFocused }
                                        .focusable()
                                        .onKeyEvent { event ->
                                            if ((event.key == Key.DirectionCenter || event.key == Key.Enter) && febboxValue.isNotEmpty()) {
                                                vm.setFebboxKey(null)
                                                true
                                            } else false
                                        }
                                        .clickable { vm.setFebboxKey(null) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Text("Clear",
                                        color = if (febboxValue.isNotEmpty()) theme.colors.buttons.danger else theme.colors.type.dimmed,
                                        fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("External Services", theme)
            ZsBottomSheetSectionCard(
                title = "TheIntroDB (optional)",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Add your TheIntroDB API key to submit new skip segments from the player.",
                    color = theme.colors.type.dimmed, fontSize = 11.sp, lineHeight = 16.sp,
                )
                Spacer(Modifier.height(10.dp))
                var tokenVisible by remember { mutableStateOf(false) }
                var tidbInput by remember(settings.tidbKey) {
                    mutableStateOf(
                        settings.tidbKey ?: ""
                    )
                }
                var tidbValidationState by remember {
                    mutableStateOf<TokenValidationState>(
                        TokenValidationState.Idle
                    )
                }

                LaunchedEffect(tidbInput) {
                    if (tidbInput.isBlank()) {
                        tidbValidationState = TokenValidationState.Idle
                        vm.setTidbKey(null)
                        return@LaunchedEffect
                    }
                    tidbValidationState = TokenValidationState.Checking
                    delay(600)
                    val result = vm.validateTidbKey(tidbInput)
                    tidbValidationState = result.fold(
                        onSuccess = { isValid ->
                            if (isValid) {
                                vm.setTidbKey(tidbInput)
                                TokenValidationState.Valid
                            } else {
                                vm.setTidbKey(null) // Prevent saving invalid key
                                TokenValidationState.Invalid("Key was rejected by TheIntroDB.")
                            }
                        },
                        onFailure = {
                            TokenValidationState.Invalid(
                                it.message ?: "Validation failed."
                            )
                        }
                    )
                }
                TvTextField(
                    value = tidbInput,
                    onValueChange = { tidbInput = it },
                    placeholder = "theintrodb:user...",
                    visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    theme = theme,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    var showFocused by remember { mutableStateOf(false) }
                    ZsOutlinedWrapper(
                        shape = RoundedCornerShape(6.dp),
                        outlineColor = Color.White,
                        outlineWidth = 2.dp,
                        gap = 2.dp,
                        visible = showFocused,
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .onFocusChanged { showFocused = it.isFocused }
                                .focusable()
                                .onKeyEvent { event ->
                                    if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                                        tokenVisible = !tokenVisible
                                        true
                                    } else false
                                }
                                .clickable { tokenVisible = !tokenVisible }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(
                                if (tokenVisible) "Hide" else "Show",
                                color = theme.colors.global.accentA, fontSize = 11.sp
                            )
                        }
                    }
                    var clearFocused by remember { mutableStateOf(false) }
                    ZsOutlinedWrapper(
                        shape = RoundedCornerShape(6.dp),
                        outlineColor = Color.White,
                        outlineWidth = 2.dp,
                        gap = 2.dp,
                        visible = clearFocused,
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .onFocusChanged { clearFocused = it.isFocused }
                                .focusable()
                                .onKeyEvent { event ->
                                    if ((event.key == Key.DirectionCenter || event.key == Key.Enter) && tidbInput.isNotEmpty()) {
                                        tidbInput = ""
                                        vm.setTidbKey(null)
                                        true
                                    } else false
                                }
                                .clickable {
                                    tidbInput = ""
                                    vm.setTidbKey(null)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "Clear",
                                color = if (tidbInput.isNotEmpty()) theme.colors.buttons.danger else theme.colors.type.dimmed,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                when (val state = tidbValidationState) {
                    TokenValidationState.Idle -> Unit
                    TokenValidationState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = theme.colors.global.accentA,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Checking key…",
                                color = theme.colors.type.dimmed,
                                fontSize = 11.sp
                            )
                        }
                    }

                    TokenValidationState.Valid -> {
                        ZsStatusBanner(
                            message = "Key accepted by TheIntroDB.",
                            variant = ZsStatusBannerVariant.Success,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    is TokenValidationState.Invalid -> {
                        ZsStatusBanner(
                            message = state.message,
                            variant = ZsStatusBannerVariant.Error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    } else {
        Column(Modifier
            .padding(bottom = 32.dp)
            .padding(top = if (isTv) 16.dp else 0.dp)) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("App update checks", theme)
            SettingsCard(theme) {
                ZsSwitchRow(
                    title = "Background release checks",
                    subtitle = "Check the saved GitHub repository for new APK releases.",
                    checked = releaseChecksEnabled,
                    onCheckedChange = { enabled ->
                        vm.setReleaseChecksEnabled(enabled)
                        if (enabled && android.os.Build.VERSION.SDK_INT >= 33 && !notificationGranted) {
                            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    notice = if (releaseChecksEnabled && !notificationGranted) "Notification permission is disabled; the app will still show the update prompt when opened." else null,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                if (releaseChecksEnabled) {
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))
                    ZsDropdownRow(
                        title = "Check interval",
                        options = ReleaseCheckInterval.entries.map { it.label },
                        selected = releaseCheckInterval.label,
                        onSelect = vm::setReleaseCheckInterval,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("TMDB API Key", theme)
            SettingsCard(theme) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("TMDB Token (optional)", color = theme.colors.type.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add your TMDB API key for direct TMDB-backed features.",
                        color = theme.colors.type.dimmed,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    var tokenVisible by remember { mutableStateOf(false) }
                    var tmdbInput by remember(settings.tmdbApiKey) { mutableStateOf(settings.tmdbApiKey ?: "") }
                    var tmdbValidationState by remember { mutableStateOf<TokenValidationState>(TokenValidationState.Idle) }

                    LaunchedEffect(tmdbInput) {
                        if (tmdbInput.isBlank()) {
                            tmdbValidationState = TokenValidationState.Idle
                            vm.setTmdbApiKey(null)
                            return@LaunchedEffect
                        }
                        tmdbValidationState = TokenValidationState.Checking
                        delay(600)
                        val result = vm.validateTmdbKey(tmdbInput)
                        tmdbValidationState = result.fold(
                            onSuccess = { isValid ->
                                if (isValid) {
                                    vm.setTmdbApiKey(tmdbInput)
                                    TokenValidationState.Valid
                                } else {
                                    vm.setTmdbApiKey(null) // Prevent saving invalid key
                                    TokenValidationState.Invalid("Key was rejected by TMDB.")
                                }
                            },
                            onFailure = { TokenValidationState.Invalid(it.message ?: "Validation failed.") }
                        )
                    }

                    BasicTextField(
                        value = tmdbInput,
                        onValueChange = { tmdbInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.colors.background.secondary)
                            .border(
                                1.dp,
                                theme.colors.type.divider.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        textStyle = TextStyle(color = theme.colors.type.text, fontSize = 12.sp),
                        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box {
                                if (tmdbInput.isEmpty()) {
                                    Text(
                                        "eyJ0eX...",
                                        color = theme.colors.type.dimmed,
                                        fontSize = 12.sp,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { tokenVisible = !tokenVisible }) {
                            Text(
                                if (tokenVisible) "Hide" else "Show",
                                color = theme.colors.global.accentA,
                                fontSize = 11.sp,
                            )
                        }
                        TextButton(onClick = { 
                            tmdbInput = ""
                            vm.setTmdbApiKey(null) 
                        }, enabled = tmdbInput.isNotEmpty()) {
                            Text(
                                "Clear",
                                color = if (tmdbInput.isNotEmpty()) theme.colors.buttons.danger else theme.colors.type.dimmed,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    when (val state = tmdbValidationState) {
                        TokenValidationState.Idle -> Unit
                        TokenValidationState.Checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = theme.colors.global.accentA,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Checking key…", color = theme.colors.type.dimmed, fontSize = 11.sp)
                            }
                        }
                        TokenValidationState.Valid -> {
                            ZsStatusBanner(
                                message = "Key accepted by TMDB.",
                                variant = ZsStatusBannerVariant.Success,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        is TokenValidationState.Invalid -> {
                            ZsStatusBanner(
                                message = state.message,
                                variant = ZsStatusBannerVariant.Error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Febbox / Aurora API", theme)
            SettingsCard(theme) {
                var showInstructions by remember { mutableStateOf(settings.febboxKey != null) }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier
                        .weight(1f)
                        .padding(end = 12.dp)) {
                        Text("Aurora API (4K) (unimplemented)", color = theme.colors.type.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Bring your own FREE Febbox account to unlock Aurora API — the best sources with 4K quality, Dolby Atmos, and the fastest load times.",
                            color = theme.colors.type.dimmed, fontSize = 11.sp, lineHeight = 16.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Your token is never stored on our servers — it is sent directly from your device to Febbox.",
                            color = theme.colors.type.dimmed, fontSize = 10.sp, lineHeight = 14.sp,
                        )
                    }
                    Switch(
                        checked = showInstructions,
                        onCheckedChange = { showInstructions = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.colors.type.emphasis,
                            checkedTrackColor = theme.colors.global.accentA,
                            uncheckedThumbColor = theme.colors.type.dimmed,
                            uncheckedTrackColor = theme.colors.background.secondary,
                        ),
                    )
                }

                if (showInstructions) {
                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))

                    ZsBottomSheetSectionCard(
                        title = "To get your Febbox token",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        val steps = listOf(
                            "Go to febbox.com and log in with Google (use a fresh account!)",
                            "Open DevTools or inspect the page",
                            "Go to Application tab → Cookies",
                            "Copy the 'ui' cookie's value.",
                            "Close the tab, but do NOT logout!",
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            steps.forEachIndexed { i, step ->
                                Text(
                                    "${i + 1}. $step",
                                    color = theme.colors.type.text,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = theme.colors.utils.divider.copy(alpha = 0.2f))

                    ZsBottomSheetSectionCard(
                        title = "Token",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        var tokenVisible by remember { mutableStateOf(false) }
                        val febboxValue = settings.febboxKey ?: ""
                        val textStyle = TextStyle(
                            color = theme.colors.type.text, fontSize = 12.sp,
                        )
                        BasicTextField(
                            value = febboxValue,
                            onValueChange = { vm.setFebboxKey(it.ifEmpty { null }) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.colors.background.secondary)
                                .border(
                                    1.dp,
                                    theme.colors.type.divider.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            textStyle = textStyle,
                            visualTransformation = if (tokenVisible) VisualTransformation.None
                                else PasswordVisualTransformation(),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box {
                                    if (febboxValue.isEmpty()) {
                                        Text(
                                            "eyJ0eXAiOiJKV1QiLCJhbGciOi...",
                                            color = theme.colors.type.dimmed,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { tokenVisible = !tokenVisible }) {
                                Text(
                                    if (tokenVisible) "Hide" else "Show",
                                    color = theme.colors.global.accentA,
                                    fontSize = 11.sp,
                                )
                            }
                            TextButton(
                                onClick = { vm.setFebboxKey(null) },
                                enabled = febboxValue.isNotEmpty(),
                            ) {
                                Text(
                                    "Clear",
                                    color = if (febboxValue.isNotEmpty()) theme.colors.buttons.danger else theme.colors.type.dimmed,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Integrations", theme)
            SettingsCard(theme) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("TheIntroDB", color = theme.colors.type.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Contribute to TheIntroDB by leaving feedback on intro, recap, and credits segments.",
                        color = theme.colors.type.dimmed,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("API Key", color = theme.colors.type.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    var tokenVisible by remember { mutableStateOf(false) }
                    var tidbInput by remember(settings.tidbKey) { mutableStateOf(settings.tidbKey ?: "") }
                    var tidbValidationState by remember { mutableStateOf<TokenValidationState>(TokenValidationState.Idle) }

                    LaunchedEffect(tidbInput) {
                        if (tidbInput.isBlank()) {
                            tidbValidationState = TokenValidationState.Idle
                            vm.setTidbKey(null)
                            return@LaunchedEffect
                        }
                        tidbValidationState = TokenValidationState.Checking
                        delay(600)
                        val result = vm.validateTidbKey(tidbInput)
                        tidbValidationState = result.fold(
                            onSuccess = { isValid ->
                                if (isValid) {
                                    vm.setTidbKey(tidbInput)
                                    TokenValidationState.Valid
                                } else {
                                    vm.setTidbKey(null) // Prevent saving invalid key
                                    TokenValidationState.Invalid("Key was rejected by TheIntroDB.")
                                }
                            },
                            onFailure = { TokenValidationState.Invalid(it.message ?: "Validation failed.") }
                        )
                    }
                    BasicTextField(
                        value = tidbInput,
                        onValueChange = { tidbInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.colors.background.secondary)
                            .border(
                                1.dp,
                                theme.colors.type.divider.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        textStyle = TextStyle(color = theme.colors.type.text, fontSize = 12.sp),
                        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box {
                                if (tidbInput.isEmpty()) {
                                    Text(
                                        "theintrodb:user...",
                                        color = theme.colors.type.dimmed,
                                        fontSize = 12.sp,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IntegrationActionOutline(theme) {
                            TextButton(onClick = { tokenVisible = !tokenVisible }) {
                                Text(
                                    if (tokenVisible) "Hide" else "Show",
                                    color = theme.colors.global.accentA,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        IntegrationActionOutline(theme) {
                            TextButton(onClick = {
                                tidbInput = ""
                                vm.setTidbKey(null)
                            }, enabled = tidbInput.isNotEmpty()) {
                                Text(
                                    "Clear",
                                    color = if (tidbInput.isNotEmpty()) theme.colors.buttons.danger else theme.colors.type.dimmed,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                    when (val state = tidbValidationState) {
                        TokenValidationState.Idle -> Unit
                        TokenValidationState.Checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = theme.colors.global.accentA,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Checking key…", color = theme.colors.type.dimmed, fontSize = 11.sp)
                            }
                        }
                        TokenValidationState.Valid -> {
                            ZsStatusBanner(
                                message = "Key accepted by TheIntroDB.",
                                variant = ZsStatusBannerVariant.Success,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        is TokenValidationState.Invalid -> {
                            ZsStatusBanner(
                                message = state.message,
                                variant = ZsStatusBannerVariant.Error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            WyzieIntegrationCard(settings, theme, vm, context)
            Spacer(Modifier.height(16.dp))
            TraktIntegrationCard(trakt, theme, vm, context)
            Spacer(Modifier.height(16.dp))
            SimklIntegrationCard(theme)
        }
    }
}

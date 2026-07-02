package com.zstream.android.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.zstream.android.data.adb.APK_ROWS_PER_PAGE
import com.zstream.android.data.adb.DEFAULT_RELEASE_REPOSITORY
import com.zstream.android.data.adb.GithubApkAsset
import com.zstream.android.data.adb.GithubReleaseCatalog
import com.zstream.android.data.adb.InstallProgress
import com.zstream.android.data.adb.ReleaseUpdateManager
import com.zstream.android.data.adb.TvAdbManager
import com.zstream.android.data.adb.releasePage
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.theme.ZStreamTheme
import com.zstream.android.ui.components.themed.ZsButton
import com.zstream.android.ui.components.themed.ZsButtonVariant
import com.zstream.android.ui.components.themed.ZsCard
import com.zstream.android.ui.components.themed.ZsCardVariant
import com.zstream.android.ui.components.themed.ZsOutlinedWrapper
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil

// Detection helpers — readable by both the wizard and MainActivity/HomeScreen
internal fun isAdbEnabled(context: android.content.Context): Boolean =
    Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

internal fun isDeveloperOptionsEnabled(context: android.content.Context): Boolean =
    Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

private enum class WizardStep {
    CONFIRM,         // "A new update is available, do you want to update?" prompt
    PHONE,           // phone-side instructions and Android settings shortcuts
    CHECKING,        // brief auto-detect spinner, advances immediately
    UNLOCK_DEV,      // developer options are off → guide to build number
    ENABLE_ADB,      // developer options on but ADB off → guide to wireless debugging
    READY,           // ADB is on; user can continue to releases
    RELEASES,        // ADB on → paginated release list + install
}

private fun Modifier.tvAdbFocusClampAll(): Modifier = focusProperties {
    up = FocusRequester.Cancel
    down = FocusRequester.Cancel
    left = FocusRequester.Cancel
    right = FocusRequester.Cancel
}

private fun Modifier.tvAdbFocusClampHorizontal(): Modifier = focusProperties {
    left = FocusRequester.Cancel
    right = FocusRequester.Cancel
}

private fun Modifier.tvAdbFocusClampVertical(): Modifier = focusProperties {
    up = FocusRequester.Cancel
    down = FocusRequester.Cancel
}

@Composable
fun TvUpdateWizardScreen(onDismiss: () -> Unit) {
    val theme = LocalZStreamTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val releaseCatalog = remember { GithubReleaseCatalog() }
    val releaseUpdateManager = remember(context) { ReleaseUpdateManager(context.applicationContext) }

    var step by remember { mutableStateOf(WizardStep.CONFIRM) }
    var apks by remember { mutableStateOf<List<GithubApkAsset>>(emptyList()) }
    var apkPage by remember { mutableStateOf(0) }
    var selectedApk by remember { mutableStateOf<GithubApkAsset?>(null) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<InstallProgress?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    val running = activeJob != null

    // Focus requester for the first actionable element — pulls focus away from home screen
    val primaryFocusRequester = remember { FocusRequester() }
    val contentScrollState = rememberScrollState()

    fun detect() {
        step = when {
            isAdbEnabled(context) -> WizardStep.READY
            isDeveloperOptionsEnabled(context) -> WizardStep.ENABLE_ADB
            else -> WizardStep.UNLOCK_DEV
        }
    }

    // Re-check when user returns from the Settings app (only relevant after CONFIRM)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (step != WizardStep.CONFIRM && step != WizardStep.CHECKING) detect()
    }

    fun openSettings(action: String) {
        try {
            context.startActivity(Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) { }
        }
    }

    fun openAboutDevice() {
        try {
            context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) { }
        }
    }

    fun scanReleases() {
        activeJob = scope.launch {
            lastError = null
            status = "Scanning GitHub releases…"
            try {
                val url = releaseUpdateManager.repositoryUrl.ifBlank { DEFAULT_RELEASE_REPOSITORY }
                apks = withContext(Dispatchers.IO) { releaseCatalog.loadAllApks(url) }
                releaseUpdateManager.recordScan(apks)
                apkPage = 0
                status = if (apks.isEmpty()) "No APK assets found." else "Found ${apks.size} APK${if (apks.size == 1) "" else "s"}."
            } catch (_: CancellationException) {
                status = "Cancelled."
            } catch (t: Throwable) {
                lastError = t.message ?: "Unknown error"
                status = "Scan failed."
            } finally {
                activeJob = null
            }
        }
    }

    // Auto-scan when we land on the RELEASES step
    LaunchedEffect(step) {
        if (step == WizardStep.RELEASES && apks.isEmpty() && !running) scanReleases()
    }

    BackHandler {
        when {
            step == WizardStep.CONFIRM -> onDismiss()
            running -> Unit
            step == WizardStep.PHONE -> step = WizardStep.CONFIRM
            step == WizardStep.CHECKING -> step = WizardStep.CONFIRM
            step == WizardStep.UNLOCK_DEV -> step = WizardStep.CONFIRM
            step == WizardStep.ENABLE_ADB -> step = WizardStep.UNLOCK_DEV
            step == WizardStep.READY -> step = WizardStep.RELEASES
            step == WizardStep.RELEASES && selectedApk != null -> selectedApk = null
            step == WizardStep.RELEASES -> step = WizardStep.CONFIRM
        }
    }

    LaunchedEffect(step, selectedApk, apkPage, apks.size, running) {
        if (step == WizardStep.CONFIRM) return@LaunchedEffect
        withFrameNanos { }
        runCatching { primaryFocusRequester.requestFocus() }
    }

    if (step == WizardStep.CONFIRM) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ZsCard(
                variant = ZsCardVariant.Modal,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .widthIn(max = 760.dp),
            ) {
                val version = releaseUpdateManager.pendingVersion
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "ZStream update available",
                        color = theme.colors.type.emphasis,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "A new APK release${version.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""} is available. Would you like to update now?",
                        color = theme.colors.type.text,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ZsButton(
                            text = "Update from phone",
                            onClick = {
                                step = WizardStep.PHONE
                            },
                            modifier = Modifier.focusRequester(primaryFocusRequester).tvAdbFocusClampVertical(),
                        )
                        ZsButton(
                            text = "Update on this TV",
                            onClick = {
                                step = WizardStep.CHECKING
                                detect()
                            },
                            modifier = Modifier.tvAdbFocusClampVertical(),
                        )
                        ZsButton(
                            text = "Not now",
                            onClick = onDismiss,
                            variant = ZsButtonVariant.Secondary,
                            modifier = Modifier.tvAdbFocusClampVertical(),
                        )
                    }
                }
            }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                primaryFocusRequester.requestFocus()
            }
        }
        return
    }

    Surface(
        color = theme.colors.background.main,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Header row — hidden on CONFIRM (it's a standalone prompt)
            if (step != WizardStep.CONFIRM) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when (step) {
                            WizardStep.CHECKING -> "Checking…"
                            WizardStep.UNLOCK_DEV -> "Enable developer options"
                            WizardStep.ENABLE_ADB -> "Enable ADB debugging"
                            WizardStep.RELEASES -> if (selectedApk != null) "Release details" else "Available releases"
                            else -> ""
                        },
                        color = theme.colors.type.emphasis,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ZsButton(
                        text = "Close",
                        onClick = onDismiss,
                        enabled = !running,
                        variant = ZsButtonVariant.Secondary,
                        modifier = Modifier.tvAdbFocusClampVertical(),
                    )
                }
                HorizontalDivider(color = theme.colors.type.divider.copy(alpha = 0.18f))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (step == WizardStep.PHONE) Modifier else Modifier.verticalScroll(contentScrollState))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    WizardStep.CONFIRM -> Unit

                    WizardStep.PHONE -> {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(3f),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    "Phone update flow",
                                    color = theme.colors.type.emphasis,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Use the phone to do the real setup. In ZStream on your phone, open the TV button in the top nav bar and follow the update steps there.",
                                    color = theme.colors.type.text,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                )
                                ZsStatusBanner(
                                    message = "Keep the phone and TV on the same Wi-Fi. If the TV is on Ethernet, that is fine as long as it is on the same local network.",
                                    variant = ZsStatusBannerVariant.Info,
                                )
                                StepCard(theme, "What to do on the phone") {
                                    Text(
                                        "Open ZStream on the phone, tap the TV button in the top navigation bar, and follow the in-app update instructions. This page only links to the Android settings you may need.",
                                        color = theme.colors.type.secondary,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ZsButton(
                                    text = "Wi-Fi settings",
                                    onClick = { openSettings(Settings.ACTION_WIFI_SETTINGS) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(primaryFocusRequester)
                                        .tvAdbFocusClampHorizontal(),
                                    buttonModifier = Modifier.fillMaxWidth(),
                                )
                                ZsButton(
                                    text = "About / Build number",
                                    onClick = { openAboutDevice() },
                                    variant = ZsButtonVariant.Secondary,
                                    modifier = Modifier.fillMaxWidth().tvAdbFocusClampHorizontal(),
                                    buttonModifier = Modifier.fillMaxWidth(),
                                )
                                ZsButton(
                                    text = "Developer options/ADB debugging/Wireless debugging",
                                    onClick = { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
                                    variant = ZsButtonVariant.Secondary,
                                    modifier = Modifier.fillMaxWidth().tvAdbFocusClampHorizontal(),
                                    buttonModifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    WizardStep.CHECKING -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp).focusRequester(primaryFocusRequester).tvAdbFocusClampVertical(),
                                color = theme.colors.global.accentA,
                                strokeWidth = 3.dp,
                            )
                        }
                    }

                    WizardStep.UNLOCK_DEV -> {
                        ZsStatusBanner(
                            message = "Developer options are not enabled on this TV.",
                            variant = ZsStatusBannerVariant.Info,
                        )
                        StepCard(theme, "Step 1 — Open About") {
                            Text(
                                "Go to Settings → Device Preferences → About. If your TV hides it elsewhere, the buttons below jump to the right places.",
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                                ZsButton(
                                    text = "About / Build number",
                                    onClick = { openAboutDevice() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(primaryFocusRequester)
                                        .tvAdbFocusClampAll(),
                                )
                        }
                        StepCard(theme, "Step 2 — Tap Build Number 7 times") {
                            Text(
                                "Find the Build Number entry and tap it 7 times. You'll see a message confirming that Developer Options are now enabled.",
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                        StepCard(theme, "Step 3 — Come back here") {
                            Text(
                                "Press Back or return to ZStream. This screen detects the change automatically.",
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }

                    WizardStep.ENABLE_ADB -> {
                        ZsStatusBanner(
                            message = "Developer options are enabled. Turn on ADB debugging, then come back here.",
                            variant = ZsStatusBannerVariant.Info,
                        )
                        StepCard(theme, "Step 1 — Open the right settings page") {
                            Text(
                                "Open Developer Options on the TV and switch on ADB debugging. The buttons below jump there directly.",
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                                ZsButton(
                                    text = "Developer options/ADB debugging/Wireless debugging",
                                    onClick = { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(primaryFocusRequester)
                                        .tvAdbFocusClampAll(),
                                )
                        }
                        StepCard(theme, "Step 2 — Enable Wireless Debugging") {
                            Text(
                                "Turn on USB Debugging, then find Wireless Debugging and enable it too. Both are needed to push the APK over Wi-Fi.",
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                        StepCard(theme, "Step 3 — Come back here") {
                            Text(
                                "Press Back or return to ZStream. When ADB is on, this screen will confirm it and unlock the releases page.",
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }

                    WizardStep.READY -> {
                        ZsStatusBanner(
                            message = "ADB debugging is on. You did the right thing.",
                            variant = ZsStatusBannerVariant.Success,
                        )
                        StepCard(theme, "Next step") {
                            Text(
                                "You can continue to the available releases page now. There you can choose the APK and install the update in the background.",
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                        ZsButton(
                            text = "Open available releases",
                            onClick = { step = WizardStep.RELEASES },
                            modifier = Modifier.focusRequester(primaryFocusRequester).tvAdbFocusClampHorizontal(),
                        )
                    }

                    WizardStep.RELEASES -> {
                        if (selectedApk != null) {
                            ReleaseDetailContent(
                                apk = selectedApk!!,
                                status = status,
                                progress = progress,
                                lastError = lastError,
                                running = running,
                                theme = theme,
                                primaryFocusRequester = primaryFocusRequester,
                                onInstall = {
                                    activeJob = scope.launch {
                                        val operationJob = coroutineContext.job
                                        lastError = null
                                        status = "Connecting…"
                                        try {
                                            val tvManager = TvAdbManager.get(context)
                                            val result = withContext(Dispatchers.IO) {
                                                tvManager.installFromUrl(
                                                    selectedApk!!.downloadUrl,
                                                    onProgress = { progress = it },
                                                    isCancelled = { !operationJob.isActive },
                                                )
                                            }
                                            status = "Installed on ${result.model}."
                                            progress = null
                                        } catch (_: CancellationException) {
                                            status = "Cancelled."
                                            progress = null
                                        } catch (t: Throwable) {
                                            lastError = t.message ?: "Unknown error"
                                            status = "Install failed."
                                            progress = null
                                        } finally {
                                            activeJob = null
                                        }
                                    }
                                },
                                onCancel = { activeJob?.cancel() },
                                onBack = { selectedApk = null },
                            )
                        } else {
                            if (status.isNotBlank()) {
                                Text(status, color = theme.colors.type.secondary, fontSize = 13.sp)
                            }
                            if (running) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = theme.colors.global.accentA,
                                    trackColor = theme.colors.background.secondary,
                                )
                            }
                            lastError?.let {
                                ZsStatusBanner(it, variant = ZsStatusBannerVariant.Error)
                            }
                            if (apks.isNotEmpty()) {
                                val page = releasePage(apks, apkPage)
                                val totalPages = ceil(apks.size.toDouble() / APK_ROWS_PER_PAGE).toInt()
                                page.forEachIndexed { index, apk ->
                                    ApkRow(
                                        apk = apk,
                                        theme = theme,
                                        modifier = if (index == 0) Modifier.focusRequester(primaryFocusRequester).tvAdbFocusClampHorizontal() else Modifier.tvAdbFocusClampHorizontal(),
                                        onClick = { selectedApk = apk },
                                    )
                                }
                                if (totalPages > 1) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ZsButton(
                                            text = "← Prev",
                                            onClick = { apkPage-- },
                                            enabled = apkPage > 0,
                                            variant = ZsButtonVariant.Secondary,
                                            modifier = Modifier, // might need to fix focus here
                                        )
                                        Text(
                                            "${apkPage + 1} / $totalPages",
                                            color = theme.colors.type.secondary,
                                            fontSize = 13.sp,
                                        )
                                        ZsButton(
                                            text = "Next →",
                                            onClick = { apkPage++ },
                                            enabled = apkPage < totalPages - 1,
                                            variant = ZsButtonVariant.Secondary,
                                            modifier = Modifier, // might need to fix focus here
                                        )
                                    }
                                }
                            }
                            ZsButton(
                                text = if (running) "Scanning…" else "Refresh releases",
                                onClick = { scanReleases() },
                                enabled = !running,
                                variant = ZsButtonVariant.Secondary,
                                modifier = Modifier.fillMaxWidth(), // might need to fix focus here
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    theme: ZStreamTheme,
    title: String,
    content: @Composable () -> Unit,
) {
    ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            content()
        }
    }
}

@Composable
private fun ApkRow(
    apk: GithubApkAsset,
    theme: ZStreamTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fmt = remember {
        SimpleDateFormat("MMM d, yyyy", Locale.US).apply { timeZone = TimeZone.getDefault() }
    }
    val date = remember(apk.releasePublishedAt ?: apk.releaseCreatedAt) {
        runCatching {
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.format(iso.parse(apk.releasePublishedAt ?: apk.releaseCreatedAt)!!)
        }.getOrDefault(apk.releasePublishedAt ?: apk.releaseCreatedAt)
    }
    var focused by remember { mutableStateOf(false) }
    ZsOutlinedWrapper(
        visible = focused,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        outlineColor = theme.colors.type.emphasis,
        gap = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .tvAdbFocusClampHorizontal()
            .onFocusChanged { focused = it.isFocused },
    ) {
        ZsCard(
            variant = ZsCardVariant.Elevated,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        apk.releaseTag.ifBlank { apk.releaseName }.ifBlank { apk.apkName },
                        color = theme.colors.type.emphasis,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Text(date, color = theme.colors.type.dimmed, fontSize = 12.sp)
                }
                Text(apk.apkName, color = theme.colors.type.secondary, fontSize = 12.sp)
                Text("%.1f MB".format(apk.size / 1_048_576.0), color = theme.colors.type.dimmed, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ReleaseDetailContent(
    apk: GithubApkAsset,
    status: String,
    progress: InstallProgress?,
    lastError: String?,
    running: Boolean,
    theme: ZStreamTheme,
    primaryFocusRequester: FocusRequester,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                apk.releaseTag.ifBlank { apk.releaseName }.ifBlank { apk.apkName },
                color = theme.colors.type.emphasis,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(apk.apkName, color = theme.colors.type.secondary, fontSize = 13.sp)
            Text("Size: %.1f MB".format(apk.size / 1_048_576.0), color = theme.colors.type.dimmed, fontSize = 12.sp)
            if (apk.releaseDescription.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(apk.releaseDescription, color = theme.colors.type.text, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }

    if (status.isNotBlank()) {
        Text(status, color = theme.colors.type.secondary, fontSize = 13.sp)
    }

    progress?.let { p ->
        val (label, fraction) = when (p) {
            InstallProgress.Connecting -> "Connecting to TV…" to null
            is InstallProgress.Downloading -> "Downloading…" to if (p.totalBytes > 0) p.bytes.toFloat() / p.totalBytes else null
            is InstallProgress.Transferring -> "Transferring to TV…" to if (p.totalBytes > 0) p.bytes.toFloat() / p.totalBytes else null
            InstallProgress.Installing -> "Installing on TV…" to null
        }
        Text(label, color = theme.colors.type.secondary, fontSize = 12.sp)
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = theme.colors.global.accentA,
                trackColor = theme.colors.background.secondary,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = theme.colors.global.accentA,
                trackColor = theme.colors.background.secondary,
            )
        }
    }

    lastError?.let { ZsStatusBanner(it, variant = ZsStatusBannerVariant.Error) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ZsButton(
            text = "← Back",
            onClick = onBack,
            enabled = !running,
            variant = ZsButtonVariant.Secondary,
            modifier = Modifier.tvAdbFocusClampVertical(),
        )
        if (running) {
            ZsButton(
                text = "Cancel",
                onClick = onCancel,
                variant = ZsButtonVariant.Danger,
                modifier = Modifier.tvAdbFocusClampVertical(),
            )
        } else {
            ZsButton(
                text = "Install on TV",
                onClick = onInstall,
                modifier = Modifier
                    .focusRequester(primaryFocusRequester)
                    .tvAdbFocusClampVertical(),
            )
        }
    }
    Text(
        "Always allow the ADB debugging prompt on the TV when it appears. If the update was successful, the app will close. You can then restart the app.",
        color = theme.colors.type.success,
        fontSize = 15.sp,
        lineHeight = 18.sp,
    )
}

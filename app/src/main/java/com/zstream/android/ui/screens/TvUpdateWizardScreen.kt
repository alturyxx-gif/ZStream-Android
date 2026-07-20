package com.zstream.android.ui.screens

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.zstream.android.R
import com.zstream.android.data.adb.AdbFailureKind
import com.zstream.android.data.adb.AdbOperationException
import com.zstream.android.data.adb.DEFAULT_RELEASE_REPOSITORY
import com.zstream.android.data.adb.GithubApkAsset
import com.zstream.android.data.adb.GithubReleaseCatalog
import com.zstream.android.data.adb.InstallProgress
import com.zstream.android.data.adb.ReleaseUpdateManager
import com.zstream.android.data.adb.TvAdbManager
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
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
    var selectedApk by remember { mutableStateOf<GithubApkAsset?>(null) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<InstallProgress?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var isReleaseContentFocused by remember { mutableStateOf(false) }
    val running = activeJob != null

    // Focus requester for the first actionable element — pulls focus away from home screen
    val primaryFocusRequester = remember { FocusRequester() }
    val releaseFocusRequesters = remember(apks) { List(apks.size) { FocusRequester() } }
    val contentScrollState = rememberScrollState()

    fun detect() {
        step = when {
            isAdbEnabled(context) -> WizardStep.READY
            isDeveloperOptionsEnabled(context) -> WizardStep.ENABLE_ADB
            else -> WizardStep.UNLOCK_DEV
        }
    }

    // Only setup pages should advance when Android Settings returns to the app.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (step == WizardStep.UNLOCK_DEV || step == WizardStep.ENABLE_ADB) detect()
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
            status = context.getString(R.string.tv_update_scanning_releases)
            try {
                val url = releaseUpdateManager.repositoryUrl.ifBlank { DEFAULT_RELEASE_REPOSITORY }
                apks = withContext(Dispatchers.IO) { releaseCatalog.loadAllApks(url) }
                releaseUpdateManager.recordScan(apks)
                selectedApk = apks.firstOrNull()
                status = if (apks.isEmpty()) {
                    context.getString(R.string.tv_update_no_apks)
                } else {
                    context.resources.getQuantityString(R.plurals.tv_update_found_apks, apks.size, apks.size)
                }
            } catch (_: CancellationException) {
                status = context.getString(R.string.tv_update_cancelled)
            } catch (t: Throwable) {
                Log.w("TvUpdateWizard", "Release scan failed", t)
                lastError = tvUpdateScanError(context, t)
                status = context.getString(R.string.tv_update_scan_failed)
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
            step == WizardStep.RELEASES && isReleaseContentFocused -> {
                val index = apks.indexOfFirst { it.assetId == selectedApk?.assetId }.coerceAtLeast(0)
                releaseFocusRequesters.getOrNull(index)?.requestFocus() ?: primaryFocusRequester.requestFocus()
            }
            step == WizardStep.CONFIRM -> onDismiss()
            running -> Unit
            step == WizardStep.PHONE -> step = WizardStep.CONFIRM
            step == WizardStep.CHECKING -> step = WizardStep.CONFIRM
            step == WizardStep.UNLOCK_DEV -> step = WizardStep.CONFIRM
            step == WizardStep.ENABLE_ADB -> step = WizardStep.UNLOCK_DEV
            step == WizardStep.READY -> step = WizardStep.CONFIRM
            step == WizardStep.RELEASES -> step = WizardStep.CONFIRM
        }
    }

    LaunchedEffect(step, apks.size) {
        if (step == WizardStep.CONFIRM) return@LaunchedEffect
        withFrameNanos { }
        runCatching {
            when {
                step != WizardStep.RELEASES -> primaryFocusRequester.requestFocus()
                releaseFocusRequesters.isNotEmpty() -> releaseFocusRequesters.first().requestFocus()
                else -> primaryFocusRequester.requestFocus()
            }
        }
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
                        stringResource(R.string.tv_update_available),
                        color = theme.colors.type.emphasis,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (version.isBlank()) stringResource(R.string.tv_update_prompt)
                        else stringResource(R.string.tv_update_prompt_version, version),
                        color = theme.colors.type.text,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ZsButton(
                            text = stringResource(R.string.tv_update_from_phone),
                            onClick = {
                                step = WizardStep.PHONE
                            },
                            modifier = Modifier.focusRequester(primaryFocusRequester).tvAdbFocusClampVertical(),
                        )
                        ZsButton(
                            text = stringResource(R.string.tv_update_on_tv),
                            onClick = {
                                step = WizardStep.CHECKING
                                detect()
                            },
                            modifier = Modifier.tvAdbFocusClampVertical(),
                        )
                        ZsButton(
                            text = stringResource(R.string.tv_update_not_now),
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
                            WizardStep.CHECKING -> stringResource(R.string.tv_update_checking)
                            WizardStep.UNLOCK_DEV -> stringResource(R.string.tv_update_enable_developer_options)
                            WizardStep.ENABLE_ADB -> stringResource(R.string.tv_update_enable_adb)
                            WizardStep.RELEASES -> stringResource(R.string.tv_update_available_releases)
                            else -> ""
                        },
                        color = theme.colors.type.emphasis,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                HorizontalDivider(color = theme.colors.type.divider.copy(alpha = 0.18f))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (step == WizardStep.PHONE || step == WizardStep.RELEASES) Modifier else Modifier.verticalScroll(contentScrollState))
                    .then(if (step == WizardStep.RELEASES) Modifier else Modifier.padding(20.dp)),
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
                                    stringResource(R.string.tv_update_phone_flow),
                                    color = theme.colors.type.emphasis,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(R.string.tv_update_phone_flow_description),
                                    color = theme.colors.type.text,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                )
                                ZsStatusBanner(
                                    message = stringResource(R.string.tv_update_same_network),
                                    variant = ZsStatusBannerVariant.Info,
                                )
                                StepCard(theme, stringResource(R.string.tv_update_phone_instructions_title)) {
                                    Text(
                                        stringResource(R.string.tv_update_phone_instructions),
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
                                    text = stringResource(R.string.tv_update_wifi_settings),
                                    onClick = { openSettings(Settings.ACTION_WIFI_SETTINGS) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(primaryFocusRequester)
                                        .tvAdbFocusClampHorizontal(),
                                    buttonModifier = Modifier.fillMaxWidth(),
                                )
                                ZsButton(
                                    text = stringResource(R.string.tv_update_about_build_number),
                                    onClick = { openAboutDevice() },
                                    variant = ZsButtonVariant.Secondary,
                                    modifier = Modifier.fillMaxWidth().tvAdbFocusClampHorizontal(),
                                    buttonModifier = Modifier.fillMaxWidth(),
                                )
                                ZsButton(
                                    text = stringResource(R.string.tv_update_developer_adb_wireless),
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
                            message = stringResource(R.string.tv_update_developer_not_enabled),
                            variant = ZsStatusBannerVariant.Info,
                        )
                        StepCard(theme, stringResource(R.string.tv_update_step_open_about)) {
                            Text(
                                stringResource(R.string.tv_update_open_about_description),
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                                ZsButton(
                                    text = stringResource(R.string.tv_update_about_build_number),
                                    onClick = { openAboutDevice() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(primaryFocusRequester)
                                        .tvAdbFocusClampAll(),
                                )
                        }
                        StepCard(theme, stringResource(R.string.tv_update_step_tap_build_number)) {
                            Text(
                                stringResource(R.string.tv_update_tap_build_number_description),
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                        StepCard(theme, stringResource(R.string.tv_update_step_come_back)) {
                            Text(
                                stringResource(R.string.tv_update_return_description),
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }

                    WizardStep.ENABLE_ADB -> {
                        ZsStatusBanner(
                            message = stringResource(R.string.tv_update_developer_enabled),
                            variant = ZsStatusBannerVariant.Info,
                        )
                        StepCard(theme, stringResource(R.string.tv_update_step_open_settings)) {
                            Text(
                                stringResource(R.string.tv_update_open_settings_description),
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                                ZsButton(
                                    text = stringResource(R.string.tv_update_developer_adb_wireless),
                                    onClick = { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(primaryFocusRequester)
                                        .tvAdbFocusClampAll(),
                                )
                        }
                        StepCard(theme, stringResource(R.string.tv_update_step_enable_wireless)) {
                            Text(
                                stringResource(R.string.tv_update_enable_wireless_description),
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                        StepCard(theme, stringResource(R.string.tv_update_step_come_back)) {
                            Text(
                                stringResource(R.string.tv_update_return_adb_description),
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }

                    WizardStep.READY -> {
                        ZsStatusBanner(
                            message = stringResource(R.string.tv_update_adb_enabled),
                            variant = ZsStatusBannerVariant.Success,
                        )
                        StepCard(theme, stringResource(R.string.tv_update_next_step)) {
                            Text(
                                stringResource(R.string.tv_update_next_step_description),
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                        ZsButton(
                            text = stringResource(R.string.tv_update_open_releases),
                            onClick = { step = WizardStep.RELEASES },
                            modifier = Modifier.focusRequester(primaryFocusRequester).tvAdbFocusClampHorizontal(),
                        )
                    }

                    WizardStep.RELEASES -> {
                        val contentFocusRequester = remember { FocusRequester() }
                        val selectedReleaseIndex = apks.indexOfFirst { it.assetId == selectedApk?.assetId }.coerceAtLeast(0)
                        val selectedReleaseFocusRequester = releaseFocusRequesters.getOrElse(selectedReleaseIndex) { primaryFocusRequester }
                        Row(Modifier.fillMaxSize()) {
                            Column(
                                Modifier
                                    .widthIn(min = 280.dp, max = 340.dp)
                                    .fillMaxHeight()
                                    .background(Brush.verticalGradient(listOf(theme.colors.background.secondary, theme.colors.background.main)))
                                    .padding(vertical = 16.dp),
                            ) {
                                LazyColumn(Modifier.weight(1f).focusRestorer(selectedReleaseFocusRequester)) {
                                    itemsIndexed(apks, key = { _, apk -> apk.assetId }) { index, apk ->
                                        ApkRow(
                                            apk = apk,
                                            selected = apk.assetId == selectedApk?.assetId,
                                            theme = theme,
                                            modifier = Modifier
                                                .padding(top = 10.dp)
                                                .focusRequester(releaseFocusRequesters[index])
                                                .focusProperties { right = contentFocusRequester },
                                            onSelect = {
                                                if (!running && selectedApk?.assetId != apk.assetId) {
                                                    selectedApk = apk
                                                    status = ""
                                                    lastError = null
                                                }
                                            },
                                        )
                                    }
                                }
                                ZsButton(
                                    text = stringResource(if (running) R.string.tv_update_cancel else R.string.tv_update_refresh_releases),
                                    onClick = { if (running) activeJob?.cancel() else scanReleases() },
                                    variant = ZsButtonVariant.Secondary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .then(if (apks.isEmpty()) Modifier.focusRequester(primaryFocusRequester) else Modifier),
                                    buttonModifier = Modifier.fillMaxWidth()
                                )
                            }
                            Box(Modifier.widthIn(min = 1.dp, max = 1.dp).fillMaxHeight().background(theme.colors.utils.divider.copy(alpha = 0.15f)))
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .onFocusChanged { isReleaseContentFocused = it.hasFocus }
                                    .verticalScroll(rememberScrollState())
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                selectedApk?.let { apk ->
                            ReleaseDetailContent(
                                apk = apk,
                                status = status,
                                progress = progress,
                                lastError = lastError,
                                running = running,
                                theme = theme,
                                onInstall = {
                                    activeJob = scope.launch {
                                        val operationJob = coroutineContext.job
                                        lastError = null
                                        status = context.getString(R.string.tv_update_connecting)
                                        try {
                                            val tvManager = TvAdbManager.get(context)
                                            val result = withContext(Dispatchers.IO) {
                                                tvManager.installFromUrl(
                                                    apk.downloadUrl,
                                                    expectedDigest = apk.digest,
                                                    onProgress = { progress = it },
                                                    isCancelled = { !operationJob.isActive },
                                                )
                                            }
                                            status = context.getString(R.string.tv_update_installed_on, result.model)
                                            progress = null
                                        } catch (_: CancellationException) {
                                            status = context.getString(R.string.tv_update_cancelled)
                                            progress = null
                                        } catch (t: Throwable) {
                                            Log.w("TvUpdateWizard", "TV update install failed", t)
                                            if (t is AdbOperationException && t.kind == AdbFailureKind.CANCELLED) {
                                                lastError = null
                                                status = context.getString(R.string.tv_update_cancelled)
                                            } else {
                                                lastError = tvUpdateInstallError(context, t)
                                                status = context.getString(R.string.tv_update_install_failed)
                                            }
                                            progress = null
                                        } finally {
                                            activeJob = null
                                        }
                                    }
                                },
                                onCancel = { activeJob?.cancel() },
                                installFocusRequester = contentFocusRequester,
                                releaseFocusRequester = selectedReleaseFocusRequester,
                            )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun tvUpdateScanError(context: android.content.Context, error: Throwable): String =
    context.getString(tvUpdateScanErrorResource(error))

internal fun tvUpdateScanErrorResource(error: Throwable): Int =
    if (error is IllegalArgumentException) {
        R.string.tv_update_invalid_repository
    } else {
        R.string.tv_update_release_scan_error
    }

private fun tvUpdateInstallError(context: android.content.Context, error: Throwable): String =
    context.getString(tvUpdateInstallErrorResource(error))

internal fun tvUpdateInstallErrorResource(error: Throwable): Int =
    when ((error as? AdbOperationException)?.kind) {
        AdbFailureKind.DISCOVERY -> R.string.transport_error_discovery
        AdbFailureKind.PAIRING -> R.string.transport_error_pairing
        AdbFailureKind.PAIRING_REQUIRED -> R.string.transport_error_pairing_required
        AdbFailureKind.CONNECTION -> R.string.transport_error_connection
        AdbFailureKind.DOWNLOAD -> R.string.transport_error_download
        AdbFailureKind.INSTALL -> R.string.transport_error_install
        AdbFailureKind.WRONG_DEVICE -> R.string.transport_error_wrong_device
        AdbFailureKind.CANCELLED -> R.string.transport_cancelled
        AdbFailureKind.UNKNOWN, null -> R.string.transport_operation_failed
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
    selected: Boolean,
    theme: ZStreamTheme,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    val fmt = remember {
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        outlineColor = Color.White,
        outlineWidth = 2.dp,
        gap = 4.dp,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(if (selected) theme.colors.global.accentA.copy(alpha = 0.25f) else Color.Transparent)
                .then(if (selected) Modifier.drawBehind {
                    drawRoundRect(
                        color = theme.colors.global.accentA,
                        size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height * 0.6f),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                        topLeft = Offset(0f, size.height * 0.2f),
                    )
                } else Modifier)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused && !selected) onSelect()
                }
                .clickable(onClick = onSelect)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(apk.releaseTag.ifBlank { apk.releaseName }.ifBlank { apk.apkName }, color = theme.colors.type.emphasis, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                Text(date, color = theme.colors.type.dimmed, fontSize = 11.sp)
            }
            Text(apk.apkName, color = theme.colors.type.secondary, fontSize = 12.sp)
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
    installFocusRequester: FocusRequester,
    releaseFocusRequester: FocusRequester,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    var previousRunning by remember { mutableStateOf(running) }
    LaunchedEffect(running) {
        if (previousRunning != running) {
            previousRunning = running
            withFrameNanos { }
            installFocusRequester.requestFocus()
        }
    }

    ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                apk.releaseTag.ifBlank { apk.releaseName }.ifBlank { apk.apkName },
                color = theme.colors.type.emphasis,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(apk.apkName, color = theme.colors.type.secondary, fontSize = 13.sp)
            Text(stringResource(R.string.tv_update_size_mb, apk.size / 1_048_576.0), color = theme.colors.type.dimmed, fontSize = 12.sp)
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
            InstallProgress.Connecting -> stringResource(R.string.tv_update_connecting_to_tv) to null
            is InstallProgress.Downloading -> stringResource(R.string.tv_update_downloading) to if (p.totalBytes > 0) p.bytes.toFloat() / p.totalBytes else null
            is InstallProgress.Transferring -> stringResource(R.string.tv_update_transferring) to if (p.totalBytes > 0) p.bytes.toFloat() / p.totalBytes else null
            InstallProgress.Installing -> stringResource(R.string.tv_update_installing) to null
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
            text = stringResource(if (running) R.string.tv_update_cancel else R.string.tv_update_install_on_tv),
            onClick = if (running) onCancel else onInstall,
            variant = if (running) ZsButtonVariant.Danger else ZsButtonVariant.Primary,
            modifier = Modifier
                .focusRequester(installFocusRequester)
                .focusProperties {
                    up = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                    left = releaseFocusRequester
                },
        )
    }
    Text(
        stringResource(R.string.tv_update_allow_adb_prompt),
        color = theme.colors.type.success,
        fontSize = 15.sp,
        lineHeight = 18.sp,
    )
}

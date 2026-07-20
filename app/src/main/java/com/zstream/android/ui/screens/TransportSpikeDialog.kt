package com.zstream.android.ui.screens

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zstream.android.data.adb.APK_ROWS_PER_PAGE
import com.zstream.android.data.adb.DEFAULT_RELEASE_REPOSITORY
import com.zstream.android.data.adb.AdbFailureKind
import com.zstream.android.data.adb.AdbOperationException
import com.zstream.android.data.adb.GithubApkAsset
import com.zstream.android.data.adb.GithubReleaseCatalog
import com.zstream.android.data.adb.InstallProgress
import com.zstream.android.data.adb.ReleaseUpdateManager
import com.zstream.android.data.adb.SavedTv
import com.zstream.android.data.adb.TvAdbManager
import com.zstream.android.data.adb.releasePage
import com.zstream.android.R
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.theme.ZStreamTheme
import com.zstream.android.ui.components.themed.ZsButton
import com.zstream.android.ui.components.themed.ZsButtonVariant
import com.zstream.android.ui.components.themed.ZsCard
import com.zstream.android.ui.components.themed.ZsCardVariant
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant
import com.zstream.android.ui.components.themed.ZsTextButton
import com.zstream.android.ui.components.themed.ZsTextField
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
import kotlin.math.ceil

internal enum class TvSetupMode {
    AUTOMATIC,
    MANUAL_WIRELESS,
    LEGACY;

    fun fallback(): TvSetupMode = when (this) {
        AUTOMATIC -> MANUAL_WIRELESS
        MANUAL_WIRELESS, LEGACY -> LEGACY
    }
}

private enum class TvInstallerPage { INTRO, CONNECTION, DEVICES, RELEASES, RELEASE_DETAIL }

private fun TvInstallerPage.progressIndex(): Int = when (this) {
    TvInstallerPage.INTRO, TvInstallerPage.CONNECTION, TvInstallerPage.DEVICES -> 0
    TvInstallerPage.RELEASES -> 1
    TvInstallerPage.RELEASE_DETAIL -> 2
}

@Composable
fun TvInstallerScreen(onDismiss: () -> Unit) {
    val tag = "TvInstaller"
    val theme = LocalZStreamTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { TvAdbManager.get(context) }
    val releaseCatalog = remember { GithubReleaseCatalog() }
    val releaseUpdateManager = remember(context) { ReleaseUpdateManager(context.applicationContext) }

    var devices by remember { mutableStateOf(manager.getSavedTvs()) }
    var selectedDevice by remember { mutableStateOf(manager.getSavedTv()) }
    var page by remember { mutableStateOf(if (devices.isEmpty()) TvInstallerPage.INTRO else TvInstallerPage.DEVICES) }
    var setupMode by remember { mutableStateOf(TvSetupMode.AUTOMATIC) }
    var pairingCode by remember { mutableStateOf("") }
    var manualHost by remember { mutableStateOf("") }
    var manualPairingPort by remember { mutableStateOf("") }
    var manualConnectPort by remember { mutableStateOf("") }
    var legacyHost by remember { mutableStateOf("") }
    var legacyPort by remember { mutableStateOf("5555") }
    var repositoryUrl by remember { mutableStateOf(releaseUpdateManager.repositoryUrl.ifBlank { DEFAULT_RELEASE_REPOSITORY }) }
    var apks by remember { mutableStateOf<List<GithubApkAsset>>(emptyList()) }
    var apkPage by remember { mutableStateOf(0) }
    var selectedApk by remember { mutableStateOf<GithubApkAsset?>(null) }
    var status by remember {
        mutableStateOf(context.getString(if (devices.isEmpty()) R.string.transport_no_tv_paired else R.string.transport_choose_tv))
    }
    var progress by remember { mutableStateOf<InstallProgress?>(null) }
    var lastError by remember { mutableStateOf<Throwable?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    val running = activeJob != null

    fun refreshDevices() {
        devices = manager.getSavedTvs()
        selectedDevice = manager.getSavedTv()
    }

    fun fail(t: Throwable) {
        Log.e(tag, "operation failed", t)
        lastError = t
        status = userMessage(context, t)
        progress = null
    }

    fun connectionSucceeded(model: String) {
        refreshDevices()
        pairingCode = ""
        status = context.getString(R.string.transport_connected_to, model)
        page = TvInstallerPage.RELEASES
    }

    fun scanReleases() {
        activeJob = scope.launch {
            lastError = null
            status = context.getString(R.string.transport_scanning_releases)
            try {
                apks = withContext(Dispatchers.IO) { releaseCatalog.loadAllApks(repositoryUrl) }
                releaseUpdateManager.recordScan(apks)
                apkPage = 0
                status = if (apks.isEmpty()) {
                    context.getString(R.string.transport_no_apk_assets)
                } else {
                    context.resources.getQuantityString(R.plurals.transport_apks_found, apks.size, apks.size)
                }
            } catch (_: CancellationException) {
                status = context.getString(R.string.transport_cancelled)
            } catch (t: Throwable) {
                fail(t)
            } finally {
                activeJob = null
            }
        }
    }

    fun install(apk: GithubApkAsset) {
        activeJob = scope.launch {
            val operationJob = coroutineContext.job
            lastError = null
            status = context.getString(R.string.transport_connecting)
            try {
                val result = withContext(Dispatchers.IO) {
                    manager.installFromUrl(
                        apk.downloadUrl,
                        expectedDigest = apk.digest,
                        onProgress = { progress = it },
                        isCancelled = { !operationJob.isActive },
                    )
                }
                status = context.getString(R.string.transport_installed_on, apk.apkName, result.model)
                progress = null
                Log.d(tag, "install succeeded output=${result.packageManagerOutput}")
            } catch (_: CancellationException) {
                status = context.getString(R.string.transport_cancelled)
                progress = null
            } catch (t: Throwable) {
                fail(t)
            } finally {
                activeJob = null
            }
        }
    }

    fun goBack() {
        if (running) return
        page = when (page) {
            TvInstallerPage.RELEASE_DETAIL -> TvInstallerPage.RELEASES
            TvInstallerPage.RELEASES -> TvInstallerPage.DEVICES
            TvInstallerPage.CONNECTION -> if (devices.isEmpty()) TvInstallerPage.INTRO else TvInstallerPage.DEVICES
            TvInstallerPage.DEVICES -> return onDismiss()
            TvInstallerPage.INTRO -> return onDismiss()
        }
    }

    val showBack = page != TvInstallerPage.INTRO && page != TvInstallerPage.DEVICES

    BackHandler { goBack() }
    Surface(color = theme.colors.background.main, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showBack) {
                        ZsTextButton(text = stringResource(R.string.transport_back), enabled = !running, onClick = { goBack() })
                    }
                    Text(pageTitle(page), color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                ZsTextButton(text = stringResource(R.string.transport_close), enabled = !running, onClick = onDismiss)
            }
            HorizontalDivider(color = theme.colors.type.divider.copy(alpha = 0.18f))
            if (page != TvInstallerPage.INTRO) {
                StepProgressBar(current = page, theme = theme)
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (page) {
                    TvInstallerPage.INTRO -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .background(theme.colors.global.accentA.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = theme.colors.global.accentA,
                                    modifier = Modifier.size(44.dp),
                                )
                            }
                            Text(
                                stringResource(R.string.transport_intro_title),
                                color = theme.colors.type.emphasis,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.transport_intro_description),
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                            ZsStatusBanner(
                                message = stringResource(R.string.transport_same_wifi_hint),
                                variant = ZsStatusBannerVariant.Info,
                            )
                            ZsButton(
                                text = stringResource(R.string.transport_get_started),
                                onClick = { page = TvInstallerPage.CONNECTION },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    TvInstallerPage.CONNECTION -> {
                        when (setupMode) {
                            TvSetupMode.AUTOMATIC -> {
                                Text(stringResource(R.string.transport_automatic_pairing), color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                StepCard(theme, stringResource(R.string.transport_auto_step_1_title)) {
                                    Text(
                                        stringResource(R.string.transport_auto_step_1_body),
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                StepCard(theme, stringResource(R.string.transport_auto_step_2_title)) {
                                    Text(
                                        stringResource(R.string.transport_auto_step_2_body),
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                StepCard(theme, stringResource(R.string.transport_auto_step_3_title)) {
                                    Text(
                                        stringResource(R.string.transport_auto_step_3_body),
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                ZsTextField(
                                    value = pairingCode,
                                    onValueChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                                    label = stringResource(R.string.transport_pairing_code_6_digit),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                ZsButton(
                                    text = stringResource(R.string.transport_find_and_pair_tv),
                                    enabled = pairingCode.length == 6,
                                    loading = running,
                                    onClick = {
                                        activeJob = scope.launch {
                                            lastError = null
                                            status = context.getString(R.string.transport_finding_and_pairing)
                                            try {
                                                val model = withContext(Dispatchers.IO) { manager.discoverPairAndConnect(pairingCode) }
                                                connectionSucceeded(model)
                                            } catch (_: CancellationException) {
                                                status = context.getString(R.string.transport_cancelled)
                                            } catch (t: Throwable) {
                                                fail(t)
                                                setupMode = setupMode.fallback()
                                                status = context.getString(R.string.transport_status_with_manual_hint, status)
                                            } finally {
                                                activeJob = null
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                ZsTextButton(text = stringResource(R.string.transport_enter_details_manually), enabled = !running, onClick = { setupMode = TvSetupMode.MANUAL_WIRELESS })
                            }

                            TvSetupMode.MANUAL_WIRELESS -> {
                                Text(stringResource(R.string.transport_manual_pairing), color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                StepCard(theme, stringResource(R.string.transport_manual_step_1_title)) {
                                    Text(
                                        stringResource(R.string.transport_manual_step_1_body),
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                StepCard(theme, stringResource(R.string.transport_manual_step_2_title)) {
                                    Text(
                                        stringResource(R.string.transport_manual_step_2_body),
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                StepCard(theme, stringResource(R.string.transport_manual_step_3_title)) {
                                    Text(
                                        stringResource(R.string.transport_manual_step_3_body),
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                ZsTextField(manualHost, { manualHost = it.trim() }, label = stringResource(R.string.transport_tv_ip_address), singleLine = true, modifier = Modifier.fillMaxWidth())
                                ZsTextField(manualPairingPort, { manualPairingPort = it.filter(Char::isDigit).take(5) }, label = stringResource(R.string.transport_pairing_port), singleLine = true, modifier = Modifier.fillMaxWidth())
                                ZsTextField(manualConnectPort, { manualConnectPort = it.filter(Char::isDigit).take(5) }, label = stringResource(R.string.transport_connect_port), singleLine = true, modifier = Modifier.fillMaxWidth())
                                ZsTextField(pairingCode, { pairingCode = it.filter(Char::isDigit).take(6) }, label = stringResource(R.string.transport_pairing_code_6_digit), singleLine = true, modifier = Modifier.fillMaxWidth())
                                ZsButton(
                                    text = stringResource(R.string.transport_pair_manual),
                                    enabled = manualHost.isNotBlank() && manualPairingPort.toIntOrNull() in 1..65535 && manualConnectPort.toIntOrNull() in 1..65535 && pairingCode.length == 6,
                                    loading = running,
                                    onClick = {
                                        activeJob = scope.launch {
                                            lastError = null
                                            status = context.getString(R.string.transport_pairing_manual)
                                            try {
                                                val model = withContext(Dispatchers.IO) {
                                                    manager.pairAndConnect(manualHost, requireNotNull(manualPairingPort.toIntOrNull()), requireNotNull(manualConnectPort.toIntOrNull()), pairingCode)
                                                }
                                                connectionSucceeded(model)
                                            } catch (_: CancellationException) {
                                                status = context.getString(R.string.transport_cancelled)
                                            } catch (t: Throwable) {
                                                fail(t)
                                                if (legacyHost.isBlank()) legacyHost = manualHost
                                                setupMode = setupMode.fallback()
                                                status = context.getString(R.string.transport_status_with_legacy_hint, status)
                                            } finally {
                                                activeJob = null
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ZsTextButton(text = stringResource(R.string.transport_try_automatic), enabled = !running, onClick = { setupMode = TvSetupMode.AUTOMATIC })
                                    ZsTextButton(text = stringResource(R.string.transport_use_legacy_adb), enabled = !running, onClick = {
                                        if (legacyHost.isBlank()) legacyHost = manualHost
                                        setupMode = TvSetupMode.LEGACY
                                    })
                                }
                            }

                            TvSetupMode.LEGACY -> {
                                Text(stringResource(R.string.transport_legacy_title), color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                ZsStatusBanner(message = stringResource(R.string.transport_legacy_hint), variant = ZsStatusBannerVariant.Info)
                                StepCard(theme, stringResource(R.string.transport_legacy_step_1_title)) {
                                    Text(
                                        stringResource(R.string.transport_legacy_step_1_body),
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                StepCard(theme, stringResource(R.string.transport_legacy_step_2_title)) {
                                    Text(
                                        stringResource(R.string.transport_legacy_step_2_body),
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                StepCard(theme, stringResource(R.string.transport_legacy_step_3_title)) {
                                    Text(
                                        stringResource(R.string.transport_legacy_step_3_body),
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                ZsTextField(legacyHost, { legacyHost = it.trim() }, label = stringResource(R.string.transport_tv_ip_address), singleLine = true, modifier = Modifier.fillMaxWidth())
                                ZsTextField(legacyPort, { legacyPort = it.filter(Char::isDigit).take(5) }, label = stringResource(R.string.transport_adb_port), singleLine = true, modifier = Modifier.fillMaxWidth())
                                ZsButton(
                                    text = stringResource(R.string.transport_connect_legacy),
                                    enabled = legacyHost.isNotBlank() && legacyPort.toIntOrNull() in 1..65535,
                                    loading = running,
                                    onClick = {
                                        activeJob = scope.launch {
                                            lastError = null
                                            status = context.getString(R.string.transport_connecting_accept_prompt)
                                            try {
                                                val model = withContext(Dispatchers.IO) { manager.connectLegacy(legacyHost, requireNotNull(legacyPort.toIntOrNull())) }
                                                connectionSucceeded(model)
                                            } catch (_: CancellationException) {
                                                status = context.getString(R.string.transport_cancelled)
                                            } catch (t: Throwable) {
                                                fail(t)
                                            } finally {
                                                activeJob = null
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                ZsTextButton(text = stringResource(R.string.transport_back_to_wireless), enabled = !running, onClick = { setupMode = TvSetupMode.MANUAL_WIRELESS })
                            }
                        }
                        if (status.isNotBlank()) {
                            Text(status, color = theme.colors.type.secondary, fontSize = 13.sp)
                        }
                        if (running) {
                            ZsTextButton(text = stringResource(R.string.transport_cancel), onClick = { activeJob?.cancel() })
                        }
                    }

                    TvInstallerPage.DEVICES -> {
                        Text(stringResource(R.string.transport_paired_tvs), color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(stringResource(R.string.transport_paired_tvs_description), color = theme.colors.type.secondary, fontSize = 13.sp)
                        devices.forEach { device ->
                            key(device.id, device.nickname) {
                                var nickname by remember { mutableStateOf(device.nickname) }
                                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(device.nickname, color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                                        Text(stringResource(R.string.transport_model_value, device.model), color = theme.colors.type.secondary, fontSize = 13.sp)
                                        Text(stringResource(R.string.transport_ip_value, device.host), color = theme.colors.type.secondary, fontSize = 13.sp)
                                        Text(device.portSummary(), color = theme.colors.type.secondary, fontSize = 13.sp)
                                        ZsTextField(
                                            value = nickname,
                                            onValueChange = { nickname = it },
                                            label = stringResource(R.string.transport_nickname),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ZsButton(
                                                text = stringResource(if (selectedDevice?.id == device.id) R.string.transport_selected else R.string.transport_select),
                                                enabled = !running,
                                                variant = if (selectedDevice?.id == device.id) ZsButtonVariant.Primary else ZsButtonVariant.Secondary,
                                                onClick = {
                                                    selectedDevice = manager.selectSavedTv(device.id)
                                                    status = context.getString(R.string.transport_selected_tv, device.nickname)
                                                    page = TvInstallerPage.RELEASES
                                                },
                                            )
                                            ZsTextButton(text = stringResource(R.string.transport_save_name), enabled = !running && nickname.isNotBlank(), onClick = {
                                                manager.renameSavedTv(device.id, nickname)
                                                refreshDevices()
                                            })
                                            ZsTextButton(text = stringResource(R.string.transport_forget), enabled = !running, onClick = {
                                                manager.forgetSavedTv(device.id)
                                                refreshDevices()
                                                if (devices.isEmpty()) page = TvInstallerPage.CONNECTION
                                            })
                                        }
                                    }
                                }
                            }
                        }
                        ZsButton(
                            text = stringResource(R.string.transport_add_tv),
                            enabled = !running,
                            variant = ZsButtonVariant.Secondary,
                            onClick = {
                                setupMode = TvSetupMode.AUTOMATIC
                                status = context.getString(R.string.transport_no_tv_paired)
                                page = TvInstallerPage.CONNECTION
                            },
                        )
                    }

                    TvInstallerPage.RELEASES -> {
                        selectedDevice?.let { Text(stringResource(R.string.transport_target_tv, it.nickname, it.host), color = theme.colors.type.text, fontSize = 13.sp) }
                        ZsTextButton(text = stringResource(R.string.transport_change_tv), enabled = !running, onClick = { page = TvInstallerPage.DEVICES })
                        Text(stringResource(R.string.transport_github_releases), color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(stringResource(R.string.transport_github_releases_description), color = theme.colors.type.secondary, fontSize = 13.sp)
                        ZsTextField(
                            value = repositoryUrl,
                            onValueChange = {
                                repositoryUrl = it
                                releaseUpdateManager.setRepositoryUrl(it)
                            },
                            label = stringResource(R.string.transport_github_repository_link),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ZsButton(
                            text = stringResource(R.string.transport_scan_releases),
                            enabled = repositoryUrl.isNotBlank(),
                            loading = running,
                            onClick = { scanReleases() },
                        )
                        if (status.isNotBlank()) {
                            Text(status, color = theme.colors.type.secondary, fontSize = 13.sp)
                        }
                        if (running) {
                            ZsTextButton(text = stringResource(R.string.transport_cancel), onClick = { activeJob?.cancel() })
                        }

                        releasePage(apks, apkPage).forEach { apk ->
                            ZsCard(
                                variant = ZsCardVariant.Elevated,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedApk = apk
                                    progress = null
                                    status = context.getString(R.string.transport_ready_to_install)
                                    page = TvInstallerPage.RELEASE_DETAIL
                                },
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(apk.apkName, color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.transport_version_tag, apk.version, apk.releaseTag), color = theme.colors.type.text, fontSize = 13.sp)
                                    Text(stringResource(R.string.transport_size_uploaded, formatBytes(apk.size), formatGithubDate(apk.assetCreatedAt)), color = theme.colors.type.secondary, fontSize = 12.sp)
                                    Text(stringResource(R.string.transport_release_value, apk.releaseName), color = theme.colors.type.secondary, fontSize = 12.sp)
                                }
                            }
                        }

                        if (apks.isNotEmpty()) {
                            val pageCount = ceil(apks.size / APK_ROWS_PER_PAGE.toDouble()).toInt()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                ZsTextButton(text = stringResource(R.string.transport_previous), enabled = apkPage > 0, onClick = { apkPage-- })
                                Text(stringResource(R.string.transport_page_of, apkPage + 1, pageCount), color = theme.colors.type.text, fontSize = 13.sp)
                                ZsTextButton(text = stringResource(R.string.transport_next), enabled = apkPage + 1 < pageCount, onClick = { apkPage++ })
                            }
                        }
                    }

                    TvInstallerPage.RELEASE_DETAIL -> {
                        selectedApk?.let { apk ->
                            Text(apk.apkName, color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(stringResource(R.string.transport_version_tag, apk.version, apk.releaseTag), color = theme.colors.type.text, fontSize = 14.sp)
                                    Text(stringResource(R.string.transport_size_uploaded, formatBytes(apk.size), formatGithubDate(apk.assetCreatedAt)), color = theme.colors.type.secondary, fontSize = 13.sp)
                                    if (apk.releaseDescription.isNotBlank()) {
                                        Text(apk.releaseDescription, color = theme.colors.type.text, fontSize = 13.sp, lineHeight = 19.sp)
                                    }
                                }
                            }

                            var showAdvanced by remember { mutableStateOf(false) }
                            ZsTextButton(
                                text = stringResource(if (showAdvanced) R.string.transport_hide_advanced else R.string.transport_show_advanced),
                                onClick = { showAdvanced = !showAdvanced },
                            )
                            if (showAdvanced) {
                                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(stringResource(R.string.transport_apk_asset), color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                                        MetadataLine(stringResource(R.string.transport_label), apk.label ?: stringResource(R.string.transport_none))
                                        MetadataLine(stringResource(R.string.transport_content_type), apk.contentType)
                                        MetadataLine(stringResource(R.string.transport_state), apk.state)
                                        MetadataLine(stringResource(R.string.transport_digest), apk.digest ?: stringResource(R.string.transport_not_provided))
                                        MetadataLine(stringResource(R.string.transport_downloads), apk.downloadCount.toString())
                                        MetadataLine(stringResource(R.string.transport_uploaded_by), apk.assetUploader)
                                        MetadataLine(stringResource(R.string.transport_updated), formatGithubDate(apk.assetUpdatedAt))
                                        MetadataLine(stringResource(R.string.transport_asset_id), apk.assetId.toString())
                                        MetadataLine(stringResource(R.string.transport_download_url), apk.downloadUrl)
                                    }
                                }
                                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(stringResource(R.string.transport_full_release), color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                                        MetadataLine(stringResource(R.string.transport_release), apk.releaseName)
                                        MetadataLine(stringResource(R.string.transport_tag), apk.releaseTag)
                                        MetadataLine(stringResource(R.string.transport_target_commit), apk.targetCommit)
                                        MetadataLine(stringResource(R.string.transport_released_by), apk.releaseAuthor)
                                        MetadataLine(stringResource(R.string.transport_created), formatGithubDate(apk.releaseCreatedAt))
                                        MetadataLine(stringResource(R.string.transport_published), apk.releasePublishedAt?.let { formatGithubDate(it) } ?: stringResource(R.string.transport_not_published))
                                        MetadataLine(stringResource(R.string.transport_release_id), apk.releaseId.toString())
                                        MetadataLine(stringResource(R.string.transport_draft), stringResource(if (apk.draft) R.string.transport_yes else R.string.transport_no))
                                        MetadataLine(stringResource(R.string.transport_prerelease), stringResource(if (apk.prerelease) R.string.transport_yes else R.string.transport_no))
                                        MetadataLine(stringResource(R.string.transport_release_url), apk.releaseUrl)
                                    }
                                }
                            }

                            progress?.let { current ->
                                val fraction = when (current) {
                                    is InstallProgress.Downloading -> current.bytes.fractionOf(current.totalBytes)
                                    is InstallProgress.Transferring -> current.bytes.fractionOf(current.totalBytes)
                                    else -> null
                                }
                                Text(progressLabel(current), color = theme.colors.type.secondary, fontSize = 13.sp)
                                if (fraction == null) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = theme.colors.global.accentA,
                                        trackColor = theme.colors.background.secondary,
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = theme.colors.global.accentA,
                                        trackColor = theme.colors.background.secondary,
                                    )
                                }
                            }
                            if (status.isNotBlank()) {
                                Text(status, color = theme.colors.type.secondary, fontSize = 13.sp)
                            }
                            if (lastError != null) {
                                ZsStatusBanner(message = userMessage(context, lastError!!), variant = ZsStatusBannerVariant.Error)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ZsButton(
                                    text = stringResource(R.string.transport_install_apk),
                                    enabled = !running && selectedDevice != null,
                                    loading = running,
                                    onClick = { install(apk) },
                                )
                                if (running) {
                                    ZsTextButton(text = stringResource(R.string.transport_cancel), onClick = { activeJob?.cancel() })
                                }
                                if (!running && lastError != null) {
                                    ZsTextButton(text = stringResource(R.string.transport_retry), onClick = { install(apk) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepProgressBar(current: TvInstallerPage, theme: ZStreamTheme) {
    val activeIndex = current.progressIndex()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index <= activeIndex) theme.colors.onboarding.barFilled else theme.colors.onboarding.bar),
            )
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
private fun MetadataLine(label: String, value: String) {
    val theme = LocalZStreamTheme.current
    Text("$label: $value", color = theme.colors.type.text, fontSize = 12.sp)
}

@Composable
private fun pageTitle(page: TvInstallerPage): String = stringResource(when (page) {
    TvInstallerPage.INTRO -> R.string.transport_connect_tv
    TvInstallerPage.CONNECTION -> R.string.transport_connect_tv
    TvInstallerPage.DEVICES -> R.string.transport_paired_tvs
    TvInstallerPage.RELEASES -> R.string.transport_choose_apk
    TvInstallerPage.RELEASE_DETAIL -> R.string.transport_release_details
})

@Composable
private fun SavedTv.portSummary(): String = if (legacyPort != null) {
    stringResource(R.string.transport_legacy_port_value, legacyPort)
} else {
    stringResource(
        R.string.transport_pairing_connect_ports,
        pairingPort?.toString() ?: stringResource(R.string.transport_unknown),
        connectPort?.toString() ?: stringResource(R.string.transport_rediscovered_when_connecting),
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format("%.2f GiB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format("%.2f MiB", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format("%.2f KiB", bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun formatGithubDate(value: String): String = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        .format(requireNotNull(parser.parse(value)))
}.getOrDefault(value.ifBlank { stringResource(R.string.transport_unknown) })

private fun Long.fractionOf(total: Long): Float =
    if (total <= 0) 0f else (toDouble() / total).toFloat().coerceIn(0f, 1f)

@Composable
private fun progressLabel(progress: InstallProgress): String = when (progress) {
    InstallProgress.Connecting -> stringResource(R.string.transport_connecting)
    is InstallProgress.Downloading -> stringResource(R.string.transport_downloading_mib, progress.bytes / 1_048_576, progress.totalBytes / 1_048_576)
    is InstallProgress.Transferring -> stringResource(R.string.transport_sending_mib, progress.bytes / 1_048_576, progress.totalBytes / 1_048_576)
    InstallProgress.Installing -> stringResource(R.string.transport_installing)
}

private fun userMessage(context: Context, t: Throwable): String = when (t) {
    is AdbOperationException -> when (t.kind) {
        AdbFailureKind.DISCOVERY -> context.getString(R.string.transport_error_discovery)
        AdbFailureKind.PAIRING -> context.getString(R.string.transport_error_pairing)
        AdbFailureKind.PAIRING_REQUIRED -> context.getString(R.string.transport_error_pairing_required)
        AdbFailureKind.CONNECTION -> context.getString(R.string.transport_error_connection)
        AdbFailureKind.DOWNLOAD -> context.getString(R.string.transport_error_download)
        AdbFailureKind.INSTALL -> context.getString(R.string.transport_error_install)
        AdbFailureKind.WRONG_DEVICE -> context.getString(R.string.transport_error_wrong_device)
        AdbFailureKind.CANCELLED -> context.getString(R.string.transport_cancelled)
        AdbFailureKind.UNKNOWN -> context.getString(R.string.transport_operation_failed)
    }
    else -> context.getString(R.string.transport_operation_failed)
}

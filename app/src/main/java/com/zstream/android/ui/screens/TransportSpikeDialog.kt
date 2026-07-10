package com.zstream.android.ui.screens

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
    var status by remember { mutableStateOf(if (devices.isEmpty()) "No TV paired" else "Choose a TV") }
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
        status = userMessage(t)
        progress = null
    }

    fun connectionSucceeded(model: String) {
        refreshDevices()
        pairingCode = ""
        status = "Connected to $model"
        page = TvInstallerPage.RELEASES
    }

    fun scanReleases() {
        activeJob = scope.launch {
            lastError = null
            status = "Scanning all GitHub releases…"
            try {
                apks = withContext(Dispatchers.IO) { releaseCatalog.loadAllApks(repositoryUrl) }
                releaseUpdateManager.recordScan(apks)
                apkPage = 0
                status = if (apks.isEmpty()) "No APK assets found" else "Found ${apks.size} APK${if (apks.size == 1) "" else "s"}"
            } catch (_: CancellationException) {
                status = "Cancelled"
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
            status = "Connecting…"
            try {
                val result = withContext(Dispatchers.IO) {
                    manager.installFromUrl(
                        apk.downloadUrl,
                        expectedDigest = apk.digest,
                        onProgress = { progress = it },
                        isCancelled = { !operationJob.isActive },
                    )
                }
                status = "Installed ${apk.apkName} on ${result.model}"
                progress = null
                Log.d(tag, "install succeeded output=${result.packageManagerOutput}")
            } catch (_: CancellationException) {
                status = "Cancelled"
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
                        ZsTextButton(text = "Back", enabled = !running, onClick = { goBack() })
                    }
                    Text(pageTitle(page), color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                ZsTextButton(text = "Close", enabled = !running, onClick = onDismiss)
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
                                "Let's get your TV set up",
                                color = theme.colors.type.emphasis,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "We'll walk you through pairing and installing the app on your TV — it only takes a minute.",
                                color = theme.colors.type.secondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            )
                            ZsStatusBanner(
                                message = "Keep the phone and TV on the same Wi-Fi (skip guest networks and VPNs), and leave ZStream open on the TV while you pair.",
                                variant = ZsStatusBannerVariant.Info,
                            )
                            ZsButton(
                                text = "Get started",
                                onClick = { page = TvInstallerPage.CONNECTION },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    TvInstallerPage.CONNECTION -> {
                        when (setupMode) {
                            TvSetupMode.AUTOMATIC -> {
                                Text("Automatic wireless pairing", color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                StepCard(theme, "Step 1 — Turn on Developer options") {
                                    Text(
                                        "On the TV: Settings → Device Preferences → About → Android TV OS build. Tap it repeatedly until Developer options unlock.",
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                StepCard(theme, "Step 2 — Enable Wireless debugging") {
                                    Text(
                                        "Back in Device Preferences, open Developer options, then turn on USB debugging and Wireless debugging.",
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                StepCard(theme, "Step 3 — Enter the pairing code") {
                                    Text(
                                        "Tap \"Pair device with pairing code\" and leave that screen open. Enter the 6-digit code below — ZStream finds the TV automatically.",
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                ZsTextField(
                                    value = pairingCode,
                                    onValueChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                                    label = "6-digit pairing code",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                ZsButton(
                                    text = "Find and pair TV",
                                    enabled = pairingCode.length == 6,
                                    loading = running,
                                    onClick = {
                                        activeJob = scope.launch {
                                            lastError = null
                                            status = "Finding and pairing with TV…"
                                            try {
                                                val model = withContext(Dispatchers.IO) { manager.discoverPairAndConnect(pairingCode) }
                                                connectionSucceeded(model)
                                            } catch (_: CancellationException) {
                                                status = "Cancelled"
                                            } catch (t: Throwable) {
                                                fail(t)
                                                setupMode = setupMode.fallback()
                                                status += " Enter the wireless debugging details manually."
                                            } finally {
                                                activeJob = null
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                ZsTextButton(text = "Enter details manually", enabled = !running, onClick = { setupMode = TvSetupMode.MANUAL_WIRELESS })
                            }

                            TvSetupMode.MANUAL_WIRELESS -> {
                                Text("Manual wireless pairing", color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                StepCard(theme, "Step 1 — Keep the TV's pairing screen open") {
                                    Text(
                                        "On the TV, keep Wireless debugging and \"Pair device with pairing code\" open.",
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                StepCard(theme, "Step 2 — Copy the address and ports") {
                                    Text(
                                        "Enter the TV IP and pairing port shown in the pairing window, plus the connect port shown on the main Wireless debugging screen. These two ports are usually different.",
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                StepCard(theme, "Step 3 — Enter the pairing code") {
                                    Text(
                                        "Enter the current 6-digit code, then pair. If this fails, ZStream opens legacy ADB setup.",
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                ZsTextField(manualHost, { manualHost = it.trim() }, label = "TV IP address", singleLine = true, modifier = Modifier.fillMaxWidth())
                                ZsTextField(manualPairingPort, { manualPairingPort = it.filter(Char::isDigit).take(5) }, label = "Pairing port", singleLine = true, modifier = Modifier.fillMaxWidth())
                                ZsTextField(manualConnectPort, { manualConnectPort = it.filter(Char::isDigit).take(5) }, label = "Connect port", singleLine = true, modifier = Modifier.fillMaxWidth())
                                ZsTextField(pairingCode, { pairingCode = it.filter(Char::isDigit).take(6) }, label = "6-digit pairing code", singleLine = true, modifier = Modifier.fillMaxWidth())
                                ZsButton(
                                    text = "Pair with manual details",
                                    enabled = manualHost.isNotBlank() && manualPairingPort.toIntOrNull() in 1..65535 && manualConnectPort.toIntOrNull() in 1..65535 && pairingCode.length == 6,
                                    loading = running,
                                    onClick = {
                                        activeJob = scope.launch {
                                            lastError = null
                                            status = "Pairing with manual details…"
                                            try {
                                                val model = withContext(Dispatchers.IO) {
                                                    manager.pairAndConnect(manualHost, requireNotNull(manualPairingPort.toIntOrNull()), requireNotNull(manualConnectPort.toIntOrNull()), pairingCode)
                                                }
                                                connectionSucceeded(model)
                                            } catch (_: CancellationException) {
                                                status = "Cancelled"
                                            } catch (t: Throwable) {
                                                fail(t)
                                                if (legacyHost.isBlank()) legacyHost = manualHost
                                                setupMode = setupMode.fallback()
                                                status += " Try legacy ADB."
                                            } finally {
                                                activeJob = null
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ZsTextButton(text = "Try automatic", enabled = !running, onClick = { setupMode = TvSetupMode.AUTOMATIC })
                                    ZsTextButton(text = "Use legacy ADB", enabled = !running, onClick = {
                                        if (legacyHost.isBlank()) legacyHost = manualHost
                                        setupMode = TvSetupMode.LEGACY
                                    })
                                }
                            }

                            TvSetupMode.LEGACY -> {
                                Text("Legacy ADB over network", color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                ZsStatusBanner(message = "Use this for older TVs without Wireless debugging.", variant = ZsStatusBannerVariant.Info)
                                StepCard(theme, "Step 1 — Enable debugging") {
                                    Text(
                                        "On the TV, enable Developer options and USB, network, or ADB debugging.",
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                StepCard(theme, "Step 2 — Find the TV's IP address") {
                                    Text(
                                        "Look in the TV's Network settings. The ADB port is usually 5555.",
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                StepCard(theme, "Step 3 — Connect and accept the prompt") {
                                    Text(
                                        "Enter both values and connect, then accept the debugging prompt on the TV (choose Always allow if available).",
                                        color = theme.colors.type.secondary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    )
                                }
                                ZsTextField(legacyHost, { legacyHost = it.trim() }, label = "TV IP address", singleLine = true, modifier = Modifier.fillMaxWidth())
                                ZsTextField(legacyPort, { legacyPort = it.filter(Char::isDigit).take(5) }, label = "ADB port", singleLine = true, modifier = Modifier.fillMaxWidth())
                                ZsButton(
                                    text = "Connect with legacy ADB",
                                    enabled = legacyHost.isNotBlank() && legacyPort.toIntOrNull() in 1..65535,
                                    loading = running,
                                    onClick = {
                                        activeJob = scope.launch {
                                            lastError = null
                                            status = "Connecting… Accept the debugging prompt on the TV."
                                            try {
                                                val model = withContext(Dispatchers.IO) { manager.connectLegacy(legacyHost, requireNotNull(legacyPort.toIntOrNull())) }
                                                connectionSucceeded(model)
                                            } catch (_: CancellationException) {
                                                status = "Cancelled"
                                            } catch (t: Throwable) {
                                                fail(t)
                                            } finally {
                                                activeJob = null
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                ZsTextButton(text = "Back to wireless debugging", enabled = !running, onClick = { setupMode = TvSetupMode.MANUAL_WIRELESS })
                            }
                        }
                        if (status.isNotBlank()) {
                            Text(status, color = theme.colors.type.secondary, fontSize = 13.sp)
                        }
                        if (running) {
                            ZsTextButton(text = "Cancel", onClick = { activeJob?.cancel() })
                        }
                    }

                    TvInstallerPage.DEVICES -> {
                        Text("Paired TVs", color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Choose where APKs will be installed, rename a TV, or add another one.", color = theme.colors.type.secondary, fontSize = 13.sp)
                        devices.forEach { device ->
                            key(device.id, device.nickname) {
                                var nickname by remember { mutableStateOf(device.nickname) }
                                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(device.nickname, color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                                        Text("Model: ${device.model}", color = theme.colors.type.secondary, fontSize = 13.sp)
                                        Text("IP: ${device.host}", color = theme.colors.type.secondary, fontSize = 13.sp)
                                        Text(device.portSummary(), color = theme.colors.type.secondary, fontSize = 13.sp)
                                        ZsTextField(
                                            value = nickname,
                                            onValueChange = { nickname = it },
                                            label = "Nickname",
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ZsButton(
                                                text = if (selectedDevice?.id == device.id) "Selected" else "Select",
                                                enabled = !running,
                                                variant = if (selectedDevice?.id == device.id) ZsButtonVariant.Primary else ZsButtonVariant.Secondary,
                                                onClick = {
                                                    selectedDevice = manager.selectSavedTv(device.id)
                                                    status = "Selected ${device.nickname}"
                                                    page = TvInstallerPage.RELEASES
                                                },
                                            )
                                            ZsTextButton(text = "Save name", enabled = !running && nickname.isNotBlank(), onClick = {
                                                manager.renameSavedTv(device.id, nickname)
                                                refreshDevices()
                                            })
                                            ZsTextButton(text = "Forget", enabled = !running, onClick = {
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
                            text = "Add TV",
                            enabled = !running,
                            variant = ZsButtonVariant.Secondary,
                            onClick = {
                                setupMode = TvSetupMode.AUTOMATIC
                                status = "No TV paired"
                                page = TvInstallerPage.CONNECTION
                            },
                        )
                    }

                    TvInstallerPage.RELEASES -> {
                        selectedDevice?.let { Text("Target: ${it.nickname} (${it.host})", color = theme.colors.type.text, fontSize = 13.sp) }
                        ZsTextButton(text = "Change TV", enabled = !running, onClick = { page = TvInstallerPage.DEVICES })
                        Text("GitHub releases", color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Enter a public GitHub repository link. ZStream scans every release and lists its APK assets.", color = theme.colors.type.secondary, fontSize = 13.sp)
                        ZsTextField(
                            value = repositoryUrl,
                            onValueChange = {
                                repositoryUrl = it
                                releaseUpdateManager.setRepositoryUrl(it)
                            },
                            label = "GitHub repository link",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ZsButton(
                            text = "Scan releases",
                            enabled = repositoryUrl.isNotBlank(),
                            loading = running,
                            onClick = { scanReleases() },
                        )
                        if (status.isNotBlank()) {
                            Text(status, color = theme.colors.type.secondary, fontSize = 13.sp)
                        }
                        if (running) {
                            ZsTextButton(text = "Cancel", onClick = { activeJob?.cancel() })
                        }

                        releasePage(apks, apkPage).forEach { apk ->
                            ZsCard(
                                variant = ZsCardVariant.Elevated,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedApk = apk
                                    progress = null
                                    status = "Ready to install"
                                    page = TvInstallerPage.RELEASE_DETAIL
                                },
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(apk.apkName, color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                                    Text("Version ${apk.version} • Tag ${apk.releaseTag}", color = theme.colors.type.text, fontSize = 13.sp)
                                    Text("${formatBytes(apk.size)} • Uploaded ${formatGithubDate(apk.assetCreatedAt)}", color = theme.colors.type.secondary, fontSize = 12.sp)
                                    Text("Release: ${apk.releaseName}", color = theme.colors.type.secondary, fontSize = 12.sp)
                                }
                            }
                        }

                        if (apks.isNotEmpty()) {
                            val pageCount = ceil(apks.size / APK_ROWS_PER_PAGE.toDouble()).toInt()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                ZsTextButton(text = "Previous", enabled = apkPage > 0, onClick = { apkPage-- })
                                Text("Page ${apkPage + 1} of $pageCount", color = theme.colors.type.text, fontSize = 13.sp)
                                ZsTextButton(text = "Next", enabled = apkPage + 1 < pageCount, onClick = { apkPage++ })
                            }
                        }
                    }

                    TvInstallerPage.RELEASE_DETAIL -> {
                        selectedApk?.let { apk ->
                            Text(apk.apkName, color = theme.colors.type.emphasis, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Version ${apk.version} • Tag ${apk.releaseTag}", color = theme.colors.type.text, fontSize = 14.sp)
                                    Text("${formatBytes(apk.size)} • Uploaded ${formatGithubDate(apk.assetCreatedAt)}", color = theme.colors.type.secondary, fontSize = 13.sp)
                                    if (apk.releaseDescription.isNotBlank()) {
                                        Text(apk.releaseDescription, color = theme.colors.type.text, fontSize = 13.sp, lineHeight = 19.sp)
                                    }
                                }
                            }

                            var showAdvanced by remember { mutableStateOf(false) }
                            ZsTextButton(
                                text = if (showAdvanced) "Hide advanced details" else "Show advanced details",
                                onClick = { showAdvanced = !showAdvanced },
                            )
                            if (showAdvanced) {
                                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("APK asset", color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                                        MetadataLine("Label", apk.label ?: "None")
                                        MetadataLine("Content type", apk.contentType)
                                        MetadataLine("State", apk.state)
                                        MetadataLine("Digest", apk.digest ?: "Not provided")
                                        MetadataLine("Downloads", apk.downloadCount.toString())
                                        MetadataLine("Uploaded by", apk.assetUploader)
                                        MetadataLine("Updated", formatGithubDate(apk.assetUpdatedAt))
                                        MetadataLine("Asset ID", apk.assetId.toString())
                                        MetadataLine("Download URL", apk.downloadUrl)
                                    }
                                }
                                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Full release", color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                                        MetadataLine("Release", apk.releaseName)
                                        MetadataLine("Tag", apk.releaseTag)
                                        MetadataLine("Target commit", apk.targetCommit)
                                        MetadataLine("Released by", apk.releaseAuthor)
                                        MetadataLine("Created", formatGithubDate(apk.releaseCreatedAt))
                                        MetadataLine("Published", apk.releasePublishedAt?.let(::formatGithubDate) ?: "Not published")
                                        MetadataLine("Release ID", apk.releaseId.toString())
                                        MetadataLine("Draft", if (apk.draft) "Yes" else "No")
                                        MetadataLine("Prerelease", if (apk.prerelease) "Yes" else "No")
                                        MetadataLine("Release URL", apk.releaseUrl)
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
                                ZsStatusBanner(message = userMessage(lastError!!), variant = ZsStatusBannerVariant.Error)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ZsButton(
                                    text = "Install this APK",
                                    enabled = !running && selectedDevice != null,
                                    loading = running,
                                    onClick = { install(apk) },
                                )
                                if (running) {
                                    ZsTextButton(text = "Cancel", onClick = { activeJob?.cancel() })
                                }
                                if (!running && lastError != null) {
                                    ZsTextButton(text = "Retry", onClick = { install(apk) })
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

private fun pageTitle(page: TvInstallerPage): String = when (page) {
    TvInstallerPage.INTRO -> "Connect TV"
    TvInstallerPage.CONNECTION -> "Connect TV"
    TvInstallerPage.DEVICES -> "Paired TVs"
    TvInstallerPage.RELEASES -> "Choose APK"
    TvInstallerPage.RELEASE_DETAIL -> "Release details"
}

private fun SavedTv.portSummary(): String = if (legacyPort != null) {
    "Legacy ADB port: $legacyPort"
} else {
    "Pairing port: ${pairingPort ?: "Unknown"} • Connect port: ${connectPort ?: "Rediscovered when connecting"}"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format("%.2f GiB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format("%.2f MiB", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format("%.2f KiB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatGithubDate(value: String): String = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(requireNotNull(parser.parse(value)))
}.getOrDefault(value.ifBlank { "Unknown" })

private fun Long.fractionOf(total: Long): Float =
    if (total <= 0) 0f else (toDouble() / total).toFloat().coerceIn(0f, 1f)

private fun progressLabel(progress: InstallProgress): String = when (progress) {
    InstallProgress.Connecting -> "Connecting…"
    is InstallProgress.Downloading -> "Downloading ${progress.bytes / 1_048_576} / ${progress.totalBytes / 1_048_576} MiB"
    is InstallProgress.Transferring -> "Sending ${progress.bytes / 1_048_576} / ${progress.totalBytes / 1_048_576} MiB"
    InstallProgress.Installing -> "Installing…"
}

private fun userMessage(t: Throwable): String = when (t) {
    is AdbOperationException -> when (t.kind) {
        AdbFailureKind.DISCOVERY -> "Could not find the TV. Ensure Wireless Debugging is enabled."
        AdbFailureKind.PAIRING -> "Pairing failed. Open a new pairing code and try again."
        AdbFailureKind.PAIRING_REQUIRED -> "TV authorization was revoked. Pair the TV again."
        AdbFailureKind.CONNECTION -> "Could not connect to the TV."
        AdbFailureKind.DOWNLOAD -> "Could not download the APK. Check the connection."
        AdbFailureKind.INSTALL -> t.message ?: "The TV rejected the APK."
        AdbFailureKind.WRONG_DEVICE -> "The discovered device is not the selected TV."
        AdbFailureKind.CANCELLED -> "Cancelled"
        AdbFailureKind.UNKNOWN -> t.message ?: "Operation failed"
    }
    else -> t.message ?: "Operation failed"
}

package com.zstream.android.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.zstream.android.ui.components.themed.ZsCard
import com.zstream.android.ui.components.themed.ZsCardVariant
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

private enum class TvInstallerPage { CONNECTION, DEVICES, RELEASES, RELEASE_DETAIL }

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
    var page by remember { mutableStateOf(if (devices.isEmpty()) TvInstallerPage.CONNECTION else TvInstallerPage.DEVICES) }
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
            TvInstallerPage.CONNECTION -> if (devices.isEmpty()) return onDismiss() else TvInstallerPage.DEVICES
            TvInstallerPage.DEVICES -> return onDismiss()
        }
    }

    BackHandler { goBack() }
    Surface(color = theme.colors.background.main, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row {
                    if (page != TvInstallerPage.DEVICES || devices.isEmpty()) {
                        TextButton(enabled = !running, onClick = { goBack() }) { Text("Back") }
                    }
                    Text(pageTitle(page), color = theme.colors.type.emphasis)
                }
                TextButton(enabled = !running, onClick = onDismiss) { Text("Close") }
            }
            HorizontalDivider(color = theme.colors.type.divider.copy(alpha = 0.18f))
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (page) {
                    TvInstallerPage.CONNECTION -> {
                        Text("Before you start", color = theme.colors.type.emphasis)
                        Text(
                            "• Connect the phone and TV to the same network. The TV may use Wi-Fi or Ethernet.\n" +
                                "• Avoid guest Wi-Fi, or VPNs.\n" +
                                "• Keep this app open while pairing and approve prompts on the TV.",
                            color = theme.colors.type.secondary,
                        )

                        when (setupMode) {
                            TvSetupMode.AUTOMATIC -> {
                                Text("Automatic wireless pairing", color = theme.colors.type.emphasis)
                                Text(
                                    "1. On the TV, enable Developer options by going to your settings → Device Preferences → About → Android TV OS build/build number and click on it until you see that developer options have been enabled.\n" +
                                        "2. Then go back to Device Preferences and open Developer options → Turn on USB Debugging and then go to Wireless debugging and turn it on.\n" +
                                        "3. Select Pair device with pairing code and leave that screen open.\n" +
                                        "4. Enter the 6-digit code below. ZStream discovers the TV address and ports automatically.\n\n" +
                                        "If discovery fails, manual wireless setup opens with fields for the values shown by the TV.",
                                    color = theme.colors.type.secondary,
                                )
                                OutlinedTextField(
                                    value = pairingCode,
                                    onValueChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                                    label = { Text("6-digit pairing code") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Button(
                                    enabled = !running && pairingCode.length == 6,
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
                                ) { Text("Find and pair TV") }
                                TextButton(enabled = !running, onClick = { setupMode = TvSetupMode.MANUAL_WIRELESS }) {
                                    Text("Enter details manually")
                                }
                            }

                            TvSetupMode.MANUAL_WIRELESS -> {
                                Text("Manual wireless pairing", color = theme.colors.type.emphasis)
                                Text(
                                    "1. Keep Wireless debugging and Pair device with pairing code open on the TV.\n" +
                                        "2. Enter the TV IP address and pairing port shown in the pairing window.\n" +
                                        "3. Enter the connect port shown as IP address & port on the main Wireless debugging screen.\n" +
                                        "4. Enter the current 6-digit code, then pair.\n\n" +
                                        "The pairing and connect ports are usually different. If this fails, ZStream opens legacy ADB setup.",
                                    color = theme.colors.type.secondary,
                                )
                                OutlinedTextField(manualHost, { manualHost = it.trim() }, label = { Text("TV IP address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(manualPairingPort, { manualPairingPort = it.filter(Char::isDigit).take(5) }, label = { Text("Pairing port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(manualConnectPort, { manualConnectPort = it.filter(Char::isDigit).take(5) }, label = { Text("Connect port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(pairingCode, { pairingCode = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit pairing code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Button(
                                    enabled = !running && manualHost.isNotBlank() && manualPairingPort.toIntOrNull() in 1..65535 && manualConnectPort.toIntOrNull() in 1..65535 && pairingCode.length == 6,
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
                                ) { Text("Pair with manual details") }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(enabled = !running, onClick = { setupMode = TvSetupMode.AUTOMATIC }) { Text("Try automatic") }
                                    TextButton(enabled = !running, onClick = {
                                        if (legacyHost.isBlank()) legacyHost = manualHost
                                        setupMode = TvSetupMode.LEGACY
                                    }) { Text("Use legacy ADB") }
                                }
                            }

                            TvSetupMode.LEGACY -> {
                                Text("Legacy ADB over network", color = theme.colors.type.emphasis)
                                Text(
                                    "Use this for older TVs without Wireless debugging.\n\n" +
                                        "1. On the TV, enable Developer options and USB, network, or ADB debugging.\n" +
                                        "2. Find the TV IP address in its Network settings. The ADB port is usually 5555.\n" +
                                        "3. Enter both values and connect. Accept the debugging prompt on the TV; choose Always allow if available.\n\n" +
                                        "Emulator test: use 10.0.2.2 and the TV emulator port (for example, emulator-5556 uses 5557).\n\n" +
                                        "If connection fails, verify the TV IP did not change, debugging is still enabled, and the router allows devices to communicate.",
                                    color = theme.colors.type.secondary,
                                )
                                OutlinedTextField(legacyHost, { legacyHost = it.trim() }, label = { Text("TV IP address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(legacyPort, { legacyPort = it.filter(Char::isDigit).take(5) }, label = { Text("ADB port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Button(
                                    enabled = !running && legacyHost.isNotBlank() && legacyPort.toIntOrNull() in 1..65535,
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
                                ) { Text("Connect with legacy ADB") }
                                TextButton(enabled = !running, onClick = { setupMode = TvSetupMode.MANUAL_WIRELESS }) { Text("Back to wireless debugging") }
                            }
                        }
                        Text("Status", color = theme.colors.type.emphasis)
                        Text(status, color = theme.colors.type.text)
                        if (running) TextButton(onClick = { activeJob?.cancel() }) { Text("Cancel") }
                    }

                    TvInstallerPage.DEVICES -> {
                        Text("Paired TVs", color = theme.colors.type.emphasis)
                        Text("Choose where APKs will be installed, rename a TV, or add another one.", color = theme.colors.type.secondary)
                        devices.forEach { device ->
                            key(device.id, device.nickname) {
                                var nickname by remember { mutableStateOf(device.nickname) }
                                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(device.nickname, color = theme.colors.type.emphasis)
                                        Text("Model: ${device.model}", color = theme.colors.type.secondary)
                                        Text("IP: ${device.host}", color = theme.colors.type.secondary)
                                        Text(device.portSummary(), color = theme.colors.type.secondary)
                                        OutlinedTextField(
                                            value = nickname,
                                            onValueChange = { nickname = it },
                                            label = { Text("Nickname") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(enabled = !running, onClick = {
                                                selectedDevice = manager.selectSavedTv(device.id)
                                                status = "Selected ${device.nickname}"
                                                page = TvInstallerPage.RELEASES
                                            }) { Text(if (selectedDevice?.id == device.id) "Selected" else "Select") }
                                            TextButton(enabled = !running && nickname.isNotBlank(), onClick = {
                                                manager.renameSavedTv(device.id, nickname)
                                                refreshDevices()
                                            }) { Text("Save name") }
                                            TextButton(enabled = !running, onClick = {
                                                manager.forgetSavedTv(device.id)
                                                refreshDevices()
                                                if (devices.isEmpty()) page = TvInstallerPage.CONNECTION
                                            }) { Text("Forget") }
                                        }
                                    }
                                }
                            }
                        }
                        Button(enabled = !running, onClick = {
                            setupMode = TvSetupMode.AUTOMATIC
                            status = "No TV paired"
                            page = TvInstallerPage.CONNECTION
                        }) { Text("Add TV") }
                    }

                    TvInstallerPage.RELEASES -> {
                        selectedDevice?.let { Text("Target: ${it.nickname} (${it.host})", color = theme.colors.type.text) }
                        TextButton(enabled = !running, onClick = { page = TvInstallerPage.DEVICES }) { Text("Change TV") }
                        Text("GitHub releases", color = theme.colors.type.emphasis)
                        Text("Enter a public GitHub repository link. ZStream scans every release and lists its APK assets.", color = theme.colors.type.secondary)
                        OutlinedTextField(
                            value = repositoryUrl,
                            onValueChange = {
                                repositoryUrl = it
                                releaseUpdateManager.setRepositoryUrl(it)
                            },
                            label = { Text("GitHub repository link") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(enabled = !running && repositoryUrl.isNotBlank(), onClick = { scanReleases() }) { Text("Scan releases") }
                        Text(status, color = theme.colors.type.text)
                        if (running) TextButton(onClick = { activeJob?.cancel() }) { Text("Cancel") }

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
                                    Text(apk.apkName, color = theme.colors.type.emphasis)
                                    Text("Version ${apk.version} • Tag ${apk.releaseTag}", color = theme.colors.type.text)
                                    Text("${formatBytes(apk.size)} • Uploaded ${formatGithubDate(apk.assetCreatedAt)}", color = theme.colors.type.secondary)
                                    Text("Release: ${apk.releaseName}", color = theme.colors.type.secondary)
                                }
                            }
                        }

                        if (apks.isNotEmpty()) {
                            val pageCount = ceil(apks.size / APK_ROWS_PER_PAGE.toDouble()).toInt()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = apkPage > 0, onClick = { apkPage-- }) { Text("Previous") }
                                Text("Page ${apkPage + 1} of $pageCount", color = theme.colors.type.text)
                                TextButton(enabled = apkPage + 1 < pageCount, onClick = { apkPage++ }) { Text("Next") }
                            }
                        }
                    }

                    TvInstallerPage.RELEASE_DETAIL -> {
                        selectedApk?.let { apk ->
                            Text(apk.apkName, color = theme.colors.type.emphasis)
                            ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("APK asset", color = theme.colors.type.emphasis)
                                    MetadataLine("Version", apk.version)
                                    MetadataLine("Label", apk.label ?: "None")
                                    MetadataLine("Size", formatBytes(apk.size))
                                    MetadataLine("Content type", apk.contentType)
                                    MetadataLine("State", apk.state)
                                    MetadataLine("Digest", apk.digest ?: "Not provided")
                                    MetadataLine("Downloads", apk.downloadCount.toString())
                                    MetadataLine("Uploaded by", apk.assetUploader)
                                    MetadataLine("Uploaded", formatGithubDate(apk.assetCreatedAt))
                                    MetadataLine("Updated", formatGithubDate(apk.assetUpdatedAt))
                                    MetadataLine("Asset ID", apk.assetId.toString())
                                    MetadataLine("Download URL", apk.downloadUrl)
                                }
                            }
                            ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Full release", color = theme.colors.type.emphasis)
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
                                    Text("Description", color = theme.colors.type.emphasis)
                                    Text(apk.releaseDescription.ifBlank { "No description" }, color = theme.colors.type.text)
                                }
                            }

                            progress?.let { current ->
                                val fraction = when (current) {
                                    is InstallProgress.Downloading -> current.bytes.fractionOf(current.totalBytes)
                                    is InstallProgress.Transferring -> current.bytes.fractionOf(current.totalBytes)
                                    else -> null
                                }
                                Text(progressLabel(current), color = theme.colors.type.secondary)
                                if (fraction == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                            }
                            Text(status, color = theme.colors.type.text)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = !running && selectedDevice != null, onClick = { install(apk) }) { Text("Install this APK") }
                                if (running) TextButton(onClick = { activeJob?.cancel() }) { Text("Cancel") }
                                if (!running && lastError != null) TextButton(onClick = { install(apk) }) { Text("Retry") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataLine(label: String, value: String) {
    val theme = LocalZStreamTheme.current
    Text("$label: $value", color = theme.colors.type.text)
}

private fun pageTitle(page: TvInstallerPage): String = when (page) {
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

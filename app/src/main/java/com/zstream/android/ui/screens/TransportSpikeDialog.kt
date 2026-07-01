package com.zstream.android.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zstream.android.data.adb.AdbFailureKind
import com.zstream.android.data.adb.AdbOperationException
import com.zstream.android.data.adb.TvAdbManager
import com.zstream.android.data.adb.InstallProgress
import com.zstream.android.data.adb.SavedTv
import com.zstream.android.theme.LocalZStreamTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_APK_URL = "https://github.com/alturyxx-gif/ZStream-Android/releases/download/full/app-debug.apk"

internal enum class TvSetupMode {
    AUTOMATIC,
    MANUAL_WIRELESS,
    LEGACY;

    fun fallback(): TvSetupMode = when (this) {
        AUTOMATIC -> MANUAL_WIRELESS
        MANUAL_WIRELESS, LEGACY -> LEGACY
    }
}

private enum class TvInstallerPage { CONNECTION, INSTALL }

@Composable
fun TvInstallerScreen(onDismiss: () -> Unit) {
    val tag = "TvInstaller"
    val theme = LocalZStreamTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { TvAdbManager.get(context) }

    var savedTv by remember { mutableStateOf<SavedTv?>(manager.getSavedTv()) }
    var page by remember { mutableStateOf(if (savedTv == null) TvInstallerPage.CONNECTION else TvInstallerPage.INSTALL) }
    var pairingCode by remember { mutableStateOf("") }
    var setupMode by remember { mutableStateOf(TvSetupMode.AUTOMATIC) }
    var manualHost by remember { mutableStateOf("") }
    var manualPairingPort by remember { mutableStateOf("") }
    var manualConnectPort by remember { mutableStateOf("") }
    var legacyHost by remember { mutableStateOf("") }
    var legacyPort by remember { mutableStateOf("5555") }
    var apkUrl by remember { mutableStateOf(DEFAULT_APK_URL) }
    var status by remember { mutableStateOf(if (savedTv == null) "No TV paired" else "Ready") }
    var progress by remember { mutableStateOf<InstallProgress?>(null) }
    var lastError by remember { mutableStateOf<Throwable?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    val running = activeJob != null

    fun fail(t: Throwable) {
        Log.e(tag, "operation failed", t)
        lastError = t
        status = userMessage(t)
        progress = null
    }

    fun install() {
        Log.d(tag, "install flow start savedTv=$savedTv apkUrl=$apkUrl")
        activeJob = scope.launch {
            val operationJob = coroutineContext.job
            lastError = null
            status = "Connecting…"
            try {
                val result = withContext(Dispatchers.IO) {
                    manager.installFromUrl(
                        apkUrl.trim(),
                        onProgress = { progress = it },
                        isCancelled = { !operationJob.isActive },
                    )
                }
                status = "Installed successfully on ${result.model}"
                progress = null
                Log.d(tag, "install succeeded output=${result.packageManagerOutput}")
            } catch (_: CancellationException) {
                Log.d(tag, "install cancelled")
                status = "Cancelled"
                progress = null
            } catch (t: Throwable) {
                fail(t)
            } finally {
                activeJob = null
                Log.d(tag, "install flow end")
            }
        }
    }

    BackHandler {
        if (!running) {
            if (page == TvInstallerPage.INSTALL) page = TvInstallerPage.CONNECTION else onDismiss()
        }
    }
    Surface(
        color = theme.colors.background.main,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row {
                    if (page == TvInstallerPage.INSTALL) {
                        TextButton(enabled = !running, onClick = { page = TvInstallerPage.CONNECTION }) { Text("Back") }
                    }
                    Text(
                        if (page == TvInstallerPage.CONNECTION) "Connect to TV" else "Install APK",
                        color = theme.colors.type.emphasis,
                    )
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
                    savedTv?.let { tv ->
                        Text("Saved TV: ${tv.model} (${tv.host})", color = theme.colors.type.text)
                        Text(
                            "Keep the TV connected to the same local network with debugging enabled. ZStream will reconnect automatically when you install.",
                            color = theme.colors.type.secondary,
                        )
                        TextButton(
                            enabled = !running,
                            onClick = {
                                manager.forgetSavedTv()
                                savedTv = null
                                page = TvInstallerPage.CONNECTION
                                setupMode = TvSetupMode.AUTOMATIC
                                status = "Saved TV forgotten"
                            },
                        ) { Text("Forget TV") }
                    }

                    if (savedTv == null) {
                        Text("Before you start", color = theme.colors.type.emphasis)
                        Text(
                            "• Connect the phone and TV to the same network. The TV may use Wi-Fi or Ethernet.\n" +
                                "• Avoid guest Wi-Fi, or VPNs.\n" +
                                "• Keep this app open while pairing and approve prompts on the TV.\n" +
                                "• On the TV, enable Developer options by going to your settings → Device Preferences → About → Android TV OS build/build number and keep clicking on it until you see that developer options have been enabled.\n",
                            color = theme.colors.type.secondary,
                        )
                    }

                    if (savedTv == null) when (setupMode) {
                        TvSetupMode.AUTOMATIC -> {
                            Text("Automatic wireless pairing", color = theme.colors.type.emphasis)
                            Text(
                                "1. Then go back to Device Preferences and open Developer options → Turn on USB Debugging and then go to Wireless debugging and turn it on.\n" +
                                "2. Select Pair device with pairing code and leave that screen open.\n" +
                                "3. Enter the 6-digit code below. ZStream discovers the TV address and ports automatically.\n\n" +
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
                                    Log.d(tag, "automatic pair pressed codeLen=${pairingCode.length}")
                                    activeJob = scope.launch {
                                        lastError = null
                                        status = "Finding and pairing with TV…"
                                        try {
                                            val model = withContext(Dispatchers.IO) {
                                                manager.discoverPairAndConnect(pairingCode)
                                            }
                                            savedTv = manager.getSavedTv()
                                            pairingCode = ""
                                            status = "Paired with $model"
                                            page = TvInstallerPage.INSTALL
                                            Log.d(tag, "automatic pair succeeded model=$model")
                                        } catch (_: CancellationException) {
                                            status = "Cancelled"
                                        } catch (t: Throwable) {
                                            Log.w(tag, "automatic pair failed type=${t.javaClass.simpleName} msg=${t.message}")
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
                            TextButton(
                                enabled = !running,
                                onClick = { setupMode = TvSetupMode.MANUAL_WIRELESS },
                            ) { Text("Enter details manually") }
                        }

                        TvSetupMode.MANUAL_WIRELESS -> {
                            Text("Manual wireless pairing", color = theme.colors.type.emphasis)
                            Text(
                                "1. Then go back to Device Preferences and open Developer options → Turn on USB Debugging and then go to Wireless debugging and turn it on.\n" +
                                    "2. Keep Wireless debugging and Pair device with pairing code open on the TV.\n" +
                                    "3. Enter the TV IP address and pairing port shown in the pairing window.\n" +
                                    "4. Enter the connect port shown as IP address & port on the main Wireless debugging screen.\n" +
                                    "5. Enter the current 6-digit code, then pair.\n\n" +
                                    "The pairing and connect ports are usually different. If this fails, ZStream opens legacy ADB setup.",
                                color = theme.colors.type.secondary,
                            )
                            OutlinedTextField(
                                value = manualHost,
                                onValueChange = { manualHost = it.trim() },
                                label = { Text("TV IP address") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = manualPairingPort,
                                onValueChange = { manualPairingPort = it.filter(Char::isDigit).take(5) },
                                label = { Text("Pairing port") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = manualConnectPort,
                                onValueChange = { manualConnectPort = it.filter(Char::isDigit).take(5) },
                                label = { Text("Connect port") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = pairingCode,
                                onValueChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                                label = { Text("6-digit pairing code") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                enabled = !running && manualHost.isNotBlank() &&
                                    manualPairingPort.toIntOrNull() in 1..65535 &&
                                    manualConnectPort.toIntOrNull() in 1..65535 && pairingCode.length == 6,
                                onClick = {
                                    Log.d(tag, "manual wireless pair pressed host=$manualHost pairingPort=$manualPairingPort connectPort=$manualConnectPort")
                                    activeJob = scope.launch {
                                        lastError = null
                                        status = "Pairing with manual details…"
                                        try {
                                            val model = withContext(Dispatchers.IO) {
                                                manager.pairAndConnect(
                                                    manualHost,
                                                    requireNotNull(manualPairingPort.toIntOrNull()),
                                                    requireNotNull(manualConnectPort.toIntOrNull()),
                                                    pairingCode,
                                                )
                                            }
                                            savedTv = manager.getSavedTv()
                                            pairingCode = ""
                                            status = "Paired with $model"
                                            page = TvInstallerPage.INSTALL
                                            Log.d(tag, "manual wireless pair succeeded model=$model")
                                        } catch (_: CancellationException) {
                                            status = "Cancelled"
                                        } catch (t: Throwable) {
                                            Log.w(tag, "manual wireless pair failed type=${t.javaClass.simpleName} msg=${t.message}")
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
                                TextButton(enabled = !running, onClick = { setupMode = TvSetupMode.AUTOMATIC }) {
                                    Text("Try automatic")
                                }
                                TextButton(
                                    enabled = !running,
                                    onClick = {
                                        if (legacyHost.isBlank()) legacyHost = manualHost
                                        setupMode = TvSetupMode.LEGACY
                                    },
                                ) { Text("Use legacy ADB") }
                            }
                        }

                        TvSetupMode.LEGACY -> {
                            Text("Legacy ADB over network", color = theme.colors.type.emphasis)
                            Text(
                                "Use this for older TVs without Wireless debugging.\n\n" +
                                    "1. On the TV, enable Developer options and USB, network, or ADB debugging.\n" +
                                    "2. Find the TV IP address in its Network settings. The ADB port is usually 5555.\n" +
                                    "3. Enter both values and connect. Accept the debugging prompt on the TV; choose Always allow if available.\n\n" +
//                                    "Emulator test: use 10.0.2.2 and the TV emulator port (for example, emulator-5556 uses 5557).\n\n" +
                                    "If connection fails, verify the TV IP did not change, debugging is still enabled, and the router allows devices to communicate.",
                                color = theme.colors.type.secondary,
                            )
                            OutlinedTextField(
                                value = legacyHost,
                                onValueChange = { legacyHost = it.trim() },
                                label = { Text("TV IP address") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = legacyPort,
                                onValueChange = { legacyPort = it.filter(Char::isDigit).take(5) },
                                label = { Text("ADB port") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                enabled = !running && legacyHost.isNotBlank() && legacyPort.toIntOrNull() in 1..65535,
                                onClick = {
                                    Log.d(tag, "legacy connect pressed host=$legacyHost port=$legacyPort")
                                    activeJob = scope.launch {
                                        lastError = null
                                        status = "Connecting… Accept the debugging prompt on the TV."
                                        try {
                                            val model = withContext(Dispatchers.IO) {
                                                manager.connectLegacy(legacyHost, requireNotNull(legacyPort.toIntOrNull()))
                                            }
                                            savedTv = manager.getSavedTv()
                                            status = "Connected to $model"
                                            page = TvInstallerPage.INSTALL
                                            Log.d(tag, "legacy connect succeeded model=$model")
                                        } catch (_: CancellationException) {
                                            status = "Cancelled"
                                        } catch (t: Throwable) {
                                            Log.w(tag, "legacy connect failed type=${t.javaClass.simpleName} msg=${t.message}")
                                            fail(t)
                                        } finally {
                                            activeJob = null
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Connect with legacy ADB") }
                            TextButton(enabled = !running, onClick = { setupMode = TvSetupMode.MANUAL_WIRELESS }) {
                                Text("Back to wireless debugging")
                            }
                        }
                    }

                    Text("Status", color = theme.colors.type.emphasis)
                    Text(status, color = theme.colors.type.text)
                    if (savedTv != null) {
                        Button(enabled = !running, onClick = { page = TvInstallerPage.INSTALL }) {
                            Text("Continue to install")
                        }
                    }
                        }

                        TvInstallerPage.INSTALL -> {
                    savedTv?.let { tv ->
                        Text("Installing on ${tv.model} (${tv.host})", color = theme.colors.type.text)
                    }
                    HorizontalDivider(color = theme.colors.type.divider.copy(alpha = 0.18f))
                    Text("Install APK", color = theme.colors.type.emphasis)
                    Text(
                        "Enter a direct HTTP or HTTPS link to one APK file. Keep both devices online and leave this screen open during download, transfer, and installation.",
                        color = theme.colors.type.secondary,
                    )
                    OutlinedTextField(
                        value = apkUrl,
                        onValueChange = { apkUrl = it },
                        label = { Text("Direct APK URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    progress?.let { current ->
                        val fraction = when (current) {
                            is InstallProgress.Downloading -> current.bytes.fractionOf(current.totalBytes)
                            is InstallProgress.Transferring -> current.bytes.fractionOf(current.totalBytes)
                            else -> null
                        }
                        Text(progressLabel(current), color = theme.colors.type.secondary)
                        if (fraction == null) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    Text(status, color = theme.colors.type.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !running && savedTv != null && apkUrl.isNotBlank(),
                        onClick = {
                            Log.d(tag, "install pressed savedTv=$savedTv apkUrl=$apkUrl")
                            install()
                        },
                    ) { Text("Install") }
                        if (running) TextButton(onClick = {
                            Log.d(tag, "cancel pressed")
                            activeJob?.cancel()
                        }) { Text("Cancel") }
                        if (!running && lastError != null && savedTv != null) {
                            TextButton(onClick = {
                                Log.d(tag, "retry pressed")
                                install()
                            }) { Text("Retry") }
                        }
                    }
                        }
                    }
                }
        }
    }
}

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
        AdbFailureKind.DOWNLOAD -> "Could not download the APK. Check the URL and connection."
        AdbFailureKind.INSTALL -> t.message ?: "The TV rejected the APK."
        AdbFailureKind.WRONG_DEVICE -> "The discovered device is not the saved TV."
        AdbFailureKind.CANCELLED -> "Cancelled"
        AdbFailureKind.UNKNOWN -> t.message ?: "Operation failed"
    }
    else -> t.message ?: "Operation failed"
}

package com.zstream.android.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.zstream.android.data.adb.DiscoveredAdbEndpoints
import com.zstream.android.data.adb.InstallProgress
import com.zstream.android.data.adb.SavedTv
import com.zstream.android.theme.LocalZStreamTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_APK_URL = "https://github.com/alturyxx-gif/ZStream-Android/releases/download/test/facer.apk"

@Composable
fun TvInstallerDialog(onDismiss: () -> Unit) {
    val tag = "TvInstaller"
    val theme = LocalZStreamTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { TvAdbManager.get(context) }

    var devices by remember { mutableStateOf<List<DiscoveredAdbEndpoints>>(emptyList()) }
    var selected by remember { mutableStateOf<DiscoveredAdbEndpoints?>(null) }
    var savedTv by remember { mutableStateOf<SavedTv?>(manager.getSavedTv()) }
    var pairingCode by remember { mutableStateOf("") }
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
                status = "Cancelled"
                progress = null
            } catch (t: Throwable) {
                fail(t)
            } finally {
                activeJob = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        confirmButton = {},
        dismissButton = { TextButton(enabled = !running, onClick = onDismiss) { Text("Close") } },
        title = { Text("Install APK on TV") },
        text = {
            Surface(
                color = theme.colors.modal.background,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.25f)),
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    savedTv?.let { tv ->
                        Text("Saved TV: ${tv.model} (${tv.host})", color = theme.colors.type.text)
                        TextButton(
                            enabled = !running,
                            onClick = {
                                manager.forgetSavedTv()
                                savedTv = null
                                status = "Saved TV forgotten"
                            },
                        ) { Text("Forget TV") }
                    }

                    Button(
                        enabled = !running,
                        onClick = {
                            activeJob = scope.launch {
                                lastError = null
                                status = "Searching for TVs…"
                                try {
                                    devices = withContext(Dispatchers.IO) { manager.discoverDevices() }
                                    selected = devices.firstOrNull()
                                    status = "Found ${devices.size} TV${if (devices.size == 1) "" else "s"}"
                                } catch (t: Throwable) {
                                    fail(AdbOperationException(AdbFailureKind.DISCOVERY, "No TVs were found.", t))
                                } finally {
                                    activeJob = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Find TVs") }

                    devices.forEach { device ->
                        TextButton(
                            onClick = { selected = device },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (selected == device) theme.colors.global.accentA else theme.colors.type.text,
                            ),
                        ) {
                            Text("${device.host}:${device.connectPort}${if (device.pairingPort != null) " · ready to pair" else ""}")
                        }
                    }

                    selected?.takeIf { it.pairingPort != null }?.let { device ->
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
                                    status = "Pairing…"
                                    try {
                                        val model = withContext(Dispatchers.IO) {
                                            manager.pairAndConnect(
                                                device.host,
                                                device.pairingPort,
                                                requireNotNull(device.connectPort),
                                                pairingCode,
                                            )
                                        }
                                        savedTv = manager.getSavedTv()
                                        pairingCode = ""
                                        status = "Paired with $model"
                                    } catch (t: Throwable) {
                                        fail(t)
                                    } finally {
                                        activeJob = null
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Pair TV") }
                    }

                    HorizontalDivider(color = theme.colors.type.divider.copy(alpha = 0.18f))
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
                            onClick = { install() },
                        ) { Text("Install") }
                        if (running) TextButton(onClick = { activeJob?.cancel() }) { Text("Cancel") }
                        if (!running && lastError != null && savedTv != null) {
                            TextButton(onClick = { install() }) { Text("Retry") }
                        }
                    }
                }
            }
        },
    )
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

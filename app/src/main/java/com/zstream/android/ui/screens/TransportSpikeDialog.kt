package com.zstream.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zstream.android.data.adb.AdbSpikeConnectionManager
import com.zstream.android.theme.LocalZStreamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

@Composable
fun TransportSpikeDialog(
    onDismiss: () -> Unit,
) {
    val tag = "AdbSpikeUI"
    val theme = LocalZStreamTheme.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val manager = remember(context) { AdbSpikeConnectionManager.get(context) }

    var host by remember { mutableStateOf("192.168.0.147") }
    var pairingPort by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("39597") }
    var pairingCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Fill in host, pairing port, connect port, and code.") }
    var running by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("ADB transport spike") },
        text = {
            Surface(
                color = theme.colors.modal.background,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, theme.colors.type.divider.copy(alpha = 0.25f)),
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("This blocks the homescreen and proves pair/connect/shell from the phone.", color = theme.colors.type.secondary, fontSize = 13.sp)
                    OutlinedTextField(value = host, onValueChange = { host = it.trim() }, label = { Text("Host / IP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = pairingPort, onValueChange = { pairingPort = it.filter(Char::isDigit) }, label = { Text("Pairing port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = connectPort, onValueChange = { connectPort = it.filter(Char::isDigit) }, label = { Text("Connect port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = pairingCode, onValueChange = { pairingCode = it.filter(Char::isDigit).take(6) }, label = { Text("Pairing code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    HorizontalDivider(color = theme.colors.type.divider.copy(alpha = 0.18f))
                    Text("Status", fontWeight = FontWeight.SemiBold, color = theme.colors.type.emphasis)
                    Text(status, color = theme.colors.type.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !running,
                            onClick = {
                                scope.launch {
                                    running = true
                                    status = "Pairing and connecting..."
                                    Log.d(tag, "Pair + Connect + Probe pressed host=$host pairingPort=$pairingPort connectPort=$connectPort")
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            manager.pairAndConnect(
                                                host = host,
                                                pairingPort = pairingPort.toInt(),
                                                connectPort = connectPort.toInt(),
                                                pairingCode = pairingCode,
                                            )
                                        }
                                        Log.d(tag, "Pair + Connect + Probe succeeded shellResult=${result.trim()}")
                                        status = "Success"
                                    } catch (t: Throwable) {
                                        Log.e(tag, "Pair + Connect + Probe failed", t)
                                        status = "Failed"
                                    } finally {
                                        running = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.colors.global.accentA),
                        ) {
                            Text("Pair + Connect + Probe")
                        }
                        TextButton(
                            enabled = !running,
                            onClick = {
                                scope.launch {
                                    running = true
                                    status = "Running shell probe..."
                                    Log.d(tag, "Probe shell pressed host=$host connectPort=$connectPort")
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            manager.runShell("getprop ro.product.model")
                                        }
                                        Log.d(tag, "Probe shell succeeded shellResult=${result.trim()}")
                                        status = "Success"
                                    } catch (t: Throwable) {
                                        Log.e(tag, "Probe shell failed", t)
                                        status = "Failed"
                                    } finally {
                                        running = false
                                    }
                                }
                            },
                        ) {
                            Text("Probe shell")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "If pairing succeeds but connect fails, the TV likely wants its connect port instead of the pairing port.",
                        color = theme.colors.type.secondary,
                        fontSize = 12.sp,
                    )
                }
            }
        },
    )
}

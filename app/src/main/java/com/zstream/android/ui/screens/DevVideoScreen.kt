package com.zstream.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import com.zstream.android.ui.components.themed.ZsOutlinedWrapper
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.theme.ZStreamTheme
import org.json.JSONObject

private const val DEV_HLS_TEST_URL = "https://alpha-charlott.github.io/video-openh264/Sintel_master.m3u8"
private const val DEV_MP4_TEST_URL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
private const val DEV_MKV_TEST_URL = ""

/**
 * Dev-only stream tester -- replicates the website's /dev/video tool: paste a raw stream URL,
 * pick HLS or MP4, optionally attach request headers, and play it directly. There is no plugin
 * source resolution here at all (see DevPlayerScreen) -- this is for testing a link someone hands
 * you, not for picking a source for real content.
 *
 * Opened by tapping the app version 5x in Settings (see SettingsScreen's onVersionTap).
 */
@Composable
fun DevVideoScreen(nav: NavController) {
    val theme = LocalZStreamTheme.current
    // TODO: pre-filled with a temporary MKV test link while adding MKV support -- remove once
    // that's verified, back to a blank default.
    var url by remember { mutableStateOf(DEV_MKV_TEST_URL) }
    var type by remember { mutableStateOf("mkv") }
    var headersEnabled by remember { mutableStateOf(false) }
    val headers = remember { mutableStateListOf("" to "") }

    fun startStream(streamUrl: String, streamType: String) {
        if (streamUrl.isBlank()) return
        val headersJson = if (headersEnabled) {
            val obj = JSONObject()
            headers.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) obj.put(key.trim(), value.trim())
            }
            if (obj.length() > 0) obj.toString() else null
        } else null

        val encodedUrl = android.net.Uri.encode(streamUrl)
        val encodedType = android.net.Uri.encode(streamType)
        val route = if (headersJson != null) {
            "devPlayer?url=$encodedUrl&type=$encodedType&headers=${android.net.Uri.encode(headersJson)}"
        } else {
            "devPlayer?url=$encodedUrl&type=$encodedType"
        }
        nav.navigate(route)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.colors.background.main)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val backInteraction = remember { MutableInteractionSource() }
            val backFocused by backInteraction.collectIsFocusedAsState()
            ZsOutlinedWrapper(shape = RoundedCornerShape(50), visible = backFocused) {
                IconButton(onClick = { nav.popBackStack() }, interactionSource = backInteraction) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.colors.type.emphasis)
                }
            }
            Text(
                "Video tester",
                color = theme.colors.type.emphasis,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Custom stream", color = theme.colors.type.emphasis, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            var urlFocused by remember { mutableStateOf(false) }
            ZsOutlinedWrapper(shape = RoundedCornerShape(4.dp), visible = urlFocused, gap = 2.dp) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://...") },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { urlFocused = it.isFocused },
                    singleLine = true,
                    colors = devTextFieldColors(theme),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DevTypeChip("MP4", type == "mp4") { type = "mp4" }
                DevTypeChip("HLS", type == "hls") { type = "hls" }
                DevTypeChip("MKV", type == "mkv") { type = "mkv" }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Headers", color = theme.colors.type.emphasis, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                val switchInteraction = remember { MutableInteractionSource() }
                val switchFocused by switchInteraction.collectIsFocusedAsState()
                ZsOutlinedWrapper(shape = RoundedCornerShape(50), visible = switchFocused) {
                    Switch(
                        checked = headersEnabled,
                        onCheckedChange = {
                            headersEnabled = it
                            if (!it) {
                                headers.clear()
                                headers.add("" to "")
                            }
                        },
                        interactionSource = switchInteraction,
                    )
                }
            }
            if (headersEnabled) {
                Spacer(Modifier.height(8.dp))
                headers.forEachIndexed { index, (key, value) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        var keyFocused by remember { mutableStateOf(false) }
                        ZsOutlinedWrapper(shape = RoundedCornerShape(4.dp), visible = keyFocused, gap = 2.dp, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = key,
                                onValueChange = { headers[index] = it to headers[index].second },
                                placeholder = { Text("Key") },
                                modifier = Modifier.fillMaxWidth().onFocusChanged { keyFocused = it.isFocused },
                                singleLine = true,
                                colors = devTextFieldColors(theme),
                            )
                        }
                        var valueFocused by remember { mutableStateOf(false) }
                        ZsOutlinedWrapper(shape = RoundedCornerShape(4.dp), visible = valueFocused, gap = 2.dp, modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { headers[index] = headers[index].first to it },
                                placeholder = { Text("Value") },
                                modifier = Modifier.fillMaxWidth().onFocusChanged { valueFocused = it.isFocused },
                                singleLine = true,
                                colors = devTextFieldColors(theme),
                            )
                        }
                        val removeInteraction = remember { MutableInteractionSource() }
                        val removeFocused by removeInteraction.collectIsFocusedAsState()
                        ZsOutlinedWrapper(shape = RoundedCornerShape(50), visible = removeFocused) {
                            IconButton(onClick = { if (headers.size > 1) headers.removeAt(index) }, interactionSource = removeInteraction) {
                                Icon(Icons.Filled.Close, null, tint = theme.colors.type.dimmed)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                val addHeaderInteraction = remember { MutableInteractionSource() }
                val addHeaderFocused by addHeaderInteraction.collectIsFocusedAsState()
                ZsOutlinedWrapper(shape = RoundedCornerShape(4.dp), visible = addHeaderFocused, gap = 2.dp) {
                    Button(
                        onClick = { headers.add("" to "") },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.colors.buttons.purple),
                        interactionSource = addHeaderInteraction,
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add header")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            val startInteraction = remember { MutableInteractionSource() }
            val startFocused by startInteraction.collectIsFocusedAsState()
            ZsOutlinedWrapper(shape = RoundedCornerShape(4.dp), visible = startFocused, gap = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { startStream(url, type) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.colors.buttons.purple),
                    interactionSource = startInteraction,
                ) {
                    Text("Start stream")
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Preset tests", color = theme.colors.type.emphasis, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val hlsInteraction = remember { MutableInteractionSource() }
                val hlsFocused by hlsInteraction.collectIsFocusedAsState()
                ZsOutlinedWrapper(shape = RoundedCornerShape(4.dp), visible = hlsFocused, gap = 2.dp, modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { startStream(DEV_HLS_TEST_URL, "hls") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.colors.background.secondary),
                        interactionSource = hlsInteraction,
                    ) {
                        Text("HLS test", color = theme.colors.type.emphasis)
                    }
                }
                val mp4Interaction = remember { MutableInteractionSource() }
                val mp4Focused by mp4Interaction.collectIsFocusedAsState()
                ZsOutlinedWrapper(shape = RoundedCornerShape(4.dp), visible = mp4Focused, gap = 2.dp, modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { startStream(DEV_MP4_TEST_URL, "mp4") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.colors.background.secondary),
                        interactionSource = mp4Interaction,
                    ) {
                        Text("MP4 test", color = theme.colors.type.emphasis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DevTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalZStreamTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    ZsOutlinedWrapper(shape = RoundedCornerShape(8.dp), visible = isFocused, gap = 2.dp) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) theme.colors.buttons.purple else theme.colors.background.secondary)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(label, color = if (selected) Color.White else theme.colors.type.emphasis, fontSize = 13.sp)
        }
    }
}

@Composable
private fun devTextFieldColors(theme: ZStreamTheme) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = theme.colors.type.emphasis,
    unfocusedTextColor = theme.colors.type.emphasis,
    focusedContainerColor = theme.colors.background.secondary,
    unfocusedContainerColor = theme.colors.background.secondary,
    focusedBorderColor = theme.colors.buttons.purple,
    unfocusedBorderColor = theme.colors.type.divider,
    cursorColor = theme.colors.buttons.purple,
)

package com.zstream.android.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.local.preferences.SettingsPreferences
import com.zstream.android.download.DownloadDestinationBroker
import com.zstream.android.download.DownloadDestinationChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadDestinationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPrefs: SettingsPreferences,
) : ViewModel() {
    suspend fun currentSdCardTreeUri(): String? = settingsPrefs.settings.first().downloadSdCardTreeUri

    /** Persists [uri] as the SD card destination and keeps write access across app restarts. */
    fun persistSdCardTreeUri(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val current = settingsPrefs.settings.first()
            settingsPrefs.updateSettings(current.copy(downloadSdCardTreeUri = uri.toString()), syncToRemote = false)
            onDone(uri.toString())
        }
    }
}

/**
 * Mounted once at the app root (see MainActivity) alongside NavGraph. Listens for
 * [DownloadDestinationBroker] requests from any download entry point (player quality picker,
 * quick-download buttons) and shows a single "where should this go" prompt, handling the SD card
 * folder picker itself so none of those call sites need their own SAF wiring.
 */
@Composable
fun DownloadDestinationPrompt() {
    val vm: DownloadDestinationViewModel = hiltViewModel()
    val pending by DownloadDestinationBroker.pending.collectAsState()
    val scope = rememberCoroutineScope()
    var awaitingFolderPick by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        awaitingFolderPick = false
        if (uri == null) {
            DownloadDestinationBroker.resolve(null)
        } else {
            vm.persistSdCardTreeUri(uri) { treeUriString ->
                DownloadDestinationBroker.resolve(DownloadDestinationChoice.SdCard(treeUriString))
            }
        }
    }

    if (pending && !awaitingFolderPick) {
        AlertDialog(
            onDismissRequest = { DownloadDestinationBroker.resolve(null) },
            title = { Text("Save download to...") },
            text = { Text("Choose where this download should be stored.") },
            confirmButton = {
                TextButton(onClick = { DownloadDestinationBroker.resolve(DownloadDestinationChoice.AppFolder) }) {
                    Text("ZStream folder")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        val existing = vm.currentSdCardTreeUri()
                        if (existing != null) {
                            DownloadDestinationBroker.resolve(DownloadDestinationChoice.SdCard(existing))
                        } else {
                            awaitingFolderPick = true
                            folderPicker.launch(null)
                        }
                    }
                }) {
                    Text("External")
                }
            },
        )
    }
}

package com.zstream.android.ui.screens

import android.util.Log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.zstream.android.data.TraktSessionExport
import com.zstream.android.data.TraktState
import com.zstream.android.data.TvSyncDiscoveredReceiver
import com.zstream.android.data.TvSyncPayload
import com.zstream.android.data.TvSyncReceiverState
import com.zstream.android.data.TvSyncRepository
import com.zstream.android.data.local.entity.SettingsEntity
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.components.themed.ZsButton
import com.zstream.android.ui.components.themed.ZsButtonVariant
import com.zstream.android.ui.components.themed.ZsCard
import com.zstream.android.ui.components.themed.ZsCardVariant
import com.zstream.android.ui.components.themed.ZsIconButton
import com.zstream.android.ui.components.themed.ZsIconButtonVariant
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant
import com.zstream.android.ui.navigation.rememberSafeNavigateBack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zstream.android.data.AccountRepository
import com.zstream.android.data.TraktRepository
import com.zstream.android.data.local.preferences.SettingsPreferences

private enum class TvSyncPhonePage { INTRO, DISCOVER, PAIR, INTEGRATIONS, PASSPHRASE, ACCOUNT_DEVICE, REVIEW, RESULT }
private const val TV_SYNC_UI_TAG = "TvSyncUI"

@HiltViewModel
class TvSyncViewModel @Inject constructor(
    private val repo: TvSyncRepository,
    settingsPreferences: SettingsPreferences,
    private val traktRepository: TraktRepository,
    accountRepository: AccountRepository,
) : ViewModel() {
    val receiverState: StateFlow<TvSyncReceiverState> = repo.receiverState
    val settings: StateFlow<SettingsEntity> = settingsPreferences.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsEntity(),
    )
    val traktState: StateFlow<TraktState> = traktRepository.state
    val accountSession = accountRepository.session.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    suspend fun discoverReceivers(): List<TvSyncDiscoveredReceiver> = repo.discoverReceivers()
    suspend fun fetchHello(host: String, port: Int) = repo.fetchHello(host, port)
    suspend fun pair(host: String, port: Int, code: String, salt: String, sessionId: String, phoneName: String) =
        repo.pair(host, port, code, salt, sessionId, phoneName)

    suspend fun buildPayload(
        tvName: String,
        selected: Map<String, Boolean>,
        passphrase: String?,
        accountDeviceName: String?,
    ): TvSyncPayload {
        val current = settings.value
        val traktSession = if (selected["trakt"] == true) traktRepository.exportSession() else null
        return TvSyncPayload(
            tvName = tvName,
            tmdbApiKey = current.tmdbApiKey.takeIf { selected["tmdb"] == true },
            febboxKey = current.febboxKey.takeIf { selected["febbox"] == true },
            debridToken = current.debridToken.takeIf { selected["debrid"] == true },
            debridService = current.debridService.takeIf { selected["debrid"] == true },
            tidbKey = current.tidbKey.takeIf { selected["tidb"] == true },
            wyzieKey = current.wyzieKey.takeIf { selected["wyzie"] == true },
            traktSession = traktSession,
            passphrase = passphrase?.trim()?.takeIf(String::isNotBlank),
            accountDeviceName = accountDeviceName?.trim()?.takeIf(String::isNotBlank),
        )
    }

    suspend fun sendPayload(host: String, port: Int, payload: TvSyncPayload) = repo.sendPayload(host, port, payload)
    fun startReceiver() = viewModelScope.launch { repo.startReceiver() }
    fun stopReceiver() = repo.stopReceiver()
    fun renameTv(name: String) = viewModelScope.launch { repo.setDefaultTvName(name) }
    fun applyPending() = viewModelScope.launch { repo.applyPendingPayload() }
    fun rejectPending() = repo.rejectPendingPayload()
}

@Composable
fun TvSyncScreen(
    nav: NavController,
    vm: TvSyncViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel(),
) {
    val isTv = LocalIsTv.current
    if (isTv) {
        TvSyncReceiverScreen(nav, vm)
    } else {
        TvSyncSenderScreen(nav, vm, settingsVm)
    }
}

@Composable
private fun TvSyncSenderScreen(
    nav: NavController,
    vm: TvSyncViewModel,
    settingsVm: SettingsViewModel,
) {
    val theme = LocalZStreamTheme.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val trakt by vm.traktState.collectAsStateWithLifecycle()
    val session by vm.accountSession.collectAsStateWithLifecycle()

    var page by remember { mutableStateOf(TvSyncPhonePage.INTRO) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<List<TvSyncDiscoveredReceiver>>(emptyList()) }
    var selectedReceiver by remember { mutableStateOf<TvSyncDiscoveredReceiver?>(null) }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("") }
    var tvName by remember { mutableStateOf("ZStream TV") }
    var pairCode by remember { mutableStateOf("") }
    var pairSalt by remember { mutableStateOf("") }
    var pairSessionId by remember { mutableStateOf("") }
    var senderName by remember(session) { mutableStateOf(session?.deviceName?.ifBlank { "Android Phone" } ?: "Android Phone") }
    var passphraseEnabled by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var accountDeviceName by remember { mutableStateOf("ZStream TV") }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val available = remember { mutableStateMapOf<String, Pair<Boolean, String>>() }

    suspend fun refreshAvailability() {
        available["tmdb"] = settings.tmdbApiKey
            ?.takeIf { it.isNotBlank() }
            ?.let {
                settingsVm.validateTmdbKey(it).fold(
                    onSuccess = { ok -> ok to if (ok) "Validated on this phone" else "Invalid on this phone" },
                    onFailure = { false to (it.message ?: "Validation failed") },
                )
            } ?: (false to "No key on this phone")
        available["tidb"] = settings.tidbKey
            ?.takeIf { it.isNotBlank() }
            ?.let {
                settingsVm.validateTidbKey(it).fold(
                    onSuccess = { ok -> ok to if (ok) "Validated on this phone" else "Invalid on this phone" },
                    onFailure = { false to (it.message ?: "Validation failed") },
                )
            } ?: (false to "No key on this phone")
        available["wyzie"] = settings.wyzieKey
            ?.takeIf { it.isNotBlank() }
            ?.let {
                settingsVm.validateWyzieKey(it).fold(
                    onSuccess = { ok -> ok to if (ok) "Validated on this phone" else "Invalid on this phone" },
                    onFailure = { false to (it.message ?: "Validation failed") },
                )
            } ?: (false to "No key on this phone")
        available["febbox"] = (!settings.febboxKey.isNullOrBlank()) to if (settings.febboxKey.isNullOrBlank()) "No key on this phone" else "Saved on this phone"
        available["debrid"] = (!settings.debridToken.isNullOrBlank()) to if (settings.debridToken.isNullOrBlank()) "No token on this phone" else "Saved on this phone"
        available["trakt"] = trakt.connected to if (trakt.connected) "Connected on this phone" else "Not connected on this phone"
        available.forEach { (key, value) ->
            if (selected[key] == null) selected[key] = value.first
            else if (!value.first) selected[key] = false
        }
    }

    LaunchedEffect(page) {
        if (page == TvSyncPhonePage.INTEGRATIONS) {
            loading = true
            refreshAvailability()
            loading = false
        }
    }

    BackHandler {
        when (page) {
            TvSyncPhonePage.INTRO -> onBack()
            TvSyncPhonePage.DISCOVER -> page = TvSyncPhonePage.INTRO
            TvSyncPhonePage.PAIR -> page = TvSyncPhonePage.DISCOVER
            TvSyncPhonePage.INTEGRATIONS -> page = TvSyncPhonePage.PAIR
            TvSyncPhonePage.PASSPHRASE -> page = TvSyncPhonePage.INTEGRATIONS
            TvSyncPhonePage.ACCOUNT_DEVICE -> page = TvSyncPhonePage.PASSPHRASE
            TvSyncPhonePage.REVIEW -> page = if (passphraseEnabled) TvSyncPhonePage.ACCOUNT_DEVICE else TvSyncPhonePage.PASSPHRASE
            TvSyncPhonePage.RESULT -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(theme.colors.background.main)) {
        ZsIconButton(
            onClick = { onBack() },
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            variant = ZsIconButtonVariant.Ghost,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Sync to TV", color = theme.colors.type.emphasis, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            status?.let {
                ZsStatusBanner(
                    message = it,
                    variant = ZsStatusBannerVariant.Info,
                )
            }
            when (page) {
                TvSyncPhonePage.INTRO -> {
                    Text("Use your phone to send integrations and optionally sign the TV in with your passphrase.", color = theme.colors.type.secondary)
                    SyncInstructionCard(
                        title = "Before you start",
                        body = "Open ZStream on the TV and choose Sync from phone. Leave that screen open."
                    )
                    ZsButton(
                        text = "Find TV",
                        onClick = { page = TvSyncPhonePage.DISCOVER },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TvSyncPhonePage.DISCOVER -> {
                    SyncInstructionCard("Find your TV", "Discovery looks for TVs on the same network. If none appear, enter the TV IP manually and still use the code shown on the TV.")
                    ZsButton(
                        text = if (loading) "Scanning…" else "Scan for TVs",
                        onClick = {
                            scope.launch {
                                loading = true
                                status = null
                                Log.d(TV_SYNC_UI_TAG, "scan start")
                                discovered = vm.discoverReceivers()
                                Log.d(TV_SYNC_UI_TAG, "scan result count=${discovered.size} values=$discovered")
                                loading = false
                                if (discovered.isEmpty()) status = "No TVs found automatically. Use manual IP below."
                            }
                        },
                        loading = loading,
                        leadingIcon = Icons.Default.Refresh,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    discovered.forEach { receiver ->
                        ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(receiver.tvName, color = theme.colors.type.emphasis)
                                Text("${receiver.host}:${receiver.port}", color = theme.colors.type.secondary)
                                ZsButton(
                                    text = "Use this TV",
                                    onClick = {
                                        scope.launch {
                                            loading = true
                                            runCatching { vm.fetchHello(receiver.host, receiver.port) }
                                                .onSuccess { hello ->
                                                    Log.d(TV_SYNC_UI_TAG, "discovered hello success host=${receiver.host} port=${receiver.port} hello=$hello")
                                                    selectedReceiver = receiver
                                                    pairSalt = hello.optString("salt")
                                                    pairSessionId = hello.optString("sessionId")
                                                    tvName = hello.optString("tvName", receiver.tvName)
                                                    page = TvSyncPhonePage.PAIR
                                                    status = null
                                                }
                                                .onFailure {
                                                    Log.w(TV_SYNC_UI_TAG, "discovered hello failed host=${receiver.host} port=${receiver.port} error=${it.message}", it)
                                                    status = it.message ?: "Could not reach that TV"
                                                }
                                            loading = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Manual IP", color = theme.colors.type.emphasis)
                    OutlinedTextField(value = manualHost, onValueChange = { manualHost = it.trim() }, label = { Text("TV IP address") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = manualPort, onValueChange = { manualPort = it.filter(Char::isDigit).take(5) }, label = { Text("TV port") }, modifier = Modifier.fillMaxWidth())
                    ZsButton(
                        text = "Connect manually",
                        onClick = {
                            scope.launch {
                                loading = true
                                Log.d(TV_SYNC_UI_TAG, "manual connect host=$manualHost port=$manualPort")
                                runCatching { vm.fetchHello(manualHost, manualPort.toInt()) }
                                    .onSuccess { hello ->
                                        Log.d(TV_SYNC_UI_TAG, "manual hello success host=$manualHost port=$manualPort hello=$hello")
                                        selectedReceiver = TvSyncDiscoveredReceiver(manualHost, manualHost, manualPort.toInt(), hello.optString("tvName", "ZStream TV"))
                                        pairSalt = hello.optString("salt")
                                        pairSessionId = hello.optString("sessionId")
                                        tvName = hello.optString("tvName", "ZStream TV")
                                        page = TvSyncPhonePage.PAIR
                                        status = null
                                    }
                                    .onFailure {
                                        Log.w(TV_SYNC_UI_TAG, "manual hello failed host=$manualHost port=$manualPort error=${it.message}", it)
                                        status = it.message ?: "Manual connection failed"
                                    }
                                loading = false
                            }
                        },
                        enabled = !loading && manualHost.isNotBlank() && manualPort.toIntOrNull() in 1..65535,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TvSyncPhonePage.PAIR -> {
                    Text("Pair with ${selectedReceiver?.tvName ?: tvName}", color = theme.colors.type.secondary)
                    OutlinedTextField(value = senderName, onValueChange = { senderName = it }, label = { Text("Phone/device name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = pairCode, onValueChange = { pairCode = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit code from TV") }, modifier = Modifier.fillMaxWidth())
                    ZsButton(
                        text = "Pair",
                        onClick = {
                            val receiver = selectedReceiver ?: return@ZsButton
                            scope.launch {
                                loading = true
                                Log.d(TV_SYNC_UI_TAG, "pair start host=${receiver.host} port=${receiver.port} codeLength=${pairCode.length}")
                                runCatching { vm.pair(receiver.host, receiver.port, pairCode, pairSalt, pairSessionId, senderName) }
                                    .onSuccess {
                                        Log.d(TV_SYNC_UI_TAG, "pair success host=${receiver.host} port=${receiver.port}")
                                        page = TvSyncPhonePage.INTEGRATIONS
                                        status = "Paired with ${receiver.tvName}"
                                    }
                                    .onFailure {
                                        Log.w(TV_SYNC_UI_TAG, "pair failed host=${receiver.host} port=${receiver.port} error=${it.message}", it)
                                        status = it.message ?: "Pairing failed"
                                    }
                                loading = false
                            }
                        },
                        enabled = !loading && pairCode.length == 6,
                        loading = loading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TvSyncPhonePage.INTEGRATIONS -> {
                    Text("Choose what to sync", color = theme.colors.type.secondary)
                    if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    IntegrationRow("TMDB", available["tmdb"], selected["tmdb"] == true) { selected["tmdb"] = it }
                    IntegrationRow("Trakt", available["trakt"], selected["trakt"] == true) { selected["trakt"] = it }
                    IntegrationRow("Febbox", available["febbox"], selected["febbox"] == true) { selected["febbox"] = it }
                    IntegrationRow("Debrid", available["debrid"], selected["debrid"] == true) { selected["debrid"] = it }
                    IntegrationRow("TheIntroDB", available["tidb"], selected["tidb"] == true) { selected["tidb"] = it }
                    IntegrationRow("Wyzie", available["wyzie"], selected["wyzie"] == true) { selected["wyzie"] = it }
                    ZsButton(text = "Continue", onClick = { page = TvSyncPhonePage.PASSPHRASE }, modifier = Modifier.fillMaxWidth())
                }
                TvSyncPhonePage.PASSPHRASE -> {
                    Text("Passphrase login", color = theme.colors.type.secondary)
                    Text("Choose Yes only if you want the TV to sign into your account as a new device.", color = theme.colors.type.secondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ZsButton(
                            text = if (!passphraseEnabled) "Yes" else "Yes selected",
                            onClick = { passphraseEnabled = true },
                            variant = if (passphraseEnabled) ZsButtonVariant.Primary else ZsButtonVariant.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                        ZsButton(
                            text = if (!passphraseEnabled) "No selected" else "No",
                            onClick = { passphraseEnabled = false; passphrase = "" },
                            variant = if (!passphraseEnabled) ZsButtonVariant.Primary else ZsButtonVariant.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (passphraseEnabled) {
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text("Enter your passphrase") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ZsButton(
                        text = "Review",
                        onClick = {
                            if (passphraseEnabled && passphrase.isBlank()) {
                                status = "Enter your passphrase before continuing"
                            } else {
                                page = if (passphraseEnabled) TvSyncPhonePage.ACCOUNT_DEVICE else TvSyncPhonePage.REVIEW
                                status = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TvSyncPhonePage.ACCOUNT_DEVICE -> {
                    Text("ZStream account device", color = theme.colors.type.secondary)
                    Text("This name identifies the new TV session in your ZStream account.", color = theme.colors.type.secondary)
                    OutlinedTextField(
                        value = accountDeviceName,
                        onValueChange = { accountDeviceName = it },
                        label = { Text("Account device name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ZsButton(
                        text = "Review",
                        onClick = {
                            if (accountDeviceName.isBlank()) status = "Enter an account device name"
                            else {
                                page = TvSyncPhonePage.REVIEW
                                status = null
                            }
                        },
                        enabled = accountDeviceName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TvSyncPhonePage.REVIEW -> {
                    val pendingSummary = buildList {
                        selected.filterValues { it }.keys.forEach { add(it) }
                        if (passphraseEnabled) add("passphrase")
                    }
                    val canSend = pendingSummary.isNotEmpty()
                    SyncInstructionCard("Review", "The TV will show one more review screen before anything is applied.")
                    Text("TV name: $tvName", color = theme.colors.type.text)
                    if (passphraseEnabled) Text("Account device: $accountDeviceName", color = theme.colors.type.text)
                    Text("Selected: ${pendingSummary.joinToString().ifBlank { "Nothing selected" }}", color = theme.colors.type.text)
                    ZsButton(
                        text = "Send to TV",
                        onClick = {
                            val receiver = selectedReceiver ?: return@ZsButton
                            scope.launch {
                                loading = true
                                Log.d(TV_SYNC_UI_TAG, "send start host=${receiver.host} port=${receiver.port} selected=${selected.filterValues { it }.keys} passphrase=$passphraseEnabled passphraseLength=${passphrase.length} leadingOrTrailingWhitespace=${passphrase != passphrase.trim()} accountDeviceName=$accountDeviceName")
                                runCatching {
                                    val payload = vm.buildPayload(tvName, selected.toMap(), passphrase.takeIf { passphraseEnabled }, accountDeviceName.takeIf { passphraseEnabled })
                                    vm.sendPayload(receiver.host, receiver.port, payload)
                                }.onSuccess {
                                    Log.d(TV_SYNC_UI_TAG, "send success host=${receiver.host} port=${receiver.port}")
                                    page = TvSyncPhonePage.RESULT
                                    status = "Sent to ${receiver.tvName}. Confirm on the TV."
                                }.onFailure {
                                    Log.w(TV_SYNC_UI_TAG, "send failed host=${receiver.host} port=${receiver.port} error=${it.message}", it)
                                    status = it.message ?: "Could not send to TV"
                                }
                                loading = false
                            }
                        },
                        loading = loading,
                        enabled = canSend && !loading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!canSend) {
                        Text("Choose at least one integration or enable passphrase login.", color = theme.colors.type.secondary)
                    }
                }
                TvSyncPhonePage.RESULT -> {
                    SyncInstructionCard("Sent to TV", "Review the request on the TV and confirm there. You can close this screen after the TV finishes.")
                }
            }
        }
    }
}

@Composable
private fun IntegrationRow(
    title: String,
    availability: Pair<Boolean, String>?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val theme = LocalZStreamTheme.current
    ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                Text(availability?.second ?: "Checking…", color = theme.colors.type.secondary, fontSize = 13.sp)
            }
            Checkbox(
                checked = checked,
                onCheckedChange = if (availability?.first == true) onCheckedChange else null,
                enabled = availability?.first == true,
            )
        }
    }
}

@Composable
private fun TvSyncReceiverScreen(
    nav: NavController,
    vm: TvSyncViewModel,
) {
    val theme = LocalZStreamTheme.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val state by vm.receiverState.collectAsStateWithLifecycle()
    var tvNameInput by remember(state.tvName) { mutableStateOf(state.tvName) }

    LaunchedEffect(Unit) {
        vm.startReceiver()
    }

    BackHandler {
        vm.stopReceiver()
        onBack()
    }

    Surface(color = theme.colors.background.main, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZsIconButton(
                    onClick = {
                        vm.stopReceiver()
                        onBack()
                    },
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    variant = ZsIconButtonVariant.Ghost,
                )
                Spacer(Modifier.width(8.dp))
                Text("Sync from phone", color = theme.colors.type.emphasis, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            SyncInstructionCard(
                "Instructions",
                if (state.pendingPayload == null)
                    "Open ZStream on your phone, choose Sync to TV, then enter the 6-digit code shown here."
                else
                    "Your phone sent a sync request. Review it before applying anything."
            )
            OutlinedTextField(
                value = tvNameInput,
                onValueChange = { tvNameInput = it },
                label = { Text("TV name") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(showKeyboardOnFocus = false),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { vm.renameTv(tvNameInput) }) { Text("Save TV name") }
            if (state.pendingPayload == null) {
                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Pairing code", color = theme.colors.type.secondary)
                        Text(state.code, color = theme.colors.type.emphasis, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                        Text(state.status, color = theme.colors.type.text)
                    }
                }
                state.localIps.forEach { ip ->
                    Text("Manual IP: $ip:${state.port}", color = theme.colors.type.secondary)
                }
            } else {
                val payload = state.pendingPayload ?: return@Column
                Text("Review incoming sync", color = theme.colors.type.emphasis)
                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("TV device name: ${payload.tvName}", color = theme.colors.type.text)
                        payload.summaryLines().forEach { Text("• $it", color = theme.colors.type.secondary) }
                    }
                }
                Text(state.status, color = theme.colors.type.secondary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZsButton(
                        text = "Apply",
                        onClick = { vm.applyPending() },
                        leadingIcon = Icons.Default.Check,
                        modifier = Modifier.weight(1f),
                    )
                    ZsButton(
                        text = "Reject",
                        onClick = { vm.rejectPending() },
                        variant = ZsButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncInstructionCard(title: String, body: String) {
    val theme = LocalZStreamTheme.current
    ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
            Text(body, color = theme.colors.type.secondary)
        }
    }
}

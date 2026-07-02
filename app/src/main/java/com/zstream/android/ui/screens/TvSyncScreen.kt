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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.zstream.android.data.TraktRepository
import com.zstream.android.data.AccountRepository
import com.zstream.android.data.local.preferences.SettingsPreferences

private enum class TvSyncPhonePage { INTRO, DISCOVER, PAIR, INTEGRATIONS, PASSPHRASE, ACCOUNT_DEVICE, REVIEW, RESULT }
private const val TV_SYNC_UI_TAG = "TvSyncUI"

@HiltViewModel
class TvSyncViewModel @Inject constructor(
    private val repo: TvSyncRepository,
    settingsPreferences: SettingsPreferences,
    private val traktRepository: TraktRepository,
    accountRepository: AccountRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
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
        passkeyKeySeed: String? = null,
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
            passkeyKeySeed = passkeyKeySeed,
        )
    }

    /** Authenticates the phone's passkey and returns the Base64Url-encoded 32-byte seed.
     *  The TV can call CryptoUtils.keysFromSeed(decode(seed)) to derive the same Ed25519
     *  key pair and run the normal challenge-login against the backend. */
    suspend fun resolvePasskeySeed(): String {
        val credId = com.zstream.android.data.CryptoUtils.authenticatePasskey(appContext)
        val seed = com.zstream.android.data.CryptoUtils.pbkdf2(credId)
        return with(com.zstream.android.data.CryptoUtils) { seed.toBase64Url() }
    }

    suspend fun sendPayload(host: String, port: Int, payload: TvSyncPayload) = repo.sendPayload(host, port, payload)
    suspend fun waitForTransferResult(host: String, port: Int) = repo.waitForTransferResult(host, port)
    fun startReceiver() = viewModelScope.launch { repo.startReceiver() }
    fun stopReceiver() = repo.stopReceiver()
    fun renameTv(name: String) = viewModelScope.launch { repo.setDefaultTvName(name) }

    /** Runs apply on viewModelScope so it survives navigation away from the screen. */
    fun applyPendingAndNotify(onDone: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = repo.applyPendingPayload()
            // Wait for the phone to poll /status and receive the result
            result.getOrNull()?.let { repo.waitForDecisionDelivery(it) }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone(result) }
        }
    }

    suspend fun applyPending() = repo.applyPendingPayload()
    fun rejectPending() = repo.rejectPendingPayload()
    suspend fun waitForDecisionDelivery(token: String) = repo.waitForDecisionDelivery(token)}

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
    val accountSession by vm.accountSession.collectAsStateWithLifecycle()

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
    var passphraseEnabled by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var accountDeviceName by remember { mutableStateOf("ZStream TV") }
    var passkeyKeySeed by remember { mutableStateOf<String?>(null) }
    var passkeyDeclined by remember { mutableStateOf(false) }
    var transferResult by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val available = remember { mutableStateMapOf<String, Pair<Boolean, String>>() }

    LaunchedEffect(accountSession?.usesPasskey) {
        if (accountSession?.usesPasskey == true) {
            passphraseEnabled = false
            passphrase = ""
        } else {
            passkeyKeySeed = null
            passkeyDeclined = false
        }
    }

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

    fun goBack() {
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
    BackHandler { goBack() }

    Box(Modifier.fillMaxSize().background(theme.colors.background.main)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZsIconButton(
                    onClick = { goBack() },
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    variant = ZsIconButtonVariant.Ghost,
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Sync to TV", color = theme.colors.type.emphasis, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when (page) {
                            TvSyncPhonePage.INTRO -> "Set up your TV without typing on it"
                            TvSyncPhonePage.DISCOVER -> "Find your TV"
                            TvSyncPhonePage.PAIR -> "Confirm pairing"
                            TvSyncPhonePage.INTEGRATIONS -> "Choose integrations"
                            TvSyncPhonePage.PASSPHRASE -> "Choose account sign-in"
                            TvSyncPhonePage.ACCOUNT_DEVICE -> "Name the account session"
                            TvSyncPhonePage.REVIEW -> "Review before sending"
                            TvSyncPhonePage.RESULT -> "Ready for TV confirmation"
                        },
                        color = theme.colors.type.secondary,
                        fontSize = 13.sp,
                    )
                }
            }
            status?.let {
                ZsStatusBanner(
                    message = it,
                    variant = ZsStatusBannerVariant.Info,
                )
            }
            when (page) {
                TvSyncPhonePage.INTRO -> {
                    SyncInstructionCard(
                        title = "On your TV",
                        body = "Open ZStream → Sync from phone, then leave the pairing code on screen."
                    )
                    ZsButton(
                        text = "Find TV",
                        onClick = { page = TvSyncPhonePage.DISCOVER },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TvSyncPhonePage.DISCOVER -> {
                    SyncInstructionCard("Same network", "Scan first. If your TV does not appear, use the IP and port shown on its screen.")
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
                    SyncInstructionCard("${selectedReceiver?.tvName ?: tvName}", "Enter the 6-digit code shown on this TV.")
                    OutlinedTextField(value = pairCode, onValueChange = { pairCode = it.filter(Char::isDigit).take(6) }, label = { Text("Pairing code") }, modifier = Modifier.fillMaxWidth())
                    ZsButton(
                        text = "Pair",
                        onClick = {
                            val receiver = selectedReceiver ?: return@ZsButton
                            scope.launch {
                                loading = true
                                Log.d(TV_SYNC_UI_TAG, "pair start host=${receiver.host} port=${receiver.port} codeLength=${pairCode.length}")
                                runCatching { vm.pair(receiver.host, receiver.port, pairCode, pairSalt, pairSessionId, "Android Phone") }
                                    .onSuccess {
                                        Log.d(TV_SYNC_UI_TAG, "pair success host=${receiver.host} port=${receiver.port}")
                                        page = TvSyncPhonePage.INTEGRATIONS
                                        status = null
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
                    Text("Only available, validated items can be selected.", color = theme.colors.type.secondary)
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
                    if (accountSession?.usesPasskey == true) {
                        Text("Sign this TV into your ZStream account?", color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                        Text("Your phone will prompt for your passkey. The TV will use it to create its own login session.", color = theme.colors.type.secondary)
                        when {
                            passkeyKeySeed != null -> {
                                ZsStatusBanner(message = "Passkey authenticated — TV will sign in on apply.", variant = ZsStatusBannerVariant.Info)
                                OutlinedTextField(
                                    value = accountDeviceName,
                                    onValueChange = { accountDeviceName = it },
                                    label = { Text("Account device name for the TV") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                ZsButton(
                                    text = "Change to: No",
                                    onClick = { passkeyKeySeed = null; passkeyDeclined = true },
                                    variant = ZsButtonVariant.Secondary,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            passkeyDeclined -> {
                                ZsStatusBanner(message = "Account sign-in skipped — integrations only.", variant = ZsStatusBannerVariant.Info)
                                ZsButton(
                                    text = "Change to: Yes, use passkey",
                                    onClick = { passkeyDeclined = false },
                                    variant = ZsButtonVariant.Secondary,
                                    leadingIcon = Icons.Default.Fingerprint,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            else -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ZsButton(
                                        text = "Yes, use passkey",
                                        onClick = {
                                            scope.launch {
                                                loading = true
                                                status = null
                                                runCatching { vm.resolvePasskeySeed() }
                                                    .onSuccess { seed -> passkeyKeySeed = seed; status = null }
                                                    .onFailure { status = it.message ?: "Passkey authentication failed" }
                                                loading = false
                                            }
                                        },
                                        loading = loading,
                                        variant = ZsButtonVariant.Primary,
                                        leadingIcon = Icons.Default.Fingerprint,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ZsButton(
                                        text = "No",
                                        onClick = { passkeyDeclined = true },
                                        variant = ZsButtonVariant.Secondary,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    } else {
                        Text("Sign this TV into your ZStream account?", color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                        Text("This creates a separate account session for the TV. Integration sync works without it.", color = theme.colors.type.secondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ZsButton(
                                text = if (passphraseEnabled) "Yes, selected" else "Yes",
                                onClick = { passphraseEnabled = true },
                                variant = if (passphraseEnabled) ZsButtonVariant.Primary else ZsButtonVariant.Secondary,
                                modifier = Modifier.weight(1f),
                            )
                            ZsButton(
                                text = if (!passphraseEnabled) "No, selected" else "No",
                                onClick = { passphraseEnabled = false; passphrase = "" },
                                variant = if (!passphraseEnabled) ZsButtonVariant.Primary else ZsButtonVariant.Secondary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (passphraseEnabled) {
                            OutlinedTextField(
                                value = passphrase,
                                onValueChange = { passphrase = it },
                                label = { Text("ZStream passphrase") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    ZsButton(
                        text = if (passphraseEnabled) "Continue" else "Review",
                        onClick = {
                            if (passphraseEnabled && passphrase.isBlank()) {
                                status = "Enter your passphrase before continuing"
                            } else if (passkeyKeySeed != null && accountDeviceName.isBlank()) {
                                status = "Enter a device name for the TV account session"
                            } else {
                                page = if (passphraseEnabled) TvSyncPhonePage.ACCOUNT_DEVICE else TvSyncPhonePage.REVIEW
                                status = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TvSyncPhonePage.ACCOUNT_DEVICE -> {
                    SyncInstructionCard("Account device name", "Use a recognizable name such as Living Room TV. You will see it in your ZStream account's device list.")
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
                        listOf(
                            "tmdb" to "TMDB API key",
                            "trakt" to "Trakt session",
                            "febbox" to "Febbox key",
                            "debrid" to "Debrid token",
                            "tidb" to "TheIntroDB key",
                            "wyzie" to "Wyzie key",
                        ).filter { selected[it.first] == true }.forEach { add(it.second) }
                        if (passphraseEnabled) add("ZStream account sign-in")
                        if (passkeyKeySeed != null) add("ZStream account sign-in via passkey")
                    }
                    val canSend = pendingSummary.isNotEmpty()
                    SyncInstructionCard("Nothing changes yet", "After sending, review and apply this request on the TV.")
                    Text("TV: $tvName", color = theme.colors.type.text)
                    if (passphraseEnabled) Text("Account device: $accountDeviceName", color = theme.colors.type.text)
                    pendingSummary.forEach { Text("• $it", color = theme.colors.type.text) }
                    ZsButton(
                        text = "Send to TV",
                        onClick = {
                            val receiver = selectedReceiver ?: return@ZsButton
                            scope.launch {
                                loading = true
                                Log.d(TV_SYNC_UI_TAG, "send start host=${receiver.host} port=${receiver.port} selected=${selected.filterValues { it }.keys} passphrase=$passphraseEnabled passphraseLength=${passphrase.length} leadingOrTrailingWhitespace=${passphrase != passphrase.trim()} accountDeviceName=$accountDeviceName")
                                runCatching {
                                    val payload = vm.buildPayload(tvName, selected.toMap(), passphrase.takeIf { passphraseEnabled }, accountDeviceName.takeIf { passphraseEnabled || passkeyKeySeed != null }, passkeyKeySeed)
                                    vm.sendPayload(receiver.host, receiver.port, payload)
                                }.onSuccess {
                                    Log.d(TV_SYNC_UI_TAG, "send success host=${receiver.host} port=${receiver.port}")
                                    page = TvSyncPhonePage.RESULT
                                    status = null
                                    loading = false
                                    runCatching { vm.waitForTransferResult(receiver.host, receiver.port) }
                                        .onSuccess { transferResult = it }
                                        .onFailure { status = it.message ?: "Could not receive the TV response" }
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
                    when (transferResult) {
                        "accepted" -> SyncResult(true) {
                            transferResult = null
                            status = null
                            page = TvSyncPhonePage.INTRO
                        }
                        "rejected" -> SyncResult(false) {
                            transferResult = null
                            status = null
                            page = TvSyncPhonePage.INTRO
                        }
                        else -> SyncInstructionCard("Waiting for ${selectedReceiver?.tvName ?: tvName}", "Accept or reject the request on your TV.")
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncResult(accepted: Boolean, onRestart: () -> Unit) {
    val theme = LocalZStreamTheme.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(
            imageVector = if (accepted) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = null,
            tint = if (accepted) theme.colors.type.success else theme.colors.type.danger,
            modifier = Modifier.size(112.dp),
        )
        Text(
            if (accepted) "Sync accepted" else "Sync rejected",
            color = theme.colors.type.emphasis,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (accepted) "Your TV applied the selected items." else "Your TV did not apply this request.",
            color = theme.colors.type.secondary,
        )
        ZsButton(text = "Start another sync", onClick = onRestart)
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
    var applying by remember { mutableStateOf(false) }

    fun returnHome() {
        vm.stopReceiver()
        nav.navigate("home") {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

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
                if (state.pendingPayload == null) "Connect your phone" else "Confirm this request",
                if (state.pendingPayload == null)
                    "On your phone, open Settings → Sync to TV and enter the code below."
                else
                    "Nothing is changed until you choose Apply."
            )
            if (state.pendingPayload == null) {
                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Enter this code on your phone", color = theme.colors.type.secondary)
                        Text(state.code, color = theme.colors.type.emphasis, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                        Text(state.status, color = theme.colors.type.text)
                    }
                }
                if (state.localIps.isNotEmpty()) {
                    Text("Manual connection: ${state.localIps.joinToString { "$it:${state.port}" }}", color = theme.colors.type.secondary)
                }
                OutlinedTextField(
                    value = tvNameInput,
                    onValueChange = { tvNameInput = it },
                    label = { Text("Local network TV name") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(showKeyboardOnFocus = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                ZsButton(
                    text = "Save TV name",
                    onClick = { vm.renameTv(tvNameInput) },
                    enabled = tvNameInput.isNotBlank() && tvNameInput.trim() != state.tvName,
                    variant = ZsButtonVariant.Secondary,
                    modifier = Modifier,
                )
            } else {
                val payload = state.pendingPayload ?: return@Column
                Text("Your phone wants to sync", color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        payload.accountDeviceName?.let { Text("Account device: $it", color = theme.colors.type.text) }
                        payload.summaryLines().forEach { Text("• $it", color = theme.colors.type.secondary) }
                    }
                }
                ZsStatusBanner(message = state.status, variant = ZsStatusBannerVariant.Info)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZsButton(
                        text = "Apply sync",
                        onClick = {
                            applying = true
                            vm.applyPendingAndNotify { result ->
                                applying = false
                                if (result.isSuccess) returnHome()
                            }
                        },
                        leadingIcon = Icons.Default.Check,
                        loading = applying,
                        modifier = Modifier.weight(1f),
                    )
                    ZsButton(
                        text = "Reject",
                        onClick = {
                            val token = vm.rejectPending()
                            if (token != null) scope.launch {
                                vm.waitForDecisionDelivery(token)
                                returnHome()
                            }
                        },
                        enabled = !applying,
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

package com.zstream.android.ui.screens

import android.util.Log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.zstream.android.data.TraktSessionExport
import com.zstream.android.R
import com.zstream.android.data.TraktState
import com.zstream.android.data.TvSyncDiscoveredReceiver
import com.zstream.android.data.TvSyncPayload
import com.zstream.android.data.TvSyncReceiverState
import com.zstream.android.data.TvSyncRepository
import com.zstream.android.data.PasskeySessionTransfer
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
    private val accountRepository: AccountRepository,
) : ViewModel() {
    val receiverState: StateFlow<TvSyncReceiverState> = repo.receiverState
    val pairedTvs: StateFlow<List<com.zstream.android.data.PairedTv>> = repo.pairedTvs
    val activeReceiverState: StateFlow<TvSyncDiscoveredReceiver?> = repo.activeReceiver
    suspend fun renamePairedTv(id: String, nickname: String) = repo.renamePairedTv(id, nickname)
    suspend fun forgetPairedTv(id: String) = repo.forgetPairedTv(id)
    suspend fun pingTv(host: String, port: Int): Boolean = repo.pingTv(host, port)
    suspend fun reconcilePairedHosts(discovered: List<TvSyncDiscoveredReceiver>) = repo.reconcilePairedHosts(discovered)
    fun setActiveReceiver(tv: com.zstream.android.data.PairedTv) = repo.setActiveReceiver(tv)
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
    suspend fun pair(host: String, port: Int, code: String, salt: String, sessionId: String, phoneName: String, tvName: String, tvDeviceId: String?) =
        repo.pair(host, port, code, salt, sessionId, phoneName, tvName, tvDeviceId)

    suspend fun buildPayload(
        tvName: String,
        selected: Map<String, Boolean>,
        passphrase: String?,
        accountDeviceName: String?,
        passkeySession: PasskeySessionTransfer? = null,
    ): TvSyncPayload {
        val current = settings.value
        val traktSession = if (selected["trakt"] == true) traktRepository.exportSession() else null
        return TvSyncPayload(
            tvName = tvName,
            tmdbApiKey = current.tmdbApiKey.takeIf { selected["tmdb"] == true },
            febboxKey = current.febboxKey.takeIf { selected["febbox"] == true },
            febboxKeys = current.febboxKeys.takeIf { selected["febbox"] == true },
            debridToken = current.debridToken.takeIf { selected["debrid"] == true },
            debridService = current.debridService.takeIf { selected["debrid"] == true },
            tidbKey = current.tidbKey.takeIf { selected["tidb"] == true },
            wyzieKey = current.wyzieKey.takeIf { selected["wyzie"] == true },
            traktSession = traktSession,
            passphrase = passphrase?.trim()?.takeIf(String::isNotBlank),
            accountDeviceName = accountDeviceName?.trim()?.takeIf(String::isNotBlank),
            passkeySession = passkeySession,
        )
    }

    /** Authenticates the phone's passkey against the backend and returns the resulting session
     *  (token + user id + nickname) for the TV to adopt. Real WebAuthn keys can't leave the
     *  authenticator, so we forward the issued session rather than a derivable seed.
     *  Needs an Activity-based context -- CredentialManager can't launch its selector UI off appContext. */
    suspend fun resolvePasskeySession(activityContext: android.content.Context, deviceName: String): PasskeySessionTransfer {
        val session = accountRepository.passkeyLoginForTransfer(activityContext, deviceName)
        return PasskeySessionTransfer(session.token, session.userId, session.nickname)
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
) {
    val isTv = LocalIsTv.current
    if (isTv) {
        TvSyncReceiverScreen(nav, vm)
    } else {
        TvManageScreen(nav, vm)
    }
}

private data class TvOnlineStatus(val checking: Boolean, val online: Boolean)

@Composable
private fun localizedSenderStatus(status: String): String = when {
    status == "TV receiver is unavailable" -> stringResource(R.string.tv_sync_receiver_unavailable)
    status == "Phone receiver is unavailable" -> stringResource(R.string.tv_sync_phone_receiver_unavailable)
    status == "Pair with the TV first" -> stringResource(R.string.tv_sync_pair_first)
    status == "Pairing failed" -> stringResource(R.string.tv_sync_pairing_failed)
    status == "Wrong pairing code" || status.contains("\"error\":\"Wrong pairing code\"") -> stringResource(R.string.tv_sync_wrong_pairing_code)
    status == "Receiver is offline" || status.contains("\"error\":\"Receiver is offline\"") -> stringResource(R.string.tv_sync_receiver_offline)
    status == "Pair again" || status.contains("\"error\":\"Pair again\"") -> stringResource(R.string.tv_sync_pair_again)
    status == "Transfer failed" || status.contains("\"error\":\"Transfer failed\"") -> stringResource(R.string.tv_sync_transfer_failed)
    status == "Timed out waiting for the TV" -> stringResource(R.string.tv_sync_timed_out_waiting)
    else -> status
}

@Composable
private fun TvManageScreen(
    nav: NavController,
    vm: TvSyncViewModel,
) {
    val theme = LocalZStreamTheme.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val pairedTvs by vm.pairedTvs.collectAsStateWithLifecycle()
    val activeReceiver by vm.activeReceiverState.collectAsStateWithLifecycle()

    var scanning by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<List<TvSyncDiscoveredReceiver>>(emptyList()) }
    var hasScanned by remember { mutableStateOf(false) }
    val statuses = remember { mutableStateMapOf<String, TvOnlineStatus>() }
    val renaming = remember { mutableStateMapOf<String, String>() }

    fun keyOf(host: String, port: Int) = "$host:$port"

    fun pingAllPaired(list: List<com.zstream.android.data.PairedTv> = pairedTvs) {
        list.forEach { tv ->
            val key = keyOf(tv.host, tv.port)
            statuses[key] = TvOnlineStatus(checking = true, online = statuses[key]?.online == true)
            scope.launch {
                val online = vm.pingTv(tv.host, tv.port)
                statuses[key] = TvOnlineStatus(checking = false, online = online)
            }
        }
    }

    fun refresh() {
        scope.launch {
            scanning = true
            discovered = vm.discoverReceivers()
            // A paired TV's port changes every time its app restarts -- catch that before pinging
            // so a merely-restarted TV shows Online instead of looking permanently lost. Read
            // pairedTvs.value directly (not the Compose snapshot) since reconcile just mutated it
            // and the snapshot won't reflect that until the next recomposition.
            vm.reconcilePairedHosts(discovered)
            pingAllPaired(vm.pairedTvs.value)
            hasScanned = true
            scanning = false
        }
    }

    LaunchedEffect(Unit) { pingAllPaired() }

    val pairedKeys = remember(pairedTvs) { pairedTvs.map { keyOf(it.host, it.port) }.toSet() }
    val pairedDeviceIds = remember(pairedTvs) { pairedTvs.mapNotNull { it.tvDeviceId }.toSet() }
    val unpairedDiscovered = discovered.filterNot {
        keyOf(it.host, it.port) in pairedKeys || (it.tvDeviceId != null && it.tvDeviceId in pairedDeviceIds)
    }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(theme.colors.background.main)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZsIconButton(onClick = onBack, icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.tv_sync_back), variant = ZsIconButtonVariant.Ghost)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tv_sync_manage_tvs), color = theme.colors.type.emphasis, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.tv_sync_manage_description), color = theme.colors.type.secondary, fontSize = 13.sp)
                }
                ZsIconButton(
                    onClick = { refresh() },
                    icon = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.tv_sync_refresh),
                    variant = ZsIconButtonVariant.Ghost,
                )
            }

            if (pairedTvs.isEmpty() && !hasScanned) {
                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Tv, null, tint = theme.colors.global.accentA, modifier = Modifier.size(48.dp))
                        Text(stringResource(R.string.tv_sync_no_tvs_yet), color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(
                            stringResource(R.string.tv_sync_no_tvs_instructions),
                            color = theme.colors.type.secondary,
                            fontSize = 13.sp,
                        )
                        ZsButton(text = stringResource(R.string.tv_sync_scan_for_tvs), leadingIcon = Icons.Default.Refresh, onClick = { refresh() }, loading = scanning)
                    }
                }
            }

            if (pairedTvs.isNotEmpty()) {
                Text(stringResource(R.string.tv_sync_paired), color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                pairedTvs.forEach { tv ->
                    key(tv.id) {
                        val status = statuses[keyOf(tv.host, tv.port)]
                        val editingName = renaming[tv.id]
                        val isActiveTv = (tv.tvDeviceId != null && activeReceiver?.tvDeviceId == tv.tvDeviceId) || (activeReceiver?.host == tv.host && activeReceiver?.port == tv.port)
                        ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        Modifier
                                            .size(9.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                when {
                                                    status?.checking == true -> theme.colors.type.dimmed
                                                    status?.online == true -> theme.colors.type.success
                                                    else -> theme.colors.type.danger
                                                }
                                            )
                                    )
                                    Text(
                                        when {
                                            status?.checking == true -> stringResource(R.string.tv_sync_checking)
                                            status?.online == true -> stringResource(R.string.tv_sync_online)
                                            else -> stringResource(R.string.tv_sync_offline)
                                        },
                                        color = theme.colors.type.secondary,
                                        fontSize = 12.sp,
                                    )
                                    if (isActiveTv) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.tv_sync_default_for_cast), color = theme.colors.global.accentA, fontSize = 12.sp)
                                    }
                                }
                                if (editingName != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = editingName,
                                            onValueChange = { renaming[tv.id] = it },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        ZsIconButton(
                                            onClick = {
                                                scope.launch { vm.renamePairedTv(tv.id, editingName) }
                                                renaming.remove(tv.id)
                                            },
                                            icon = Icons.Default.Check,
                                            contentDescription = stringResource(R.string.tv_sync_save_name),
                                            variant = ZsIconButtonVariant.Ghost,
                                        )
                                        ZsIconButton(
                                            onClick = { renaming.remove(tv.id) },
                                            icon = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.tv_sync_cancel),
                                            variant = ZsIconButtonVariant.Ghost,
                                        )
                                    }
                                } else {
                                    Text(
                                        tv.nickname,
                                        color = theme.colors.type.emphasis,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        modifier = Modifier.clickable { renaming[tv.id] = tv.nickname },
                                    )
                                }
                                Text("${tv.host}:${tv.port}", color = theme.colors.type.secondary, fontSize = 12.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ZsButton(
                                        text = stringResource(R.string.tv_sync_sync),
                                        variant = ZsButtonVariant.Secondary,
                                        onClick = { nav.navigate("tvSyncPair?tvId=${tv.id}") },
                                        modifier = Modifier.weight(1f),
                                        buttonModifier = Modifier.fillMaxWidth(),
                                    )
                                    ZsButton(
                                        text = stringResource(R.string.tv_sync_update),
                                        variant = ZsButtonVariant.Secondary,
                                        onClick = { nav.navigate("tvInstaller") },
                                        modifier = Modifier.weight(1f),
                                        buttonModifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ZsButton(
                                        text = stringResource(if (isActiveTv) R.string.tv_sync_default_set else R.string.tv_sync_make_default),
                                        variant = ZsButtonVariant.Secondary,
                                        enabled = !isActiveTv,
                                        onClick = { vm.setActiveReceiver(tv) },
                                        modifier = Modifier.weight(1f),
                                        buttonModifier = Modifier.fillMaxWidth(),
                                    )
                                    ZsButton(
                                        text = stringResource(R.string.tv_sync_forget),
                                        variant = ZsButtonVariant.Secondary,
                                        onClick = { scope.launch { vm.forgetPairedTv(tv.id) } },
                                        modifier = Modifier.weight(1f),
                                        buttonModifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (hasScanned) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.tv_sync_nearby), color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (unpairedDiscovered.isEmpty()) {
                    Text(
                        stringResource(if (scanning) R.string.tv_sync_scanning else R.string.tv_sync_no_unpaired_tvs),
                        color = theme.colors.type.secondary,
                        fontSize = 13.sp,
                    )
                } else {
                    unpairedDiscovered.forEach { receiver ->
                        ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(receiver.tvName, color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                                    Text("${receiver.host}:${receiver.port}", color = theme.colors.type.secondary, fontSize = 12.sp)
                                }
                                ZsButton(
                                    text = stringResource(R.string.tv_sync_pair),
                                    onClick = {
                                        nav.navigate("tvSyncPair?host=${receiver.host}&port=${receiver.port}&tvName=${java.net.URLEncoder.encode(receiver.tvName, "UTF-8")}")
                                    },
                                )
                            }
                        }
                    }
                }
            }

            ZsButton(
                text = stringResource(R.string.tv_sync_add_tv_manually),
                variant = ZsButtonVariant.Secondary,
                leadingIcon = Icons.Default.Fingerprint,
                onClick = { nav.navigate("tvSyncPair") },
                modifier = Modifier.fillMaxWidth(),
                buttonModifier = Modifier.fillMaxWidth(),
            )
        }
        ZsIconButton(
            onClick = { nav.navigate("tvInstaller") },
            icon = Icons.Default.Add,
            contentDescription = stringResource(R.string.tv_sync_install_zstream_on_tv),
            containerSize = 56.dp,
            iconSize = 28.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(24.dp),
        )
    }
}

@Composable
fun TvSyncPairScreen(
    nav: NavController,
    vm: TvSyncViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel(),
) {
    val args = nav.currentBackStackEntry?.arguments
    val tvId = args?.getString("tvId")?.takeIf { it.isNotBlank() }
    val host = args?.getString("host")?.takeIf { it.isNotBlank() }
    val port = args?.getInt("port") ?: 0
    val discoveredTvName = args?.getString("tvName")?.takeIf { it.isNotBlank() }
    val defaultTvName = stringResource(R.string.tv_sync_default_tv_name)
    val presetDiscovered = if (host != null && port > 0) {
        TvSyncDiscoveredReceiver(serviceName = discoveredTvName ?: host, host = host, port = port, tvName = discoveredTvName ?: defaultTvName)
    } else null
    TvSyncSenderScreen(nav, vm, settingsVm, presetTvId = tvId, presetDiscoveredReceiver = presetDiscovered)
}

@Composable
private fun TvSyncSenderScreen(
    nav: NavController,
    vm: TvSyncViewModel,
    settingsVm: SettingsViewModel,
    presetTvId: String? = null,
    presetDiscoveredReceiver: TvSyncDiscoveredReceiver? = null,
) {
    val theme = LocalZStreamTheme.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val trakt by vm.traktState.collectAsStateWithLifecycle()
    val accountSession by vm.accountSession.collectAsStateWithLifecycle()
    val defaultTvName = stringResource(R.string.tv_sync_default_tv_name)
    val defaultPhoneName = stringResource(R.string.tv_sync_default_phone_name)

    var page by remember { mutableStateOf(TvSyncPhonePage.INTRO) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<List<TvSyncDiscoveredReceiver>>(emptyList()) }
    var selectedReceiver by remember { mutableStateOf<TvSyncDiscoveredReceiver?>(null) }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("") }
    var tvName by remember { mutableStateOf(defaultTvName) }
    var pairCode by remember { mutableStateOf("") }
    var pairSalt by remember { mutableStateOf("") }
    var pairSessionId by remember { mutableStateOf("") }
    var passphraseEnabled by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var accountDeviceName by remember { mutableStateOf(defaultTvName) }
    var passkeySession by remember { mutableStateOf<PasskeySessionTransfer?>(null) }
    var passkeyDeclined by remember { mutableStateOf(false) }
    var transferResult by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val available = remember { mutableStateMapOf<String, Pair<Boolean, String>>() }
    val pairedTvs by vm.pairedTvs.collectAsStateWithLifecycle()

    // Entered from Manage TVs with an already-paired device -- skip discovery/pairing entirely
    // and jump straight to picking what to sync, since the token/secret are already live.
    LaunchedEffect(presetTvId, pairedTvs) {
        if (presetTvId == null || selectedReceiver != null) return@LaunchedEffect
        val preset = pairedTvs.find { it.id == presetTvId } ?: return@LaunchedEffect
        selectedReceiver = TvSyncDiscoveredReceiver(serviceName = preset.tvName, host = preset.host, port = preset.port, tvName = preset.nickname, tvDeviceId = preset.tvDeviceId)
        tvName = preset.nickname
        page = TvSyncPhonePage.INTEGRATIONS
    }

    // Entered from Manage TVs' "nearby" list -- fetch its pairing salt/session and go straight to
    // entering the code, skipping the discovery scan since we already know where it is.
    LaunchedEffect(presetDiscoveredReceiver) {
        val receiver = presetDiscoveredReceiver ?: return@LaunchedEffect
        if (selectedReceiver != null) return@LaunchedEffect
        loading = true
        runCatching { vm.fetchHello(receiver.host, receiver.port) }
            .onSuccess { hello ->
                selectedReceiver = receiver.copy(tvDeviceId = hello.optString("tvDeviceId").takeIf { it.isNotBlank() } ?: receiver.tvDeviceId)
                pairSalt = hello.optString("salt")
                pairSessionId = hello.optString("sessionId")
                tvName = hello.optString("tvName", receiver.tvName)
                page = TvSyncPhonePage.PAIR
            }
            .onFailure { status = it.message ?: context.getString(R.string.tv_sync_could_not_reach_tv) }
        loading = false
    }

    LaunchedEffect(accountSession?.usesPasskey) {
        if (accountSession?.usesPasskey == true) {
            passphraseEnabled = false
            passphrase = ""
        } else {
            passkeySession = null
            passkeyDeclined = false
        }
    }

    suspend fun refreshAvailability() {
        available["tmdb"] = settings.tmdbApiKey
            ?.takeIf { it.isNotBlank() }
            ?.let {
                settingsVm.validateTmdbKey(it).fold(
                    onSuccess = { ok -> ok to context.getString(if (ok) R.string.tv_sync_validated_on_phone else R.string.tv_sync_invalid_on_phone) },
                    onFailure = { false to (it.message ?: context.getString(R.string.tv_sync_validation_failed)) },
                )
            } ?: (false to context.getString(R.string.tv_sync_no_key_on_phone))
        available["tidb"] = settings.tidbKey
            ?.takeIf { it.isNotBlank() }
            ?.let {
                settingsVm.validateTidbKey(it).fold(
                    onSuccess = { ok -> ok to context.getString(if (ok) R.string.tv_sync_validated_on_phone else R.string.tv_sync_invalid_on_phone) },
                    onFailure = { false to (it.message ?: context.getString(R.string.tv_sync_validation_failed)) },
                )
            } ?: (false to context.getString(R.string.tv_sync_no_key_on_phone))
        available["wyzie"] = settings.wyzieKey
            ?.takeIf { it.isNotBlank() }
            ?.let {
                settingsVm.validateWyzieKey(it).fold(
                    onSuccess = { ok -> ok to context.getString(if (ok) R.string.tv_sync_validated_on_phone else R.string.tv_sync_invalid_on_phone) },
                    onFailure = { false to (it.message ?: context.getString(R.string.tv_sync_validation_failed)) },
                )
            } ?: (false to context.getString(R.string.tv_sync_no_key_on_phone))
        available["febbox"] = (!settings.febboxKey.isNullOrBlank()) to context.getString(if (settings.febboxKey.isNullOrBlank()) R.string.tv_sync_no_key_on_phone else R.string.tv_sync_saved_on_phone)
        available["debrid"] = (!settings.debridToken.isNullOrBlank()) to context.getString(if (settings.debridToken.isNullOrBlank()) R.string.tv_sync_no_token_on_phone else R.string.tv_sync_saved_on_phone)
        available["trakt"] = trakt.connected to context.getString(if (trakt.connected) R.string.tv_sync_connected_on_phone else R.string.tv_sync_not_connected_on_phone)
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
            TvSyncPhonePage.PAIR -> if (presetDiscoveredReceiver != null) onBack() else page = TvSyncPhonePage.DISCOVER
            TvSyncPhonePage.INTEGRATIONS -> if (presetTvId != null) onBack() else page = TvSyncPhonePage.PAIR
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
                    contentDescription = stringResource(R.string.tv_sync_back),
                    variant = ZsIconButtonVariant.Ghost,
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.tv_sync_to_tv), color = theme.colors.type.emphasis, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when (page) {
                            TvSyncPhonePage.INTRO -> stringResource(R.string.tv_sync_subtitle_intro)
                            TvSyncPhonePage.DISCOVER -> stringResource(R.string.tv_sync_subtitle_discover)
                            TvSyncPhonePage.PAIR -> stringResource(R.string.tv_sync_subtitle_pair)
                            TvSyncPhonePage.INTEGRATIONS -> stringResource(R.string.tv_sync_subtitle_integrations)
                            TvSyncPhonePage.PASSPHRASE -> stringResource(R.string.tv_sync_subtitle_account_sign_in)
                            TvSyncPhonePage.ACCOUNT_DEVICE -> stringResource(R.string.tv_sync_subtitle_account_device)
                            TvSyncPhonePage.REVIEW -> stringResource(R.string.tv_sync_subtitle_review)
                            TvSyncPhonePage.RESULT -> stringResource(R.string.tv_sync_subtitle_result)
                        },
                        color = theme.colors.type.secondary,
                        fontSize = 13.sp,
                    )
                }
            }
            status?.let {
                ZsStatusBanner(
                    message = localizedSenderStatus(it),
                    variant = ZsStatusBannerVariant.Info,
                )
            }
            when (page) {
                TvSyncPhonePage.INTRO -> {
                    SyncInstructionCard(
                        title = stringResource(R.string.tv_sync_on_your_tv),
                        body = stringResource(R.string.tv_sync_open_tv_instructions)
                    )
                    ZsButton(
                        text = stringResource(R.string.tv_sync_find_tv),
                        onClick = { page = TvSyncPhonePage.DISCOVER },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TvSyncPhonePage.DISCOVER -> {
                    SyncInstructionCard(stringResource(R.string.tv_sync_same_network), stringResource(R.string.tv_sync_same_network_description))
                    ZsButton(
                        text = stringResource(if (loading) R.string.tv_sync_scanning else R.string.tv_sync_scan_for_tvs),
                        onClick = {
                            scope.launch {
                                loading = true
                                status = null
                                Log.d(TV_SYNC_UI_TAG, "scan start")
                                discovered = vm.discoverReceivers()
                                Log.d(TV_SYNC_UI_TAG, "scan result count=${discovered.size} values=$discovered")
                                loading = false
                                if (discovered.isEmpty()) status = context.getString(R.string.tv_sync_no_tvs_found_manual)
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
                                    text = stringResource(R.string.tv_sync_use_this_tv),
                                    onClick = {
                                        scope.launch {
                                            loading = true
                                            runCatching { vm.fetchHello(receiver.host, receiver.port) }
                                                .onSuccess { hello ->
                                                    Log.d(TV_SYNC_UI_TAG, "discovered hello success host=${receiver.host} port=${receiver.port} hello=$hello")
                                                    selectedReceiver = receiver.copy(tvDeviceId = hello.optString("tvDeviceId").takeIf { it.isNotBlank() } ?: receiver.tvDeviceId)
                                                    pairSalt = hello.optString("salt")
                                                    pairSessionId = hello.optString("sessionId")
                                                    tvName = hello.optString("tvName", receiver.tvName)
                                                    page = TvSyncPhonePage.PAIR
                                                    status = null
                                                }
                                                .onFailure {
                                                    Log.w(TV_SYNC_UI_TAG, "discovered hello failed host=${receiver.host} port=${receiver.port} error=${it.message}", it)
                                                    status = it.message ?: context.getString(R.string.tv_sync_could_not_reach_tv)
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
                    Text(stringResource(R.string.tv_sync_manual_ip), color = theme.colors.type.emphasis)
                    OutlinedTextField(value = manualHost, onValueChange = { manualHost = it.trim() }, label = { Text(stringResource(R.string.tv_sync_tv_ip_address)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = manualPort, onValueChange = { manualPort = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.tv_sync_tv_port)) }, modifier = Modifier.fillMaxWidth())
                    ZsButton(
                        text = stringResource(R.string.tv_sync_connect_manually),
                        onClick = {
                            scope.launch {
                                loading = true
                                Log.d(TV_SYNC_UI_TAG, "manual connect host=$manualHost port=$manualPort")
                                runCatching { vm.fetchHello(manualHost, manualPort.toInt()) }
                                    .onSuccess { hello ->
                                        Log.d(TV_SYNC_UI_TAG, "manual hello success host=$manualHost port=$manualPort hello=$hello")
                                        selectedReceiver = TvSyncDiscoveredReceiver(
                                            serviceName = manualHost,
                                            host = manualHost,
                                            port = manualPort.toInt(),
                                            tvName = hello.optString("tvName", defaultTvName),
                                            tvDeviceId = hello.optString("tvDeviceId").takeIf { it.isNotBlank() },
                                        )
                                        pairSalt = hello.optString("salt")
                                        pairSessionId = hello.optString("sessionId")
                                        tvName = hello.optString("tvName", defaultTvName)
                                        page = TvSyncPhonePage.PAIR
                                        status = null
                                    }
                                    .onFailure {
                                        Log.w(TV_SYNC_UI_TAG, "manual hello failed host=$manualHost port=$manualPort error=${it.message}", it)
                                        status = it.message ?: context.getString(R.string.tv_sync_manual_connection_failed)
                                    }
                                loading = false
                            }
                        },
                        enabled = !loading && manualHost.isNotBlank() && manualPort.toIntOrNull() in 1..65535,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TvSyncPhonePage.PAIR -> {
                    SyncInstructionCard(selectedReceiver?.tvName ?: tvName, stringResource(R.string.tv_sync_enter_six_digit_code))
                    OutlinedTextField(value = pairCode, onValueChange = { pairCode = it.filter(Char::isDigit).take(6) }, label = { Text(stringResource(R.string.tv_sync_pairing_code)) }, modifier = Modifier.fillMaxWidth())
                    ZsButton(
                        text = stringResource(R.string.tv_sync_pair),
                        onClick = {
                            val receiver = selectedReceiver ?: return@ZsButton
                            scope.launch {
                                loading = true
                                Log.d(TV_SYNC_UI_TAG, "pair start host=${receiver.host} port=${receiver.port} codeLength=${pairCode.length}")
                                runCatching { vm.pair(receiver.host, receiver.port, pairCode, pairSalt, pairSessionId, defaultPhoneName, tvName, receiver.tvDeviceId) }
                                    .onSuccess {
                                        Log.d(TV_SYNC_UI_TAG, "pair success host=${receiver.host} port=${receiver.port}")
                                        page = TvSyncPhonePage.INTEGRATIONS
                                        status = null
                                    }
                                    .onFailure {
                                        Log.w(TV_SYNC_UI_TAG, "pair failed host=${receiver.host} port=${receiver.port} error=${it.message}", it)
                                        status = it.message ?: context.getString(R.string.tv_sync_pairing_failed)
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
                    Text(stringResource(R.string.tv_sync_only_validated_items), color = theme.colors.type.secondary)
                    if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    IntegrationRow("TMDB", available["tmdb"], selected["tmdb"] == true) { selected["tmdb"] = it }
                    IntegrationRow("Trakt", available["trakt"], selected["trakt"] == true) { selected["trakt"] = it }
                    IntegrationRow("Febbox", available["febbox"], selected["febbox"] == true) { selected["febbox"] = it }
                    IntegrationRow("Debrid", available["debrid"], selected["debrid"] == true) { selected["debrid"] = it }
                    IntegrationRow("TheIntroDB", available["tidb"], selected["tidb"] == true) { selected["tidb"] = it }
                    IntegrationRow("Wyzie", available["wyzie"], selected["wyzie"] == true) { selected["wyzie"] = it }
                    ZsButton(text = stringResource(R.string.tv_sync_continue), onClick = { page = TvSyncPhonePage.PASSPHRASE }, modifier = Modifier.fillMaxWidth())
                }
                TvSyncPhonePage.PASSPHRASE -> {
                    if (accountSession?.usesPasskey == true) {
                        Text(stringResource(R.string.tv_sync_sign_in_question), color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.tv_sync_passkey_description), color = theme.colors.type.secondary)
                        when {
                            passkeySession != null -> {
                                ZsStatusBanner(message = stringResource(R.string.tv_sync_passkey_authenticated), variant = ZsStatusBannerVariant.Info)
                                OutlinedTextField(
                                    value = accountDeviceName,
                                    onValueChange = { accountDeviceName = it },
                                    label = { Text(stringResource(R.string.tv_sync_account_device_name_for_tv)) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                ZsButton(
                                    text = stringResource(R.string.tv_sync_change_to_no),
                                    onClick = { passkeySession = null; passkeyDeclined = true },
                                    variant = ZsButtonVariant.Secondary,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            passkeyDeclined -> {
                                ZsStatusBanner(message = stringResource(R.string.tv_sync_account_sign_in_skipped), variant = ZsStatusBannerVariant.Info)
                                ZsButton(
                                    text = stringResource(R.string.tv_sync_change_to_passkey),
                                    onClick = { passkeyDeclined = false },
                                    variant = ZsButtonVariant.Secondary,
                                    leadingIcon = Icons.Default.Fingerprint,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            else -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ZsButton(
                                        text = stringResource(R.string.tv_sync_yes_use_passkey),
                                        onClick = {
                                            scope.launch {
                                                loading = true
                                                status = null
                                                runCatching { vm.resolvePasskeySession(context, accountDeviceName.ifBlank { tvName }) }
                                                    .onSuccess { session -> passkeySession = session; status = null }
                                                    .onFailure { status = it.message ?: context.getString(R.string.tv_sync_passkey_auth_failed) }
                                                loading = false
                                            }
                                        },
                                        loading = loading,
                                        variant = ZsButtonVariant.Primary,
                                        leadingIcon = Icons.Default.Fingerprint,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ZsButton(
                                        text = stringResource(R.string.tv_sync_no),
                                        onClick = { passkeyDeclined = true },
                                        variant = ZsButtonVariant.Secondary,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    } else {
                        Text(stringResource(R.string.tv_sync_sign_in_question), color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.tv_sync_account_session_description), color = theme.colors.type.secondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ZsButton(
                                text = stringResource(if (passphraseEnabled) R.string.tv_sync_yes_selected else R.string.tv_sync_yes),
                                onClick = { passphraseEnabled = true },
                                variant = if (passphraseEnabled) ZsButtonVariant.Primary else ZsButtonVariant.Secondary,
                                modifier = Modifier.weight(1f),
                            )
                            ZsButton(
                                text = stringResource(if (!passphraseEnabled) R.string.tv_sync_no_selected else R.string.tv_sync_no),
                                onClick = { passphraseEnabled = false; passphrase = "" },
                                variant = if (!passphraseEnabled) ZsButtonVariant.Primary else ZsButtonVariant.Secondary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (passphraseEnabled) {
                            OutlinedTextField(
                                value = passphrase,
                                onValueChange = { passphrase = it },
                                label = { Text(stringResource(R.string.tv_sync_zstream_passphrase)) },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    ZsButton(
                        text = stringResource(if (passphraseEnabled) R.string.tv_sync_continue else R.string.tv_sync_review),
                        onClick = {
                            if (passphraseEnabled && passphrase.isBlank()) {
                                status = context.getString(R.string.tv_sync_enter_passphrase)
                            } else if (passkeySession != null && accountDeviceName.isBlank()) {
                                status = context.getString(R.string.tv_sync_enter_tv_account_device_name)
                            } else {
                                page = if (passphraseEnabled) TvSyncPhonePage.ACCOUNT_DEVICE else TvSyncPhonePage.REVIEW
                                status = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TvSyncPhonePage.ACCOUNT_DEVICE -> {
                    SyncInstructionCard(stringResource(R.string.tv_sync_account_device_name), stringResource(R.string.tv_sync_account_device_name_description))
                    OutlinedTextField(
                        value = accountDeviceName,
                        onValueChange = { accountDeviceName = it },
                        label = { Text(stringResource(R.string.tv_sync_account_device_name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ZsButton(
                        text = stringResource(R.string.tv_sync_review),
                        onClick = {
                            if (accountDeviceName.isBlank()) status = context.getString(R.string.tv_sync_enter_account_device_name)
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
                    val integrationSummaryLabels = listOf(
                        "tmdb" to stringResource(R.string.tv_sync_summary_tmdb_key),
                        "trakt" to stringResource(R.string.tv_sync_summary_trakt_session),
                        "febbox" to stringResource(R.string.tv_sync_summary_febbox_key),
                        "debrid" to stringResource(R.string.tv_sync_summary_debrid_token),
                        "tidb" to stringResource(R.string.tv_sync_summary_tidb_key),
                        "wyzie" to stringResource(R.string.tv_sync_summary_wyzie_key),
                    )
                    val pendingSummary = buildList {
                        integrationSummaryLabels.filter { selected[it.first] == true }.forEach { add(it.second) }
                        if (passphraseEnabled) add(context.getString(R.string.tv_sync_summary_account_sign_in))
                        if (passkeySession != null) add(context.getString(R.string.tv_sync_summary_passkey_sign_in))
                    }
                    val canSend = pendingSummary.isNotEmpty()
                    SyncInstructionCard(stringResource(R.string.tv_sync_nothing_changes_yet), stringResource(R.string.tv_sync_review_on_tv))
                    Text(stringResource(R.string.tv_sync_tv_value, tvName), color = theme.colors.type.text)
                    if (passphraseEnabled) Text(stringResource(R.string.tv_sync_account_device_value, accountDeviceName), color = theme.colors.type.text)
                    pendingSummary.forEach { Text(stringResource(R.string.tv_sync_bullet_item, it), color = theme.colors.type.text) }
                    ZsButton(
                        text = stringResource(R.string.tv_sync_send_to_tv),
                        onClick = {
                            val receiver = selectedReceiver ?: return@ZsButton
                            scope.launch {
                                loading = true
                                Log.d(TV_SYNC_UI_TAG, "send start host=${receiver.host} port=${receiver.port} selected=${selected.filterValues { it }.keys} passphrase=$passphraseEnabled passphraseLength=${passphrase.length} leadingOrTrailingWhitespace=${passphrase != passphrase.trim()} accountDeviceName=$accountDeviceName")
                                runCatching {
                                    val payload = vm.buildPayload(tvName, selected.toMap(), passphrase.takeIf { passphraseEnabled }, accountDeviceName.takeIf { passphraseEnabled || passkeySession != null }, passkeySession)
                                    vm.sendPayload(receiver.host, receiver.port, payload)
                                }.onSuccess {
                                    Log.d(TV_SYNC_UI_TAG, "send success host=${receiver.host} port=${receiver.port}")
                                    page = TvSyncPhonePage.RESULT
                                    status = null
                                    loading = false
                                    runCatching { vm.waitForTransferResult(receiver.host, receiver.port) }
                                        .onSuccess { transferResult = it }
                                        .onFailure { status = it.message ?: context.getString(R.string.tv_sync_could_not_receive_response) }
                                }.onFailure {
                                    Log.w(TV_SYNC_UI_TAG, "send failed host=${receiver.host} port=${receiver.port} error=${it.message}", it)
                                    status = it.message ?: context.getString(R.string.tv_sync_could_not_send_to_tv)
                                }
                                loading = false
                            }
                        },
                        loading = loading,
                        enabled = canSend && !loading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!canSend) {
                        Text(stringResource(R.string.tv_sync_choose_integration_or_login), color = theme.colors.type.secondary)
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
                        else -> SyncInstructionCard(
                            stringResource(R.string.tv_sync_waiting_for_tv, selectedReceiver?.tvName ?: tvName),
                            stringResource(R.string.tv_sync_accept_or_reject_on_tv),
                        )
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
            stringResource(if (accepted) R.string.tv_sync_accepted else R.string.tv_sync_rejected),
            color = theme.colors.type.emphasis,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(if (accepted) R.string.tv_sync_accepted_description else R.string.tv_sync_rejected_description),
            color = theme.colors.type.secondary,
        )
        ZsButton(text = stringResource(R.string.tv_sync_start_another), onClick = onRestart)
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
                Text(availability?.second ?: stringResource(R.string.tv_sync_checking), color = theme.colors.type.secondary, fontSize = 13.sp)
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
private fun TvSyncPayload.localizedSummaryLines(): List<String> {
    val lines = mutableListOf<String>()
    if (tmdbApiKey != null) lines += stringResource(R.string.tv_sync_summary_tmdb_key)
    if (febboxKey != null) lines += stringResource(R.string.tv_sync_summary_febbox_key)
    if (debridToken != null) {
        lines += stringResource(
            R.string.tv_sync_summary_debrid_service,
            debridService ?: stringResource(R.string.tv_sync_service),
        )
    }
    if (tidbKey != null) lines += stringResource(R.string.tv_sync_summary_tidb_key)
    if (wyzieKey != null) lines += stringResource(R.string.tv_sync_summary_wyzie_key)
    if (traktSession != null) lines += stringResource(R.string.tv_sync_summary_trakt_session)
    if (passphrase != null) lines += stringResource(R.string.tv_sync_summary_passphrase_login)
    if (passkeySession != null) lines += stringResource(R.string.tv_sync_summary_passkey_login)
    return lines
}

@Composable
private fun localizedReceiverStatus(status: String): String = when {
    status == "Receiver offline" -> stringResource(R.string.tv_sync_receiver_offline)
    status == "Waiting for your phone" -> stringResource(R.string.tv_sync_waiting_for_phone)
    status == "Receiver error" -> stringResource(R.string.tv_sync_receiver_error)
    status == "Signed in and synced from your phone" -> stringResource(R.string.tv_sync_signed_in_and_synced)
    status == "Synced from your phone" -> stringResource(R.string.tv_sync_synced_from_phone)
    status == "Could not apply sync request" -> stringResource(R.string.tv_sync_could_not_apply_request)
    status == "Transfer rejected. Waiting for your phone" -> stringResource(R.string.tv_sync_transfer_rejected_waiting)
    status == "Review the incoming sync on this TV" -> stringResource(R.string.tv_sync_review_incoming)
    status.startsWith("Paired with ") -> stringResource(R.string.tv_sync_paired_with, status.removePrefix("Paired with "))
    else -> status
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
    val defaultTvName = stringResource(R.string.tv_sync_default_tv_name)
    var tvNameInput by remember(state.tvName, defaultTvName) {
        mutableStateOf(if (state.tvName == "ZStream TV") defaultTvName else state.tvName)
    }
    var applying by remember { mutableStateOf(false) }

    fun returnHome() {
        nav.navigate("home") {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    // The receiver is meant to keep listening for as long as the TV app is open (so casting works
    // without the user having to keep this screen open) -- only start it if it isn't already
    // running, since re-starting would reset the pairing code/secrets and kick any paired phone.
    LaunchedEffect(Unit) {
        if (!vm.receiverState.value.active) vm.startReceiver()
    }

    BackHandler { onBack() }

    Surface(color = theme.colors.background.main, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZsIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.tv_sync_back),
                    variant = ZsIconButtonVariant.Ghost,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tv_sync_from_phone), color = theme.colors.type.emphasis, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            SyncInstructionCard(
                stringResource(if (state.pendingPayload == null) R.string.tv_sync_connect_your_phone else R.string.tv_sync_confirm_request),
                if (state.pendingPayload == null)
                    stringResource(R.string.tv_sync_phone_open_settings)
                else
                    stringResource(R.string.tv_sync_nothing_changes_until_apply)
            )
            if (state.pendingPayload == null) {
                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.tv_sync_enter_code_on_phone), color = theme.colors.type.secondary)
                        Text(state.code, color = theme.colors.type.emphasis, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                        Text(localizedReceiverStatus(state.status), color = theme.colors.type.text)
                    }
                }
                if (state.localIps.isNotEmpty()) {
                    Text(stringResource(R.string.tv_sync_manual_connection_value, state.localIps.joinToString { "$it:${state.port}" }), color = theme.colors.type.secondary)
                }
                OutlinedTextField(
                    value = tvNameInput,
                    onValueChange = { tvNameInput = it },
                    label = { Text(stringResource(R.string.tv_sync_local_network_tv_name)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(showKeyboardOnFocus = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                ZsButton(
                    text = stringResource(R.string.tv_sync_save_tv_name),
                    onClick = { vm.renameTv(tvNameInput) },
                    enabled = tvNameInput.isNotBlank() && tvNameInput.trim() != state.tvName,
                    variant = ZsButtonVariant.Secondary,
                    modifier = Modifier,
                )
            } else {
                val payload = state.pendingPayload ?: return@Column
                Text(stringResource(R.string.tv_sync_phone_wants_to_sync), color = theme.colors.type.emphasis, fontWeight = FontWeight.SemiBold)
                ZsCard(variant = ZsCardVariant.Elevated, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        payload.accountDeviceName?.let { Text(stringResource(R.string.tv_sync_account_device_value, it), color = theme.colors.type.text) }
                        payload.localizedSummaryLines().forEach { Text(stringResource(R.string.tv_sync_bullet_item, it), color = theme.colors.type.secondary) }
                    }
                }
                ZsStatusBanner(message = localizedReceiverStatus(state.status), variant = ZsStatusBannerVariant.Info)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZsButton(
                        text = stringResource(R.string.tv_sync_apply_sync),
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
                        text = stringResource(R.string.tv_sync_reject),
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

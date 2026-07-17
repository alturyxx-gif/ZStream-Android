package com.zstream.android.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.zstream.android.data.local.entity.SettingsEntity
import com.zstream.android.data.local.preferences.SettingsPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val Context.tvSyncStore by preferencesDataStore("tv_sync")

data class TvSyncDiscoveredReceiver(
    val serviceName: String,
    val host: String,
    val port: Int,
    val tvName: String,
    val tvDeviceId: String? = null,
)

data class TvSyncIntegrationAvailability(
    val label: String,
    val enabled: Boolean,
    val status: String,
    val checkedByDefault: Boolean,
)

data class TraktSessionExport(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val profileJson: String? = null,
    val playbackImported: Boolean = false,
)

data class TvSyncPayload(
    val tvName: String,
    val tmdbApiKey: String? = null,
    val febboxKey: String? = null,
    // The full set of Febbox/Aurora keys the user has added on the phone, not just the currently
    // active one -- AuroraKeyManager.ensureActiveKey() needs the whole list to rotate away from
    // an exhausted key. Without it, a TV that only received the single active febboxKey has no
    // fallback: ensureActiveKey() sees an empty list and just trusts that one key blindly (skips
    // the validity/exhaustion check it would otherwise do), so playback quietly breaks on the TV
    // the moment that key's bandwidth runs out, even though the phone would have rotated already.
    val febboxKeys: List<String>? = null,
    val debridToken: String? = null,
    val debridService: String? = null,
    val tidbKey: String? = null,
    val wyzieKey: String? = null,
    val traktSession: TraktSessionExport? = null,
    val passphrase: String? = null,
    val accountDeviceName: String? = null,
    /** A live account session the phone obtained via passkey (WebAuthn), forwarded for the TV to
     *  adopt directly. Real WebAuthn keys can't leave the authenticator, so unlike the old model
     *  there is no seed for the TV to re-derive -- it uses this issued session as-is. */
    val passkeySession: PasskeySessionTransfer? = null,
) {
    fun summaryLines(): List<String> = buildList {
        if (tmdbApiKey != null) add("TMDB API key")
        if (febboxKey != null) add("Febbox key")
        if (debridToken != null) add("Debrid (${debridService ?: "service"})")
        if (tidbKey != null) add("TheIntroDB key")
        if (wyzieKey != null) add("Wyzie key")
        if (traktSession != null) add("Trakt session")
        if (passphrase != null) add("Account login via passphrase")
        if (passkeySession != null) add("Account login via passkey")
    }
}

/** A passkey-authenticated session forwarded phone -> TV during sync. */
data class PasskeySessionTransfer(
    val token: String,
    val userId: String,
    val nickname: String,
)

/**
 * A TV this phone has successfully paired with, persisted to disk so casting/syncing keeps
 * working across app restarts without re-pairing (as long as the TV process itself hasn't also
 * restarted and dropped its side of the session).
 */
data class PairedTv(
    val id: String,
    val tvDeviceId: String? = null,
    val tvName: String,
    val nickname: String,
    val host: String,
    val port: Int,
    val token: String,
    val secretBase64: String,
    val pairedAt: Long,
    val releaseOwnerId: String? = null,
    val releaseOwnerName: String? = null,
)

data class PairedPhone(
    val id: String,
    val phoneDeviceId: String?,
    val phoneName: String,
    val host: String?,
    val port: Int?,
    val pairedAt: Long,
) {
    val needsRepair: Boolean
        get() = phoneDeviceId.isNullOrBlank() || host.isNullOrBlank() || port == null
}

/** TV-side record of a phone that paired with it, persisted so the session survives a TV app restart. */
private data class PairedPhoneSession(
    val token: String,
    val secretBase64: String,
    val phoneName: String,
    val pairedAt: Long,
    val phoneDeviceId: String? = null,
    val host: String? = null,
    val callbackPort: Int? = null,
)

private data class DiscoveredPhone(
    val phoneDeviceId: String,
    val phoneName: String,
    val host: String,
    val port: Int,
)

private data class ProcessedReleaseRequest(
    val requestId: String,
    val issuedAt: Long,
    val message: String,
)

private data class ReleaseRequestAttempt(
    val requestId: String,
    val issuedAt: Long,
)

private data class ReleaseSubscriptionEnvelope(
    val requestId: String,
    val issuedAt: Long,
    val request: ReleaseSubscriptionRequest,
)

private class ReleaseSubscriptionRejected(message: String) : IllegalStateException(message)

data class TvSyncReceiverState(
    val active: Boolean = false,
    val tvName: String = "ZStream TV",
    val code: String = "",
    val port: Int = 0,
    val localIps: List<String> = emptyList(),
    val status: String = "Receiver offline",
    val pendingPayload: TvSyncPayload? = null,
    val pendingToken: String? = null,
)

/**
 * A one-shot "start playing this, right now" command sent from the phone to a paired TV.
 * Carries the exact source/variant already chosen on the phone plus current progress, so the TV
 * plays immediately instead of re-running its own source search.
 */
data class CastPlaybackRequest(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val year: Int,
    val poster: String?,
    val season: Int?,
    val episode: Int?,
    val seasonId: String?,
    val episodeId: String?,
    val sourceId: String,
    val variantId: String?,
    val progressSec: Long,
)

@Singleton
class TvSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val settingsPreferences: SettingsPreferences,
    private val accountRepository: AccountRepository,
    private val traktRepository: TraktRepository,
    private val trackedReleaseRepository: TrackedReleaseRepository,
    private val releaseNotifyManager: ReleaseNotifyManager,
) {
    private val tag = "TvSync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _receiverState = MutableStateFlow(TvSyncReceiverState())
    val receiverState: StateFlow<TvSyncReceiverState> = _receiverState.asStateFlow()

    // Sender (phone) side: the last TV this device successfully paired with, kept in memory only
    // for this process's lifetime — used so a "Cast" button elsewhere in the app (e.g. the player)
    // doesn't need the user to re-run discovery every time.
    private val _activeReceiver = MutableStateFlow<TvSyncDiscoveredReceiver?>(null)
    val activeReceiver: StateFlow<TvSyncDiscoveredReceiver?> = _activeReceiver.asStateFlow()

    // Receiver (TV) side: a cast command that just arrived and hasn't been consumed by the
    // navigation observer yet.
    private val _pendingCast = MutableStateFlow<CastPlaybackRequest?>(null)
    val pendingCast: StateFlow<CastPlaybackRequest?> = _pendingCast.asStateFlow()

    fun consumeCast(): CastPlaybackRequest? {
        val request = _pendingCast.value
        _pendingCast.value = null
        return request
    }

    // Sender (phone) side: every TV this phone has ever paired with, persisted to disk.
    private val _pairedTvs = MutableStateFlow<List<PairedTv>>(emptyList())
    val pairedTvs: StateFlow<List<PairedTv>> = _pairedTvs.asStateFlow()

    private val pairedPhoneSessions = ConcurrentHashMap<String, PairedPhoneSession>()
    private val _pairedPhones = MutableStateFlow<List<PairedPhone>>(emptyList())
    val pairedPhones: StateFlow<List<PairedPhone>> = _pairedPhones.asStateFlow()
    private val releaseRequestAttempts = ConcurrentHashMap<String, ReleaseRequestAttempt>()
    private val phoneClientSlots = Semaphore(MAX_PHONE_CONNECTIONS)

    init {
        scope.launch {
            _pairedTvs.value = loadPairedTvsFromDisk()
            // Repopulate the in-memory sender-side maps so cast/sync keep working after a phone
            // app restart without the user having to re-pair.
            _pairedTvs.value.forEach { tv ->
                sessionIds["${tv.host}:${tv.port}"] = tv.token
                pairedSecrets["${tv.host}:${tv.port}"] = secretFromBase64(tv.secretBase64)
            }
            _pairedTvs.value.maxByOrNull { it.pairedAt }?.let {
                _activeReceiver.value = TvSyncDiscoveredReceiver(serviceName = it.tvName, host = it.host, port = it.port, tvName = it.nickname, tvDeviceId = it.tvDeviceId)
            }
        }
        scope.launch {
            loadPhoneSessionsFromDisk().forEach { session ->
                pairedPhoneSessions[session.id] = session
                pairedSecrets[session.token] = secretFromBase64(session.secretBase64)
            }
            publishPairedPhones()
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun secretToBase64(bytes: ByteArray): String = Base64.Default.encode(bytes)

    @OptIn(ExperimentalEncodingApi::class)
    private fun secretFromBase64(value: String): ByteArray = Base64.Default.decode(value)

    // Guards startReceiver() against concurrent callers (e.g. the app-level auto-start effect and
    // the "Sync from phone" screen's own start-if-not-active effect both firing around app launch) --
    // without this, both could race stopReceiver()/socket-creation and leave the UI showing a
    // pairing code for a socket the other caller already closed.
    private val receiverStartMutex = Mutex()
    private val phoneReceiverStartMutex = Mutex()
    private val phoneSessionsMutex = Mutex()
    private val processedReleaseMutex = Mutex()
    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var phoneServerSocket: ServerSocket? = null
    private var phoneRegistrationListener: NsdManager.RegistrationListener? = null
    @Volatile private var phoneRegistrationRetryCount = 0
    private var currentCode: String? = null
    private var currentSalt: String? = null
    private var currentTvDeviceId: String? = null
    private val pairedSecrets = ConcurrentHashMap<String, ByteArray>()
    private val sessionIds = ConcurrentHashMap<String, String>()
    private val transferResults = ConcurrentHashMap<String, String>()
    private val deliveredResults = ConcurrentHashMap.newKeySet<String>()

    private val keyTvName = stringPreferencesKey("tv_name")
    private val keyTvDeviceId = stringPreferencesKey("tv_device_id")
    private val keyPhoneDeviceId = stringPreferencesKey("phone_device_id")
    private val keyPhoneName = stringPreferencesKey("phone_name")
    private val keyPairedTvs = stringPreferencesKey("paired_tvs")
    private val keyPhoneSessions = stringPreferencesKey("paired_phone_sessions")
    private val keyProcessedReleaseRequests = stringPreferencesKey("processed_release_requests")

    suspend fun defaultTvName(): String =
        context.tvSyncStore.data.first()[keyTvName] ?: "${Build.MODEL.takeIf { it.isNotBlank() } ?: "ZStream"} TV"

    suspend fun setDefaultTvName(name: String) {
        context.tvSyncStore.edit { it[keyTvName] = name.trim().ifBlank { "ZStream TV" } }
        _receiverState.value = _receiverState.value.copy(tvName = name.trim().ifBlank { "ZStream TV" })
    }

    private suspend fun tvDeviceId(): String {
        context.tvSyncStore.data.first()[keyTvDeviceId]?.let { return it }
        val id = randomToken(18)
        context.tvSyncStore.edit { it[keyTvDeviceId] = id }
        return id
    }

    private suspend fun phoneDeviceId(): String {
        context.tvSyncStore.data.first()[keyPhoneDeviceId]?.let { return it }
        val id = randomToken(18)
        context.tvSyncStore.edit { it[keyPhoneDeviceId] = id }
        return id
    }

    private suspend fun phoneName(): String {
        val deviceIdSuffix = phoneDeviceId().takeLast(4).uppercase()
        context.tvSyncStore.data.first()[keyPhoneName]
            ?.takeIf { it.endsWith("($deviceIdSuffix)") }
            ?.let { return it }
        val accountDeviceName = accountRepository.session.first()
            ?.deviceName
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("Android", ignoreCase = true) }
        val manufacturer = Build.MANUFACTURER.trim().takeUnless {
            it.isBlank() || Build.MODEL.startsWith(it, ignoreCase = true)
        }
        val modelName = listOfNotNull(manufacturer, Build.MODEL.trim().takeIf(String::isNotBlank))
            .joinToString(" ").ifBlank { "Android phone" }
        // The short stable suffix keeps two otherwise-identical phone models distinguishable in
        // the TV picker without exposing the full device identifier.
        val name = "${(accountDeviceName ?: modelName).take(40)} ($deviceIdSuffix)"
        context.tvSyncStore.edit { it[keyPhoneName] = name }
        return name
    }

    private suspend fun loadPairedTvsFromDisk(): List<PairedTv> = runCatching {
        val raw = context.tvSyncStore.data.first()[keyPairedTvs] ?: return emptyList()
        val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, PairedTv::class.java).type
        gson.fromJson<List<PairedTv>>(raw, type) ?: emptyList()
    }.getOrElse {
        Log.w(tag, "loadPairedTvsFromDisk failed", it)
        emptyList()
    }

    private suspend fun savePairedTvsToDisk(list: List<PairedTv>) {
        context.tvSyncStore.edit { it[keyPairedTvs] = gson.toJson(list) }
    }

    private suspend fun processReleaseRequestOnce(
        envelope: ReleaseSubscriptionEnvelope,
        action: suspend () -> String,
    ): String = processedReleaseMutex.withLock {
        val now = System.currentTimeMillis()
        require(envelope.requestId.matches(Regex("[A-Za-z0-9_-]{16,64}"))) {
            "Invalid release subscription request"
        }
        require(envelope.issuedAt <= now + RELEASE_REQUEST_CLOCK_SKEW_MS) {
            "Release subscription request is dated in the future"
        }
        require(now - envelope.issuedAt <= RELEASE_REQUEST_MAX_AGE_MS) {
            "Release subscription request expired"
        }

        val stored = runCatching {
            val raw = context.tvSyncStore.data.first()[keyProcessedReleaseRequests]
                ?: return@runCatching emptyList()
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                List::class.java,
                ProcessedReleaseRequest::class.java,
            ).type
            gson.fromJson<List<ProcessedReleaseRequest>>(raw, type).orEmpty()
        }.getOrElse {
            Log.w(tag, "Could not read processed release requests", it)
            emptyList()
        }
        stored.firstOrNull { it.requestId == envelope.requestId }?.let { return@withLock it.message }

        val message = action()
        val retained = (stored + ProcessedReleaseRequest(envelope.requestId, envelope.issuedAt, message))
            .filter { now - it.issuedAt <= PROCESSED_RELEASE_RETENTION_MS }
            .sortedByDescending(ProcessedReleaseRequest::issuedAt)
            .take(MAX_PROCESSED_RELEASE_REQUESTS)
        context.tvSyncStore.edit { it[keyProcessedReleaseRequests] = gson.toJson(retained) }
        message
    }

    private suspend fun upsertPairedTv(
        host: String,
        port: Int,
        tvName: String,
        tvDeviceId: String?,
        token: String,
        secret: ByteArray,
        releaseOwnerId: String,
        releaseOwnerName: String,
    ) {
        val existing = _pairedTvs.value.find {
            (tvDeviceId != null && it.tvDeviceId == tvDeviceId) || (it.host == host && it.port == port)
        }
        val updated = PairedTv(
            id = existing?.id ?: randomToken(12),
            tvDeviceId = tvDeviceId ?: existing?.tvDeviceId,
            tvName = tvName,
            nickname = existing?.nickname?.takeIf { it.isNotBlank() } ?: tvName,
            host = host,
            port = port,
            token = token,
            secretBase64 = secretToBase64(secret),
            pairedAt = System.currentTimeMillis(),
            releaseOwnerId = releaseOwnerId,
            releaseOwnerName = releaseOwnerName,
        )
        val next = _pairedTvs.value.filterNot { it.id == updated.id } + updated
        _pairedTvs.value = next
        savePairedTvsToDisk(next)
    }

    suspend fun renamePairedTv(id: String, nickname: String) {
        val next = _pairedTvs.value.map { if (it.id == id) it.copy(nickname = nickname.trim().ifBlank { it.tvName }) else it }
        _pairedTvs.value = next
        savePairedTvsToDisk(next)
    }

    /** Picks which paired TV the quick "Cast" button in the player targets. */
    fun setActiveReceiver(tv: PairedTv) {
        _activeReceiver.value = TvSyncDiscoveredReceiver(serviceName = tv.tvName, host = tv.host, port = tv.port, tvName = tv.nickname, tvDeviceId = tv.tvDeviceId)
    }

    suspend fun forgetPairedTv(id: String) {
        val removed = _pairedTvs.value.find { it.id == id }
        val next = _pairedTvs.value.filterNot { it.id == id }
        _pairedTvs.value = next
        savePairedTvsToDisk(next)
        if (removed != null) {
            val key = "${removed.host}:${removed.port}"
            sessionIds.remove(key)
            pairedSecrets.remove(key)
            if (_activeReceiver.value?.host == removed.host && _activeReceiver.value?.port == removed.port) {
                _activeReceiver.value = _pairedTvs.value.maxByOrNull { it.pairedAt }?.let {
                    TvSyncDiscoveredReceiver(serviceName = it.tvName, host = it.host, port = it.port, tvName = it.nickname, tvDeviceId = it.tvDeviceId)
                }
            }
        }
    }

    /** Pings a TV at its last known address. True if it responded (i.e. is currently reachable). */
    suspend fun pingTv(host: String, port: Int): Boolean = runCatching { fetchHello(host, port) }.isSuccess

    /**
     * A TV's port is a fresh ServerSocket(0) pick every time its app process restarts, so a
     * previously-paired TV can go "offline" simply because it's now listening on a different port
     * at the same name. Called after a discovery scan: if a discovered receiver shares a paired
     * TV's stable device ID but a different host/port, silently relocate the paired entry to it
     * (same token/secret -- the TV persists those across its own restarts too) instead of making
     * the user re-pair. Name matching is only a legacy fallback for already-saved TVs without IDs.
     */
    suspend fun reconcilePairedHosts(discovered: List<TvSyncDiscoveredReceiver>) {
        var changed = false
        val next = _pairedTvs.value.map { tv ->
            val match = discovered.find {
                val sameDevice = tv.tvDeviceId != null && it.tvDeviceId == tv.tvDeviceId
                val legacySameName = tv.tvDeviceId == null && it.tvName == tv.tvName
                (sameDevice || legacySameName) && (it.host != tv.host || it.port != tv.port)
            }
            if (match == null) {
                tv
            } else {
                changed = true
                val oldKey = "${tv.host}:${tv.port}"
                val secret = pairedSecrets.remove(oldKey)
                sessionIds.remove(oldKey)
                if (secret != null) {
                    val newKey = "${match.host}:${match.port}"
                    sessionIds[newKey] = tv.token
                    pairedSecrets[newKey] = secret
                }
                tv.copy(host = match.host, port = match.port, tvDeviceId = tv.tvDeviceId ?: match.tvDeviceId)
            }
        }
        if (changed) {
            val active = _activeReceiver.value
            val activeMatch = next.find {
                it.host == active?.host ||
                    (it.tvDeviceId != null && it.tvDeviceId == active?.tvDeviceId) ||
                    (active?.tvDeviceId == null && it.tvName == active?.serviceName)
            }
            _pairedTvs.value = next
            savePairedTvsToDisk(next)
            activeMatch?.let { setActiveReceiver(it) }
        }
    }

    private suspend fun loadPhoneSessionsFromDisk(): List<PairedPhoneSession> = runCatching {
        val raw = context.tvSyncStore.data.first()[keyPhoneSessions] ?: return emptyList()
        val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, PairedPhoneSession::class.java).type
        gson.fromJson<List<PairedPhoneSession>>(raw, type) ?: emptyList()
    }.getOrElse {
        Log.w(tag, "loadPhoneSessionsFromDisk failed", it)
        emptyList()
    }

    private val PairedPhoneSession.id: String
        get() = phoneDeviceId?.takeIf(String::isNotBlank) ?: "legacy:$token"

    private fun publishPairedPhones() {
        _pairedPhones.value = pairedPhoneSessions.values
            .sortedByDescending(PairedPhoneSession::pairedAt)
            .map { session ->
                PairedPhone(
                    id = session.id,
                    phoneDeviceId = session.phoneDeviceId,
                    phoneName = session.phoneName,
                    host = session.host,
                    port = session.callbackPort,
                    pairedAt = session.pairedAt,
                )
            }
    }

    private suspend fun persistPhoneSession(
        token: String,
        secret: ByteArray,
        phoneName: String,
        phoneDeviceId: String?,
        host: String?,
        callbackPort: Int?,
    ) = phoneSessionsMutex.withLock {
        val session = PairedPhoneSession(
            token = token,
            secretBase64 = secretToBase64(secret),
            phoneName = phoneName,
            pairedAt = System.currentTimeMillis(),
            phoneDeviceId = phoneDeviceId,
            host = host,
            callbackPort = callbackPort,
        )
        val superseded = pairedPhoneSessions.entries.filter { entry ->
            entry.value.token == token ||
                (!phoneDeviceId.isNullOrBlank() && entry.value.phoneDeviceId == phoneDeviceId)
        }
        superseded.forEach { entry ->
            pairedPhoneSessions.remove(entry.key)
            pairedSecrets.remove(entry.value.token)
        }
        pairedSecrets[token] = secret
        pairedPhoneSessions[session.id] = session
        context.tvSyncStore.edit { it[keyPhoneSessions] = gson.toJson(pairedPhoneSessions.values.toList()) }
        publishPairedPhones()
    }

    suspend fun startReceiver(tvName: String? = null) = receiverStartMutex.withLock {
        if (_receiverState.value.active && tvName == null) {
            // Already listening and no explicit rename requested -- treat as a no-op instead of
            // rotating the code/port out from under a receiver screen that's about to display it.
            return@withLock
        }
        stopReceiver()
        val socket = ServerSocket(0)
        serverSocket = socket
        val code = (100000..999999).random().toString()
        val salt = randomToken(16)
        currentCode = code
        currentSalt = salt
        // Deliberately NOT clearing pairedSecrets/sessionIds here -- they hold persisted phone
        // sessions (reloaded from disk in init{}) that must survive a receiver restart, otherwise
        // every TV app relaunch would silently invalidate every previously-paired phone.
        transferResults.clear()
        deliveredResults.clear()
        val actualName = (tvName ?: defaultTvName()).trim().ifBlank { "ZStream TV" }
        val deviceId = tvDeviceId()
        currentTvDeviceId = deviceId
        setDefaultTvName(actualName)
        _receiverState.value = TvSyncReceiverState(
            active = true,
            tvName = actualName,
            code = code,
            port = socket.localPort,
            localIps = localIpv4Addresses(),
            status = "Waiting for your phone",
        )
        Log.d(tag, "startReceiver tvName=$actualName tvDeviceId=$deviceId port=${socket.localPort} ips=${_receiverState.value.localIps}")
        registerService(actualName, deviceId, socket.localPort)
        scope.launch {
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    Log.d(tag, "accept client=${client.inetAddress.hostAddress}:${client.port}")
                    scope.launch { handleClient(client) }
                } catch (_: Exception) {
                    if (!socket.isClosed) {
                        Log.w(tag, "accept failed while receiver active")
                        _receiverState.value = _receiverState.value.copy(status = "Receiver error")
                    }
                }
            }
        }
    }

    fun stopReceiver() {
        Log.d(tag, "stopReceiver")
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
        } catch (_: Exception) {
        }
        registrationListener = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        currentCode = null
        currentSalt = null
        currentTvDeviceId = null
        _receiverState.value = TvSyncReceiverState()
    }

    /** Keeps a paired phone reachable while its app process is alive. */
    suspend fun startPhoneReceiver() = phoneReceiverStartMutex.withLock {
        if (phoneServerSocket?.isClosed == false) return@withLock
        stopPhoneReceiver()
        var socket: ServerSocket? = null
        try {
            socket = ServerSocket(0)
            phoneServerSocket = socket
            val name = phoneName()
            val deviceId = phoneDeviceId()
            registerPhoneService(name, deviceId, socket.localPort)
            Log.d(tag, "startPhoneReceiver phoneName=$name phoneDeviceId=$deviceId port=${socket.localPort}")
            val activeSocket = socket
            scope.launch {
                while (!activeSocket.isClosed) {
                    try {
                        val client = activeSocket.accept()
                        if (!phoneClientSlots.tryAcquire()) {
                            client.close()
                        } else {
                            scope.launch {
                                try {
                                    handlePhoneClient(client)
                                } finally {
                                    phoneClientSlots.release()
                                }
                            }
                        }
                    } catch (_: Exception) {
                        if (!activeSocket.isClosed) Log.w(tag, "phone receiver accept failed")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Phone receiver startup failed", e)
            runCatching { socket?.close() }
            if (phoneServerSocket === socket) phoneServerSocket = null
            phoneRegistrationListener = null
            schedulePhoneReceiverRetry()
        }
    }

    fun stopPhoneReceiver() {
        try {
            phoneRegistrationListener?.let { nsdManager.unregisterService(it) }
        } catch (_: Exception) {
        }
        phoneRegistrationListener = null
        try {
            phoneServerSocket?.close()
        } catch (_: Exception) {
        }
        phoneServerSocket = null
    }

    private fun schedulePhoneReceiverRetry() {
        val retry = ++phoneRegistrationRetryCount
        val delayMs = (retry * 2_000L).coerceAtMost(30_000L)
        scope.launch {
            delay(delayMs)
            runCatching { startPhoneReceiver() }
                .onFailure { Log.w(tag, "Phone receiver retry failed", it) }
        }
    }

    private suspend fun persistAuthenticatedPhoneEndpoint(
        sessionId: String,
        expectedToken: String,
        phone: DiscoveredPhone,
    ) = phoneSessionsMutex.withLock {
        val current = pairedPhoneSessions[sessionId] ?: return@withLock
        if (current.token != expectedToken) return@withLock
        pairedPhoneSessions[sessionId] = current.copy(
            phoneName = phone.phoneName,
            host = phone.host,
            callbackPort = phone.port,
        )
        context.tvSyncStore.edit { it[keyPhoneSessions] = gson.toJson(pairedPhoneSessions.values.toList()) }
        publishPairedPhones()
    }

    private suspend fun discoverPhones(timeoutMs: Long): List<DiscoveredPhone> =
        suspendCancellableCoroutine { continuation ->
            val found = ConcurrentHashMap<String, DiscoveredPhone>()
            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.w(tag, "phone discovery start failed error=$errorCode")
                    runCatching { nsdManager.stopServiceDiscovery(this) }
                    if (continuation.isActive) continuation.resume(emptyList())
                }

                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.w(tag, "phone discovery stop failed error=$errorCode")
                }

                override fun onDiscoveryStarted(serviceType: String?) = Unit
                override fun onDiscoveryStopped(serviceType: String?) = Unit
                override fun onServiceLost(serviceInfo: NsdServiceInfo?) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceType != PHONE_NSD_SERVICE_TYPE) return
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.w(tag, "phone resolve failed service=${serviceInfo?.serviceName} error=$errorCode")
                        }

                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val deviceId = resolved.attributes["phoneDeviceId"]?.decodeToString()?.takeIf(String::isNotBlank) ?: return
                            val host = resolved.host?.hostAddress ?: return
                            val name = resolved.attributes["phoneName"]?.decodeToString()?.takeIf(String::isNotBlank)
                                ?: resolved.serviceName
                            found["$deviceId:$host:${resolved.port}"] =
                                DiscoveredPhone(deviceId, name, host, resolved.port)
                        }
                    })
                }
            }
            nsdManager.discoverServices(PHONE_NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            scope.launch {
                delay(timeoutMs)
                runCatching { nsdManager.stopServiceDiscovery(listener) }
                if (continuation.isActive) continuation.resume(found.values.toList())
            }
            continuation.invokeOnCancellation {
                runCatching { nsdManager.stopServiceDiscovery(listener) }
            }
        }

    suspend fun discoverReceivers(timeoutMs: Long = 3500): List<TvSyncDiscoveredReceiver> =
        suspendCancellableCoroutine { continuation ->
            val found = linkedMapOf<String, TvSyncDiscoveredReceiver>()
            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.w(tag, "discover onStartDiscoveryFailed serviceType=$serviceType error=$errorCode")
                    runCatching { nsdManager.stopServiceDiscovery(this) }
                    if (continuation.isActive) continuation.resume(emptyList())
                }
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.w(tag, "discover onStopDiscoveryFailed serviceType=$serviceType error=$errorCode")
                    runCatching { nsdManager.stopServiceDiscovery(this) }
                }
                override fun onDiscoveryStarted(serviceType: String?) {
                    Log.d(tag, "discover started serviceType=$serviceType")
                }
                override fun onDiscoveryStopped(serviceType: String?) {
                    Log.d(tag, "discover stopped serviceType=$serviceType found=${found.size}")
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                    Log.d(tag, "discover lost service=${serviceInfo?.serviceName}")
                }
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    Log.d(tag, "discover found service=${serviceInfo.serviceName} type=${serviceInfo.serviceType}")
                    if (serviceInfo.serviceType != NSD_SERVICE_TYPE) return
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.w(tag, "resolve failed service=${serviceInfo?.serviceName} error=$errorCode")
                        }
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val host = resolved.host?.hostAddress ?: return
                            val attrs = resolved.attributes
                            val tvName = attrs["tvName"]?.decodeToString()?.ifBlank { resolved.serviceName } ?: resolved.serviceName
                            val tvDeviceId = attrs["tvDeviceId"]?.decodeToString()?.takeIf { it.isNotBlank() }
                            Log.d(tag, "resolve success service=${resolved.serviceName} host=$host port=${resolved.port} tvName=$tvName tvDeviceId=$tvDeviceId attrs=${attrs.keys}")
                            found["$host:${resolved.port}"] = TvSyncDiscoveredReceiver(
                                serviceName = resolved.serviceName,
                                host = host,
                                port = resolved.port,
                                tvName = tvName,
                                tvDeviceId = tvDeviceId,
                            )
                        }
                    })
                }
            }
            Log.d(tag, "discover request timeoutMs=$timeoutMs serviceType=$NSD_SERVICE_TYPE")
            nsdManager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            scope.launch {
                delay(timeoutMs)
                runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
                Log.d(tag, "discover timeout complete found=${found.values}")
                if (continuation.isActive) continuation.resume(found.values.toList())
            }
            continuation.invokeOnCancellation {
                Log.d(tag, "discover cancelled")
                runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
            }
        }

    suspend fun fetchHello(host: String, port: Int): JSONObject = withContext(Dispatchers.IO) {
        Log.d(tag, "fetchHello host=$host port=$port")
        val request = Request.Builder().url("http://$host:$port/hello").build()
        return@withContext httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            Log.d(tag, "fetchHello response code=${response.code} body=$raw")
            if (!response.isSuccessful) error("TV receiver is unavailable")
            JSONObject(raw)
        }
    }

    suspend fun pair(host: String, port: Int, code: String, salt: String, sessionId: String, phoneName: String, tvName: String = "TV", tvDeviceId: String? = null): String = withContext(Dispatchers.IO) {
        startPhoneReceiver()
        val actualPhoneName = phoneName.trim()
            .takeUnless { it.isBlank() || it.equals("Android Phone", ignoreCase = true) }
            ?: this@TvSyncRepository.phoneName()
        val deviceId = phoneDeviceId()
        val callbackPort = phoneServerSocket?.localPort ?: error("Phone receiver is unavailable")
        val releaseOwner = accountRepository.session.first()
        val releaseOwnerId = releaseOwner?.userId ?: "guest:${accountRepository.getOrCreateGuestId()}"
        val releaseOwnerName = releaseOwner?.nickname?.trim().takeUnless { it.isNullOrBlank() } ?: "Guest"
        Log.d(tag, "pair host=$host port=$port codeLength=${code.length} sessionId=$sessionId phoneName=$actualPhoneName")
        val secret = deriveSecret(code, salt)
        val proof = CryptoUtils.encryptData("pair:$sessionId", secret)
        val callbackProof = CryptoUtils.encryptData("callback:$sessionId:$deviceId:$callbackPort", secret)
        val body = JSONObject()
            .put("sessionId", sessionId)
            .put("clientName", actualPhoneName)
            .put("phoneDeviceId", deviceId)
            .put("callbackPort", callbackPort)
            .put("callbackProof", callbackProof)
            .put("proof", proof)
            .toString()
            .toRequestBody(JSON.toMediaType())
        val request = Request.Builder().url("http://$host:$port/pair").post(body).build()
        return@withContext httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            Log.d(tag, "pair response code=${response.code}")
            if (!response.isSuccessful) error(raw.ifBlank { "Pairing failed" })
            val token = JSONObject(raw).getString("token")
            sessionIds["$host:$port"] = token
            pairedSecrets["$host:$port"] = secret
            _activeReceiver.value = TvSyncDiscoveredReceiver(serviceName = tvName, host = host, port = port, tvName = tvName, tvDeviceId = tvDeviceId)
            upsertPairedTv(
                host,
                port,
                tvName,
                tvDeviceId,
                token,
                secret,
                releaseOwnerId,
                releaseOwnerName,
            )
            token
        }
    }

    /** True if this phone still holds a live pairing session with the given TV, in-memory or persisted to disk. */
    fun isPairedWith(host: String, port: Int): Boolean =
        sessionIds.containsKey("$host:$port") && pairedSecrets.containsKey("$host:$port")

    suspend fun sendCast(host: String, port: Int, request: CastPlaybackRequest) = withContext(Dispatchers.IO) {
        val key = "$host:$port"
        val secret = pairedSecrets[key] ?: error("Pair with the TV first")
        val token = sessionIds[key] ?: error("Pair with the TV first")
        Log.d(tag, "sendCast host=$host port=$port tmdbId=${request.tmdbId} sourceId=${request.sourceId} progressSec=${request.progressSec}")
        val encryptedPayload = CryptoUtils.encryptData(castRequestToJson(request), secret)
        val body = JSONObject()
            .put("token", token)
            .put("payload", encryptedPayload)
            .toString()
            .toRequestBody(JSON.toMediaType())
        val httpRequest = Request.Builder().url("http://$host:$port/cast").post(body).build()
        httpClient.newCall(httpRequest).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            Log.d(tag, "sendCast response code=${response.code} body=$raw")
            if (!response.isSuccessful) error(raw.ifBlank { "Cast failed" })
        }
    }

    suspend fun sendPayload(host: String, port: Int, payload: TvSyncPayload) = withContext(Dispatchers.IO) {
        val key = "$host:$port"
        val secret = pairedSecrets[key] ?: error("Pair with the TV first")
        val token = sessionIds[key] ?: error("Pair with the TV first")
        Log.d(tag, "sendPayload host=$host port=$port keys=${payload.summaryLines()}")
        val encryptedPayload = CryptoUtils.encryptData(payloadToJson(payload), secret)
        val body = JSONObject()
            .put("token", token)
            .put("payload", encryptedPayload)
            .toString()
            .toRequestBody(JSON.toMediaType())
        val request = Request.Builder().url("http://$host:$port/transfer").post(body).build()
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            Log.d(tag, "sendPayload response code=${response.code} body=$raw")
            if (!response.isSuccessful) error(raw.ifBlank { "Transfer failed" })
        }
    }

    suspend fun sendReleaseSubscription(
        phone: PairedPhone,
        request: ReleaseSubscriptionRequest,
    ): String = withContext(Dispatchers.IO) {
        val session = pairedPhoneSessions[phone.id] ?: error("This phone is no longer paired")
        val deviceId = session.phoneDeviceId
        val initialHost = session.host
        val initialPort = session.callbackPort
        if (deviceId.isNullOrBlank() || initialHost.isNullOrBlank() || initialPort == null) {
            error("Pair this phone again to enable release notifications")
        }
        val secret = secretFromBase64(session.secretBase64)
        val attemptKey = "${phone.id}:${request.key}"
        val now = System.currentTimeMillis()
        val attempt = releaseRequestAttempts.compute(attemptKey) { _, existing ->
            existing?.takeIf { now - it.issuedAt <= RELEASE_REQUEST_MAX_AGE_MS }
                ?: ReleaseRequestAttempt(randomToken(18), now)
        } ?: error("Could not create the release subscription request")
        val envelope = ReleaseSubscriptionEnvelope(attempt.requestId, attempt.issuedAt, request)

        try {
            val message = sendReleaseSubscriptionToEndpoint(
                initialHost,
                initialPort,
                session,
                secret,
                envelope,
            )
            releaseRequestAttempts.remove(attemptKey, attempt)
            return@withContext message
        } catch (e: ReleaseSubscriptionRejected) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "Last paired phone endpoint did not authenticate; discovering its current endpoint", e)
        }

        val discovered = try {
            discoverPhones(3_500).filter { it.phoneDeviceId == deviceId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        if (discovered.isEmpty()) {
            throw IllegalStateException(
                "Could not reach ${session.phoneName}. Open ZStream on that phone and try again",
            )
        }

        var lastFailure: Exception? = null
        discovered.distinctBy { it.host to it.port }.forEach { candidate ->
            try {
                val message = sendReleaseSubscriptionToEndpoint(
                    candidate.host,
                    candidate.port,
                    session,
                    secret,
                    envelope,
                )
                persistAuthenticatedPhoneEndpoint(phone.id, session.token, candidate)
                releaseRequestAttempts.remove(attemptKey, attempt)
                return@withContext message
            } catch (e: ReleaseSubscriptionRejected) {
                throw e
            } catch (e: Exception) {
                lastFailure = e
            }
        }
        throw IllegalStateException(
            "Could not confirm the subscription on ${session.phoneName}. Check that phone and try again",
            lastFailure,
        )
    }

    private fun sendReleaseSubscriptionToEndpoint(
        host: String,
        port: Int,
        session: PairedPhoneSession,
        secret: ByteArray,
        envelope: ReleaseSubscriptionEnvelope,
    ): String {
        val encryptedPayload = CryptoUtils.encryptData(releaseSubscriptionToJson(envelope), secret)
        val body = JSONObject()
            .put("token", session.token)
            .put("payload", encryptedPayload)
            .toString()
            .toRequestBody(JSON.toMediaType())
        val request = Request.Builder()
            .url(
                okhttp3.HttpUrl.Builder()
                    .scheme("http")
                    .host(host)
                    .port(port)
                    .addPathSegment("release-subscription")
                    .build()
            )
            .post(body)
            .build()
        return httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Phone endpoint did not return an authenticated acknowledgement")
            }
            val encryptedAck = JSONObject(raw).getString("payload")
            val ack = JSONObject(CryptoUtils.decryptData(encryptedAck, secret))
            require(ack.getString("requestId") == envelope.requestId) {
                "Phone returned an invalid subscription acknowledgement"
            }
            if (!ack.optBoolean("ok")) {
                throw ReleaseSubscriptionRejected(
                    ack.optString("message").takeIf(String::isNotBlank)
                        ?: "Subscription failed on ${session.phoneName}"
                )
            }
            ack.optString("message")
                .takeIf(String::isNotBlank)
                ?: "${session.phoneName} will notify you when ${envelope.request.title} releases"
        }
    }

    suspend fun waitForTransferResult(host: String, port: Int): String {
        val token = sessionIds["$host:$port"] ?: error("Pair with the TV first")
        repeat(600) {
            val status = runCatching {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url("http://$host:$port/status?token=$token").build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("TV status unavailable")
                        JSONObject(response.body?.string().orEmpty()).getString("status")
                    }
                }
            }.getOrNull()
            if (status == "accepted" || status == "rejected") {
                Log.d(tag, "waitForTransferResult status=$status host=$host port=$port")
                return status
            }
            delay(500)
        }
        error("Timed out waiting for the TV")
    }

    suspend fun applyPendingPayload(): Result<String> = runCatching {
        val payload = _receiverState.value.pendingPayload ?: error("No sync request to apply")
        val token = _receiverState.value.pendingToken ?: error("Sync request expired")
        Log.d(tag, "applyPendingPayload start summary=${payload.summaryLines()}")

        // Mark accepted immediately — login/sync below can take time and we don't want
        // the phone's status poller to time out waiting.
        _receiverState.value = _receiverState.value.copy(
            pendingPayload = null,
            pendingToken = null,
            status = when {
                payload.passphrase != null || payload.passkeySession != null -> "Signed in and synced from your phone"
                else -> "Synced from your phone"
            },
        )
        // Rotate the code shown for *new* pairing attempts, but deliberately leave this token's
        // entry in pairedSecrets/sessionIds alone -- wiping it here would kill the just-used
        // pairing session, breaking the paired phone's ability to cast right after syncing (the
        // whole point of casting is that it's repeatable for as long as the pairing lives).
        currentCode = (100000..999999).random().toString()
        currentSalt = randomToken(16)
        _receiverState.value = _receiverState.value.copy(code = currentCode.orEmpty())
        transferResults[token] = "accepted"

        if (payload.passphrase != null) {
            Log.d(tag, "applyPendingPayload account login passphraseLength=${payload.passphrase.length} deviceName=${payload.accountDeviceName}")
            accountRepository.login(payload.passphrase, payload.accountDeviceName ?: payload.tvName)
        }
        if (payload.passkeySession != null) {
            Log.d(tag, "applyPendingPayload adopting forwarded passkey session deviceName=${payload.accountDeviceName}")
            accountRepository.adoptSession(
                AccountSession(
                    userId = payload.passkeySession.userId,
                    token = payload.passkeySession.token,
                    nickname = payload.passkeySession.nickname,
                    deviceName = payload.accountDeviceName ?: payload.tvName,
                    usesPasskey = true,
                )
            )
        }
        val current = settingsPreferences.settings.first()
        settingsPreferences.updateSettings(
            current.copy(
                tmdbApiKey = payload.tmdbApiKey ?: current.tmdbApiKey,
                febboxKey = payload.febboxKey ?: current.febboxKey,
                febboxKeys = payload.febboxKeys ?: current.febboxKeys,
                debridToken = payload.debridToken ?: current.debridToken,
                debridService = payload.debridService ?: current.debridService,
                tidbKey = payload.tidbKey ?: current.tidbKey,
                wyzieKey = payload.wyzieKey ?: current.wyzieKey,
            ),
            syncToRemote = false,
        )
        payload.traktSession?.let { traktRepository.importSession(it) }
        Log.d(tag, "applyPendingPayload success")
        token
    }.onFailure {
        Log.w(tag, "applyPendingPayload failed error=${it.message}", it)
        _receiverState.value = _receiverState.value.copy(
            status = it.message ?: "Could not apply sync request",
        )
    }

    fun rejectPendingPayload(): String? {
        val token = _receiverState.value.pendingToken
        token?.let { transferResults[it] = "rejected" }
        _receiverState.value = _receiverState.value.copy(
            pendingPayload = null,
            pendingToken = null,
            status = "Transfer rejected. Waiting for your phone",
        )
        // Same reasoning as applyPendingPayload: rejecting the key-sync shouldn't kill the pairing
        // session itself -- the phone should still be able to cast without re-pairing.
        return token
    }

    suspend fun waitForDecisionDelivery(token: String) {
        repeat(30) {
            if (token in deliveredResults) return
            delay(100)
        }
    }

    private fun registerService(tvName: String, tvDeviceId: String, port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceType = NSD_SERVICE_TYPE
            serviceName = "ZStream $tvName"
            setPort(port)
            setAttribute("tvName", tvName)
            setAttribute("tvDeviceId", tvDeviceId)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.w(tag, "register failed service=${serviceInfo?.serviceName} error=$errorCode")
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                Log.d(tag, "register success service=${serviceInfo?.serviceName} port=$port tvName=$tvName tvDeviceId=$tvDeviceId")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                Log.d(tag, "unregister service=${serviceInfo?.serviceName}")
            }
        }
        registrationListener = listener
        Log.d(tag, "register request serviceName=ZStream $tvName type=$NSD_SERVICE_TYPE port=$port tvDeviceId=$tvDeviceId")
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun registerPhoneService(phoneName: String, phoneDeviceId: String, port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceType = PHONE_NSD_SERVICE_TYPE
            serviceName = "ZStream $phoneName"
            setPort(port)
            setAttribute("phoneName", phoneName)
            setAttribute("phoneDeviceId", phoneDeviceId)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.w(tag, "phone register failed service=${serviceInfo?.serviceName} error=$errorCode")
                if (phoneRegistrationListener !== this) return
                phoneRegistrationListener = null
                runCatching { phoneServerSocket?.close() }
                phoneServerSocket = null
                schedulePhoneReceiverRetry()
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                if (phoneRegistrationListener !== this) return
                phoneRegistrationRetryCount = 0
                Log.d(tag, "phone register success service=${serviceInfo?.serviceName} port=$port phoneDeviceId=$phoneDeviceId")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) = Unit
        }
        phoneRegistrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private suspend fun handlePhoneClient(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 10_000
            val writer = OutputStreamWriter(socket.getOutputStream())
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val requestLine = readHttpLineLimited(reader, MAX_HTTP_REQUEST_LINE_CHARS) ?: return
                val headers = linkedMapOf<String, String>()
                var totalHeaderChars = 0
                var headersComplete = false
                for (index in 0 until MAX_HTTP_HEADER_LINES) {
                    val line = readHttpLineLimited(reader, MAX_HTTP_HEADER_LINE_CHARS) ?: return
                    if (line.isBlank()) {
                        headersComplete = true
                        break
                    }
                    totalHeaderChars += line.length
                    require(totalHeaderChars <= MAX_HTTP_HEADER_CHARS) { "Request headers are too large" }
                    val parts = line.split(':', limit = 2)
                    if (parts.size == 2) headers[parts[0].trim().lowercase()] = parts[1].trim()
                }
                require(headersComplete) { "Too many request headers" }
                val length = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                if (length !in 1..MAX_LOCAL_REQUEST_BYTES) {
                    respond(writer, 413, """{"error":"Invalid request size"}""")
                    return
                }
                val chars = CharArray(length)
                var read = 0
                while (read < length) {
                    val count = reader.read(chars, read, length - read)
                    if (count < 0) break
                    read += count
                }
                if (requestLine.startsWith("POST /release-subscription ")) {
                    handleReleaseSubscription(String(chars, 0, read), writer)
                } else {
                    respond(writer, 404, """{"error":"Not found"}""")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(tag, "Rejected malformed phone callback request", e)
                runCatching { respond(writer, 400, """{"error":"Invalid request"}""") }
            }
        }
    }

    private suspend fun handleReleaseSubscription(body: String, writer: OutputStreamWriter) {
        var acknowledgementSecret: ByteArray? = null
        var acknowledgementRequestId: String? = null
        try {
            val json = JSONObject(body)
            val token = json.getString("token")
            val pairedTv = _pairedTvs.value.find { it.token == token }
                ?: loadPairedTvsFromDisk().find { it.token == token }
                ?: error("Pair with this phone again")
            val secret = secretFromBase64(pairedTv.secretBase64)
            acknowledgementSecret = secret
            val decrypted = CryptoUtils.decryptData(json.getString("payload"), secret)
            val envelope = releaseSubscriptionFromJson(decrypted)
            acknowledgementRequestId = envelope.requestId
            val currentOwnerId = accountRepository.currentReleaseOwner()
            require(pairedTv.releaseOwnerId != null && pairedTv.releaseOwnerId == currentOwnerId) {
                val profile = pairedTv.releaseOwnerName?.takeIf(String::isNotBlank) ?: "the paired profile"
                "Switch to $profile on this phone, or pair it again"
            }
            val message = processReleaseRequestOnce(envelope) {
                val generation = trackedReleaseRepository.subscribe(envelope.request)
                val confirmationPosted = runCatching {
                    releaseNotifyManager.showSubscriptionConfirmation(envelope.request)
                }.getOrElse {
                    Log.w(tag, "Subscription confirmation failed", it)
                    false
                }
                if (!confirmationPosted) {
                    generation?.let { trackedReleaseRepository.rollbackSubscription(it) }
                    throw NotificationUnavailableException(
                        "Notifications are disabled on this phone. Enable them and try again"
                    )
                }
                "${phoneName()} will notify you when ${envelope.request.title} releases"
            }
            respondReleaseAcknowledgement(
                writer,
                secret,
                envelope.requestId,
                ok = true,
                message = message,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "handleReleaseSubscription failed error=${e.message}", e)
            val secret = acknowledgementSecret
            val requestId = acknowledgementRequestId
            if (secret != null && requestId != null) {
                respondReleaseAcknowledgement(
                    writer,
                    secret,
                    requestId,
                    ok = false,
                    message = e.message ?: "Subscription failed",
                )
            } else {
                respond(writer, 400, """{"error":"Invalid subscription request"}""")
            }
        }
    }

    private fun respondReleaseAcknowledgement(
        writer: OutputStreamWriter,
        secret: ByteArray,
        requestId: String,
        ok: Boolean,
        message: String,
    ) {
        val acknowledgement = CryptoUtils.encryptData(
            JSONObject()
                .put("requestId", requestId)
                .put("ok", ok)
                .put("message", message)
                .toString(),
            secret,
        )
        respond(
            writer,
            200,
            JSONObject().put("payload", acknowledgement).toString(),
        )
    }

    private suspend fun handleClient(client: Socket) {
        client.use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            Log.d(tag, "handleClient requestLine=$requestLine from=${socket.inetAddress.hostAddress}:${socket.port}")
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: return
                if (line.isBlank()) break
                val parts = line.split(':', limit = 2)
                if (parts.size == 2) headers[parts[0].trim().lowercase()] = parts[1].trim()
            }
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            Log.d(tag, "handleClient headers=$headers contentLength=$length")
            val body = if (length > 0) CharArray(length).let {
                reader.read(it)
                String(it)
            } else ""
            if (body.isNotBlank()) Log.d(tag, "handleClient bodyLength=${body.length}")
            val writer = OutputStreamWriter(socket.getOutputStream())
            when {
                requestLine.startsWith("GET /hello") -> respond(writer, 200, helloJson().toString())
                requestLine.startsWith("POST /pair") -> handlePair(body, socket.inetAddress.hostAddress, writer)
                requestLine.startsWith("POST /transfer") -> handleTransfer(body, writer)
                requestLine.startsWith("POST /cast") -> handleCast(body, writer)
                requestLine.startsWith("GET /status") -> handleStatus(requestLine, writer)
                else -> respond(writer, 404, """{"error":"Not found"}""")
            }
        }
    }

    private fun helloJson(): JSONObject = JSONObject()
        .put("tvName", _receiverState.value.tvName)
        .put("tvDeviceId", currentTvDeviceId)
        .put("salt", currentSalt)
        .put("codeLength", 6)
        .put("port", _receiverState.value.port)
        .put("ips", JSONArray(_receiverState.value.localIps))
        .put("sessionId", randomToken(12).also {
            sessionIds["pending"] = it
            Log.d(tag, "helloJson sessionId=$it tvName=${_receiverState.value.tvName} port=${_receiverState.value.port} ips=${_receiverState.value.localIps}")
        })

    private suspend fun handlePair(body: String, clientHost: String?, writer: OutputStreamWriter) {
        runCatching {
            val json = JSONObject(body)
            val sessionId = json.getString("sessionId")
            val proof = json.getString("proof")
            val salt = currentSalt ?: error("Receiver is offline")
            val code = currentCode ?: error("Receiver is offline")
            val secret = deriveSecret(code, salt)
            val decrypted = CryptoUtils.decryptData(proof, secret)
            require(decrypted == "pair:$sessionId") { "Wrong pairing code" }
            val token = randomToken(24)
            val callbackPort = json.optInt("callbackPort", 0).takeIf { it in 1..65535 }
            val phoneDeviceId = json.optString("phoneDeviceId").takeIf(String::isNotBlank)
            val callbackProof = json.optString("callbackProof").takeIf(String::isNotBlank)
            if (callbackPort != null || phoneDeviceId != null || callbackProof != null) {
                require(callbackPort != null && phoneDeviceId != null && callbackProof != null) {
                    "Invalid phone callback"
                }
                require(
                    CryptoUtils.decryptData(callbackProof, secret) ==
                        "callback:$sessionId:$phoneDeviceId:$callbackPort"
                ) { "Invalid phone callback" }
            }
            persistPhoneSession(
                token = token,
                secret = secret,
                phoneName = json.optString("clientName").ifBlank { "phone" },
                phoneDeviceId = phoneDeviceId,
                host = clientHost,
                callbackPort = callbackPort,
            )
            Log.d(tag, "handlePair success sessionId=$sessionId client=${json.optString("clientName")}")
            _receiverState.value = _receiverState.value.copy(status = "Paired with ${json.optString("clientName").ifBlank { "phone" }}")
            respond(writer, 200, JSONObject().put("token", token).toString())
        }.onFailure {
            Log.w(tag, "handlePair failed error=${it.message}", it)
            respond(writer, 401, JSONObject().put("error", it.message ?: "Pairing failed").toString())
        }
    }

    private fun handleTransfer(body: String, writer: OutputStreamWriter) {
        runCatching {
            val json = JSONObject(body)
            val token = json.getString("token")
            // Peek, don't consume -- unlike the old single-use-token design, the pairing session
            // now needs to keep working afterward so the same phone can cast without re-pairing.
            val secret = pairedSecrets[token] ?: error("Pair again")
            val decrypted = CryptoUtils.decryptData(json.getString("payload"), secret)
            val payload = payloadFromJson(decrypted)
            Log.d(tag, "handleTransfer success summary=${payload.summaryLines()} tvName=${payload.tvName}")
            _receiverState.value = _receiverState.value.copy(
                pendingPayload = payload,
                pendingToken = token,
                status = "Review the incoming sync on this TV",
            )
            respond(writer, 200, """{"ok":true}""")
        }.onFailure {
            Log.w(tag, "handleTransfer failed error=${it.message}", it)
            respond(writer, 400, JSONObject().put("error", it.message ?: "Transfer failed").toString())
        }
    }

    private fun handleCast(body: String, writer: OutputStreamWriter) {
        runCatching {
            val json = JSONObject(body)
            val token = json.getString("token")
            // Unlike handleTransfer's pairedSecrets.remove(token) (one-time API-key transfer),
            // casting is meant to be repeatable for as long as this pairing session lives, so the
            // secret is only peeked, never consumed.
            val secret = pairedSecrets[token] ?: error("Pair again")
            val decrypted = CryptoUtils.decryptData(json.getString("payload"), secret)
            val request = castRequestFromJson(decrypted)
            Log.d(tag, "handleCast success tmdbId=${request.tmdbId} sourceId=${request.sourceId}")
            _pendingCast.value = request
            respond(writer, 200, """{"ok":true}""")
        }.onFailure {
            Log.w(tag, "handleCast failed error=${it.message}", it)
            respond(writer, 400, JSONObject().put("error", it.message ?: "Cast failed").toString())
        }
    }

    private fun handleStatus(requestLine: String, writer: OutputStreamWriter) {
        val token = requestLine.substringAfter("token=", "").substringBefore(' ')
        val status = transferResults[token]
            ?: if (_receiverState.value.pendingToken == token) "pending" else "unknown"
        Log.d(tag, "handleStatus status=$status")
        respond(writer, 200, JSONObject().put("status", status).toString())
        if (status == "accepted" || status == "rejected") deliveredResults += token
    }

    private fun respond(writer: OutputStreamWriter, code: Int, body: String) {
        writer.write("HTTP/1.1 $code OK\r\n")
        writer.write("Content-Type: application/json\r\n")
        writer.write("Content-Length: ${body.toByteArray().size}\r\n")
        writer.write("Connection: close\r\n")
        writer.write("\r\n")
        writer.write(body)
        writer.flush()
    }

    private fun payloadToJson(payload: TvSyncPayload): String = JSONObject().apply {
        put("tvName", payload.tvName)
        payload.tmdbApiKey?.let { put("tmdbApiKey", it) }
        payload.febboxKey?.let { put("febboxKey", it) }
        payload.febboxKeys?.let { put("febboxKeys", JSONArray(it)) }
        payload.debridToken?.let { put("debridToken", it) }
        payload.debridService?.let { put("debridService", it) }
        payload.tidbKey?.let { put("tidbKey", it) }
        payload.wyzieKey?.let { put("wyzieKey", it) }
        payload.passphrase?.let { put("passphrase", it) }
        payload.accountDeviceName?.let { put("accountDeviceName", it) }
        payload.passkeySession?.let { put("passkeySession", gson.toJson(it)) }
        payload.traktSession?.let { put("traktSession", gson.toJson(it)) }
    }.toString()

    private fun payloadFromJson(json: String): TvSyncPayload {
        val obj = JSONObject(json)
        return TvSyncPayload(
            tvName = obj.getString("tvName"),
            tmdbApiKey = obj.optString("tmdbApiKey").takeIf { it.isNotBlank() },
            febboxKey = obj.optString("febboxKey").takeIf { it.isNotBlank() },
            febboxKeys = obj.optJSONArray("febboxKeys")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } },
            debridToken = obj.optString("debridToken").takeIf { it.isNotBlank() },
            debridService = obj.optString("debridService").takeIf { it.isNotBlank() },
            tidbKey = obj.optString("tidbKey").takeIf { it.isNotBlank() },
            wyzieKey = obj.optString("wyzieKey").takeIf { it.isNotBlank() },
            passphrase = obj.optString("passphrase").takeIf { it.isNotBlank() },
            accountDeviceName = obj.optString("accountDeviceName").takeIf { it.isNotBlank() },
            passkeySession = obj.optString("passkeySession").takeIf { it.isNotBlank() }?.let {
                gson.fromJson(it, PasskeySessionTransfer::class.java)
            },
            traktSession = obj.optString("traktSession").takeIf { it.isNotBlank() }?.let {
                gson.fromJson(it, TraktSessionExport::class.java)
            },
        )
    }

    private fun castRequestToJson(request: CastPlaybackRequest): String = JSONObject().apply {
        put("tmdbId", request.tmdbId)
        put("mediaType", request.mediaType)
        put("title", request.title)
        put("year", request.year)
        request.poster?.let { put("poster", it) }
        request.season?.let { put("season", it) }
        request.episode?.let { put("episode", it) }
        request.seasonId?.let { put("seasonId", it) }
        request.episodeId?.let { put("episodeId", it) }
        put("sourceId", request.sourceId)
        request.variantId?.let { put("variantId", it) }
        put("progressSec", request.progressSec)
    }.toString()

    private fun castRequestFromJson(json: String): CastPlaybackRequest {
        val obj = JSONObject(json)
        return CastPlaybackRequest(
            tmdbId = obj.getInt("tmdbId"),
            mediaType = obj.getString("mediaType"),
            title = obj.optString("title"),
            year = obj.optInt("year", 0),
            poster = obj.optString("poster").takeIf { it.isNotBlank() },
            season = obj.optInt("season", -1).takeIf { it >= 0 },
            episode = obj.optInt("episode", -1).takeIf { it >= 0 },
            seasonId = obj.optString("seasonId").takeIf { it.isNotBlank() },
            episodeId = obj.optString("episodeId").takeIf { it.isNotBlank() },
            sourceId = obj.getString("sourceId"),
            variantId = obj.optString("variantId").takeIf { it.isNotBlank() },
            progressSec = obj.optLong("progressSec", 0L),
        )
    }

    private fun releaseSubscriptionToJson(envelope: ReleaseSubscriptionEnvelope): String = JSONObject().apply {
        val request = envelope.request
        put("requestId", envelope.requestId)
        put("issuedAt", envelope.issuedAt)
        put("tmdbId", request.tmdbId)
        put("mediaType", request.mediaType)
        put("title", request.title)
        request.posterPath?.let { put("posterPath", it) }
        request.seasonNumber?.let { put("seasonNumber", it) }
        request.episodeNumber?.let { put("episodeNumber", it) }
        request.episodeTitle?.let { put("episodeTitle", it) }
    }.toString()

    private fun releaseSubscriptionFromJson(json: String): ReleaseSubscriptionEnvelope {
        val obj = JSONObject(json)
        val request = ReleaseSubscriptionRequest(
            tmdbId = obj.getInt("tmdbId"),
            mediaType = obj.getString("mediaType"),
            title = obj.getString("title"),
            posterPath = obj.optString("posterPath").takeIf(String::isNotBlank),
            seasonNumber = obj.optInt("seasonNumber", -1).takeIf { it >= 0 },
            episodeNumber = obj.optInt("episodeNumber", -1).takeIf { it > 0 },
            episodeTitle = obj.optString("episodeTitle").takeIf(String::isNotBlank),
        )
        require(request.tmdbId > 0 && request.title.isNotBlank()) { "Invalid release subscription" }
        request.key // validates type and required episode identity
        return ReleaseSubscriptionEnvelope(
            requestId = obj.getString("requestId"),
            issuedAt = obj.getLong("issuedAt"),
            request = request,
        )
    }

    private fun readHttpLineLimited(reader: BufferedReader, maxChars: Int): String? {
        val value = StringBuilder()
        while (true) {
            val next = reader.read()
            if (next < 0) return value.takeIf { it.isNotEmpty() }?.toString()
            if (next.toChar() == '\n') {
                if (value.endsWith("\r")) value.setLength(value.length - 1)
                return value.toString()
            }
            require(value.length < maxChars) { "HTTP line is too long" }
            value.append(next.toChar())
        }
    }

    private fun deriveSecret(code: String, salt: String): ByteArray = CryptoUtils.pbkdf2(code, "tvsync:$salt")

    private fun localIpv4Addresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress }
            .map(InetAddress::getHostAddress)
            .sorted()
    }.getOrDefault(emptyList())

    private fun randomToken(bytes: Int): String {
        val data = ByteArray(bytes)
        SecureRandom().nextBytes(data)
        @OptIn(ExperimentalEncodingApi::class)
        return Base64.UrlSafe.encode(data).trimEnd('=')
    }

    companion object {
        private const val NSD_SERVICE_TYPE = "_zstreamsync._tcp."
        private const val PHONE_NSD_SERVICE_TYPE = "_zstreamphone._tcp."
        private const val JSON = "application/json; charset=utf-8"
        private const val MAX_LOCAL_REQUEST_BYTES = 64 * 1024
        private const val MAX_PHONE_CONNECTIONS = 8
        private const val MAX_HTTP_REQUEST_LINE_CHARS = 2_048
        private const val MAX_HTTP_HEADER_LINE_CHARS = 8_192
        private const val MAX_HTTP_HEADER_CHARS = 16_384
        private const val MAX_HTTP_HEADER_LINES = 32
        private const val MAX_PROCESSED_RELEASE_REQUESTS = 128
        private const val RELEASE_REQUEST_CLOCK_SKEW_MS = 10 * 60 * 1_000L
        private const val RELEASE_REQUEST_MAX_AGE_MS = 24 * 60 * 60 * 1_000L
        private const val PROCESSED_RELEASE_RETENTION_MS = 48 * 60 * 60 * 1_000L
    }
}

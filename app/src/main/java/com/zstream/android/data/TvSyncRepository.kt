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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    val debridToken: String? = null,
    val debridService: String? = null,
    val tidbKey: String? = null,
    val wyzieKey: String? = null,
    val traktSession: TraktSessionExport? = null,
    val passphrase: String? = null,
    val accountDeviceName: String? = null,
) {
    fun summaryLines(): List<String> = buildList {
        if (tmdbApiKey != null) add("TMDB API key")
        if (febboxKey != null) add("Febbox key")
        if (debridToken != null) add("Debrid (${debridService ?: "service"})")
        if (tidbKey != null) add("TheIntroDB key")
        if (wyzieKey != null) add("Wyzie key")
        if (traktSession != null) add("Trakt session")
        if (passphrase != null) add("Account login via passphrase")
    }
}

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

@Singleton
class TvSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val settingsPreferences: SettingsPreferences,
    private val accountRepository: AccountRepository,
    private val traktRepository: TraktRepository,
) {
    private val tag = "TvSync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _receiverState = MutableStateFlow(TvSyncReceiverState())
    val receiverState: StateFlow<TvSyncReceiverState> = _receiverState.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var currentCode: String? = null
    private var currentSalt: String? = null
    private val pairedSecrets = ConcurrentHashMap<String, ByteArray>()
    private val sessionIds = ConcurrentHashMap<String, String>()
    private val transferResults = ConcurrentHashMap<String, String>()
    private val deliveredResults = ConcurrentHashMap.newKeySet<String>()

    private val keyTvName = stringPreferencesKey("tv_name")

    suspend fun defaultTvName(): String =
        context.tvSyncStore.data.first()[keyTvName] ?: "${Build.MODEL.takeIf { it.isNotBlank() } ?: "ZStream"} TV"

    suspend fun setDefaultTvName(name: String) {
        context.tvSyncStore.edit { it[keyTvName] = name.trim().ifBlank { "ZStream TV" } }
        _receiverState.value = _receiverState.value.copy(tvName = name.trim().ifBlank { "ZStream TV" })
    }

    suspend fun startReceiver(tvName: String? = null) {
        stopReceiver()
        val socket = ServerSocket(0)
        serverSocket = socket
        val code = (100000..999999).random().toString()
        val salt = randomToken(16)
        currentCode = code
        currentSalt = salt
        pairedSecrets.clear()
        sessionIds.clear()
        transferResults.clear()
        deliveredResults.clear()
        val actualName = (tvName ?: defaultTvName()).trim().ifBlank { "ZStream TV" }
        setDefaultTvName(actualName)
        _receiverState.value = TvSyncReceiverState(
            active = true,
            tvName = actualName,
            code = code,
            port = socket.localPort,
            localIps = localIpv4Addresses(),
            status = "Waiting for your phone",
        )
        Log.d(tag, "startReceiver tvName=$actualName port=${socket.localPort} ips=${_receiverState.value.localIps}")
        registerService(actualName, socket.localPort)
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
        pairedSecrets.clear()
        sessionIds.clear()
        currentCode = null
        currentSalt = null
        _receiverState.value = TvSyncReceiverState()
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
                            Log.d(tag, "resolve success service=${resolved.serviceName} host=$host port=${resolved.port} tvName=$tvName attrs=${attrs.keys}")
                            found["$host:${resolved.port}"] = TvSyncDiscoveredReceiver(
                                serviceName = resolved.serviceName,
                                host = host,
                                port = resolved.port,
                                tvName = tvName,
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

    suspend fun pair(host: String, port: Int, code: String, salt: String, sessionId: String, phoneName: String): String = withContext(Dispatchers.IO) {
        Log.d(tag, "pair host=$host port=$port codeLength=${code.length} sessionId=$sessionId phoneName=$phoneName")
        val secret = deriveSecret(code, salt)
        val proof = CryptoUtils.encryptData("pair:$sessionId", secret)
        val body = JSONObject()
            .put("sessionId", sessionId)
            .put("clientName", phoneName)
            .put("proof", proof)
            .toString()
            .toRequestBody(JSON.toMediaType())
        val request = Request.Builder().url("http://$host:$port/pair").post(body).build()
        return@withContext httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            Log.d(tag, "pair response code=${response.code} body=$raw")
            if (!response.isSuccessful) error(raw.ifBlank { "Pairing failed" })
            val token = JSONObject(raw).getString("token")
            sessionIds["$host:$port"] = token
            pairedSecrets["$host:$port"] = secret
            token
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
        if (payload.passphrase != null) {
            Log.d(tag, "applyPendingPayload account login passphraseLength=${payload.passphrase.length} leadingOrTrailingWhitespace=${payload.passphrase != payload.passphrase.trim()} deviceName=${payload.accountDeviceName}")
            accountRepository.login(payload.passphrase, payload.accountDeviceName ?: payload.tvName)
        }
        val current = settingsPreferences.settings.first()
        settingsPreferences.updateSettings(
            current.copy(
                tmdbApiKey = payload.tmdbApiKey ?: current.tmdbApiKey,
                febboxKey = payload.febboxKey ?: current.febboxKey,
                debridToken = payload.debridToken ?: current.debridToken,
                debridService = payload.debridService ?: current.debridService,
                tidbKey = payload.tidbKey ?: current.tidbKey,
                wyzieKey = payload.wyzieKey ?: current.wyzieKey,
            ),
            syncToRemote = false,
        )
        payload.traktSession?.let { traktRepository.importSession(it) }
        _receiverState.value = _receiverState.value.copy(
            pendingPayload = null,
            pendingToken = null,
            status = if (payload.passphrase != null) "Signed in and synced from your phone" else "Synced from your phone",
        )
        pairedSecrets.clear()
        sessionIds.clear()
        currentCode = (100000..999999).random().toString()
        currentSalt = randomToken(16)
        _receiverState.value = _receiverState.value.copy(code = currentCode.orEmpty())
        transferResults[token] = "accepted"
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
        pairedSecrets.clear()
        sessionIds.clear()
        return token
    }

    suspend fun waitForDecisionDelivery(token: String) {
        repeat(30) {
            if (token in deliveredResults) return
            delay(100)
        }
    }

    private fun registerService(tvName: String, port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceType = NSD_SERVICE_TYPE
            serviceName = "ZStream $tvName"
            setPort(port)
            setAttribute("tvName", tvName)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.w(tag, "register failed service=${serviceInfo?.serviceName} error=$errorCode")
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                Log.d(tag, "register success service=${serviceInfo?.serviceName} port=$port tvName=$tvName")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                Log.d(tag, "unregister service=${serviceInfo?.serviceName}")
            }
        }
        registrationListener = listener
        Log.d(tag, "register request serviceName=ZStream $tvName type=$NSD_SERVICE_TYPE port=$port")
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
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
            if (body.isNotBlank()) Log.d(tag, "handleClient body=$body")
            val writer = OutputStreamWriter(socket.getOutputStream())
            when {
                requestLine.startsWith("GET /hello") -> respond(writer, 200, helloJson().toString())
                requestLine.startsWith("POST /pair") -> handlePair(body, writer)
                requestLine.startsWith("POST /transfer") -> handleTransfer(body, writer)
                requestLine.startsWith("GET /status") -> handleStatus(requestLine, writer)
                else -> respond(writer, 404, """{"error":"Not found"}""")
            }
        }
    }

    private fun helloJson(): JSONObject = JSONObject()
        .put("tvName", _receiverState.value.tvName)
        .put("salt", currentSalt)
        .put("codeLength", 6)
        .put("port", _receiverState.value.port)
        .put("ips", JSONArray(_receiverState.value.localIps))
        .put("sessionId", randomToken(12).also {
            sessionIds["pending"] = it
            Log.d(tag, "helloJson sessionId=$it tvName=${_receiverState.value.tvName} port=${_receiverState.value.port} ips=${_receiverState.value.localIps}")
        })

    private fun handlePair(body: String, writer: OutputStreamWriter) {
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
            pairedSecrets[token] = secret
            Log.d(tag, "handlePair success sessionId=$sessionId token=$token client=${json.optString("clientName")}")
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
            val secret = pairedSecrets.remove(token) ?: error("Pair again")
            val decrypted = CryptoUtils.decryptData(json.getString("payload"), secret)
            val payload = payloadFromJson(decrypted)
            Log.d(tag, "handleTransfer success token=$token summary=${payload.summaryLines()} tvName=${payload.tvName}")
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
        payload.debridToken?.let { put("debridToken", it) }
        payload.debridService?.let { put("debridService", it) }
        payload.tidbKey?.let { put("tidbKey", it) }
        payload.wyzieKey?.let { put("wyzieKey", it) }
        payload.passphrase?.let { put("passphrase", it) }
        payload.accountDeviceName?.let { put("accountDeviceName", it) }
        payload.traktSession?.let { put("traktSession", gson.toJson(it)) }
    }.toString()

    private fun payloadFromJson(json: String): TvSyncPayload {
        val obj = JSONObject(json)
        return TvSyncPayload(
            tvName = obj.getString("tvName"),
            tmdbApiKey = obj.optString("tmdbApiKey").takeIf { it.isNotBlank() },
            febboxKey = obj.optString("febboxKey").takeIf { it.isNotBlank() },
            debridToken = obj.optString("debridToken").takeIf { it.isNotBlank() },
            debridService = obj.optString("debridService").takeIf { it.isNotBlank() },
            tidbKey = obj.optString("tidbKey").takeIf { it.isNotBlank() },
            wyzieKey = obj.optString("wyzieKey").takeIf { it.isNotBlank() },
            passphrase = obj.optString("passphrase").takeIf { it.isNotBlank() },
            accountDeviceName = obj.optString("accountDeviceName").takeIf { it.isNotBlank() },
            traktSession = obj.optString("traktSession").takeIf { it.isNotBlank() }?.let {
                gson.fromJson(it, TraktSessionExport::class.java)
            },
        )
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
        private const val JSON = "application/json; charset=utf-8"
    }
}

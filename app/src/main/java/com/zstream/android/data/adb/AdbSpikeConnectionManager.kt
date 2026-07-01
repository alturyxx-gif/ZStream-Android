package com.zstream.android.data.adb

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.LocalServices
import io.github.muntashirakon.adb.android.AdbMdns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Security
import java.security.cert.Certificate
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class DiscoveredAdbEndpoints(
    val host: String,
    val pairingPort: Int?,
    val connectPort: Int?,
)

class TvAdbManager private constructor(
    private val appContext: Context,
) : AbsAdbConnectionManager() {
    private val tag = "TvAdb"
    private val httpClient = OkHttpClient()
    private val savedTv = appContext.getSharedPreferences("adb_saved_tv", Context.MODE_PRIVATE)
    private val gson = Gson()
    // Keep legacy filenames so existing paired TVs continue trusting this app.
    private val privateKeyFile = File(appContext.filesDir, "adb_spike_private.pk8")
    private val certificateFile = File(appContext.filesDir, "adb_spike_cert.cer")

    private val adbPrivateKey: PrivateKey by lazy { loadOrCreateKeyPair().first }
    private val adbCertificate: Certificate by lazy { loadOrCreateKeyPair().second }

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        setThrowOnUnauthorised(true)
    }

    override fun getPrivateKey(): PrivateKey = adbPrivateKey

    override fun getCertificate(): Certificate = adbCertificate

    override fun getDeviceName(): String = "ZStream Phone"

    fun discoverEndpoints(timeoutMillis: Long = 10_000): DiscoveredAdbEndpoints =
        discoverDevices(timeoutMillis).first()

    fun discoverDevices(timeoutMillis: Long = 10_000): List<DiscoveredAdbEndpoints> {
        var lastFailure: IOException? = null
        for (attempt in 1..2) {
            try {
                return discoverDevicesOnce(timeoutMillis, attempt)
            } catch (e: IOException) {
                lastFailure = e
                Log.w(tag, "discoverEndpoints attempt=$attempt failed: ${e.message}")
            }
        }
        throw requireNotNull(lastFailure)
    }

    private fun discoverDevicesOnce(timeoutMillis: Long, attempt: Int): List<DiscoveredAdbEndpoints> {
        Log.d(tag, "discoverEndpoints start attempt=$attempt timeoutMs=$timeoutMillis")
        val savedHost = getSavedTv()?.host
        val localHosts = Collections.list(NetworkInterface.getNetworkInterfaces())
            .flatMap { Collections.list(it.inetAddresses) }
            .mapNotNull { it.hostAddress }
            .toSet()
        Log.d(tag, "discoverEndpoints localHosts=$localHosts")
        val pairingByHost = ConcurrentHashMap<String, ConcurrentLinkedDeque<Int>>()
        val connectByHost = ConcurrentHashMap<String, ConcurrentLinkedDeque<Int>>()
        val preferredMatchFound = CountDownLatch(1)

        val pairingDiscovery = AdbMdns(appContext, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { address, port ->
            val host = address?.hostAddress
            if (host != null && host !in localHosts && port > 0) {
                pairingByHost.computeIfAbsent(host) { ConcurrentLinkedDeque() }.apply {
                    remove(port)
                    addLast(port)
                }
                Log.d(tag, "discoverEndpoints pairing host=$host port=$port")
                if ((host == savedHost || host.startsWith("192.168.0.")) && connectByHost.containsKey(host)) {
                    preferredMatchFound.countDown()
                }
            } else if (host != null && host in localHosts) {
                Log.d(tag, "discoverEndpoints ignoring local pairing host=$host port=$port")
            }
        }
        val connectDiscovery = AdbMdns(appContext, AdbMdns.SERVICE_TYPE_TLS_CONNECT) { address, port ->
            val host = address?.hostAddress
            if (host != null && host !in localHosts && port > 0) {
                connectByHost.computeIfAbsent(host) { ConcurrentLinkedDeque() }.apply {
                    remove(port)
                    addLast(port)
                }
                Log.d(tag, "discoverEndpoints connect host=$host port=$port")
                if (host == savedHost || host.startsWith("192.168.0.") && pairingByHost.containsKey(host)) {
                    preferredMatchFound.countDown()
                }
            } else if (host != null && host in localHosts) {
                Log.d(tag, "discoverEndpoints ignoring local connect host=$host port=$port")
            }
        }

        pairingDiscovery.start()
        connectDiscovery.start()
        try {
            if (preferredMatchFound.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                Log.d(tag, "discoverEndpoints preferred host found; collecting for 750ms")
                TimeUnit.MILLISECONDS.sleep(750)
            }
        } finally {
            pairingDiscovery.stop()
            connectDiscovery.stop()
        }

        val reachableConnectEndpoints = connectByHost.flatMap { (host, ports) ->
            ports.toList().asReversed().firstOrNull { port ->
                val reachable = isPortReachable(host, port)
                Log.d(tag, "discoverEndpoints probe host=$host port=$port reachable=$reachable")
                reachable
            }?.let { listOf(host to it) }.orEmpty()
        }
        val endpoints = orderDiscoveredDevices(reachableConnectEndpoints.map { (host, port) ->
            DiscoveredAdbEndpoints(host, pairingByHost[host]?.lastOrNull(), port)
        }, savedHost)
        if (endpoints.isEmpty()) throw IOException("No reachable wireless ADB connect service discovered")
        return endpoints.also {
            Log.d(tag, "discoverEndpoints done devices=$it")
        }
    }

    private fun isPortReachable(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), 750) }
        true
    } catch (_: IOException) {
        false
    }

    fun getSavedTvs(): List<SavedTv> {
        savedTv.getString("devices", null)?.let { json ->
            return runCatching {
                gson.fromJson<List<SavedTv>>(json, object : TypeToken<List<SavedTv>>() {}.type).orEmpty()
            }.getOrElse {
                Log.w(tag, "could not read saved TVs", it)
                emptyList()
            }
        }
        val host = savedTv.getString("host", null) ?: return emptyList()
        val model = savedTv.getString("model", null) ?: return emptyList()
        val legacyPort = savedTv.getInt("legacy_port", -1).takeIf { it > 0 }
        return listOf(SavedTv(host, model, legacyPort)).also { persistSavedTvs(it, it.first().id) }
    }

    fun getSavedTv(): SavedTv? {
        val devices = getSavedTvs()
        val selectedId = savedTv.getString("selected_device", null)
        val selected = devices.firstOrNull { it.id == selectedId } ?: devices.firstOrNull()
        Log.d(tag, "getSavedTv selectedId=$selectedId count=${devices.size} result=${selected?.id ?: "null"}")
        return selected
    }

    fun selectSavedTv(id: String): SavedTv {
        val device = getSavedTvs().firstOrNull { it.id == id } ?: throw IOException("Saved TV not found")
        if (getSavedTv()?.id != id && isConnected) disconnect()
        savedTv.edit().putString("selected_device", id).apply()
        Log.d(tag, "selected saved TV id=$id host=${device.host}")
        return device
    }

    fun renameSavedTv(id: String, nickname: String) {
        val cleanName = nickname.trim().ifBlank { throw IllegalArgumentException("Device name is required") }
        val devices = getSavedTvs().map { if (it.id == id) it.copy(nickname = cleanName) else it }
        require(devices.any { it.id == id }) { "Saved TV not found" }
        persistSavedTvs(devices, getSavedTv()?.id)
    }

    fun forgetSavedTv(id: String? = getSavedTv()?.id) {
        if (id == null) return
        if (isConnected) disconnect()
        val selectedId = getSavedTv()?.id
        val devices = getSavedTvs().filterNot { it.id == id }
        persistSavedTvs(devices, selectedId?.takeIf { it != id } ?: devices.firstOrNull()?.id)
        Log.d(tag, "forgot saved TV id=$id")
    }

    private fun saveTv(device: SavedTv) {
        val devices = mergeSavedTv(getSavedTvs(), device)
        persistSavedTvs(devices, device.id)
    }

    private fun persistSavedTvs(devices: List<SavedTv>, selectedId: String?) {
        savedTv.edit()
            .putString("devices", gson.toJson(devices))
            .putString("selected_device", selectedId)
            .remove("host")
            .remove("model")
            .remove("legacy_port")
            .apply()
    }

    fun reconnectSavedTv(): DiscoveredAdbEndpoints {
        val savedDevice = getSavedTv() ?: throw IOException("No saved TV")
        val savedHost = savedDevice.host
        val savedModel = savedDevice.model
        val legacyPort = savedDevice.legacyPort
        Log.d(tag, "reconnectSavedTv device id=${savedDevice.id} nickname=${savedDevice.nickname} pairingPort=${savedDevice.pairingPort} connectPort=${savedDevice.connectPort}")
        Log.d(tag, "reconnectSavedTv start savedHost=$savedHost savedModel=$savedModel legacyPort=$legacyPort")
        if (isConnected) {
            val model = runShell("getprop", "ro.product.model").trim()
            if (model != savedModel) {
                disconnect()
                throw AdbOperationException(AdbFailureKind.WRONG_DEVICE, "Connected device does not match saved TV")
            }
            Log.d(tag, "reconnectSavedTv reusing active connection model=$model")
            return DiscoveredAdbEndpoints(savedHost, savedDevice.pairingPort, savedDevice.connectPort ?: legacyPort)
        }
        val endpoint = if (legacyPort != null) {
            Log.d(tag, "reconnectSavedTv using saved legacy endpoint host=$savedHost port=$legacyPort")
            DiscoveredAdbEndpoints(savedHost, null, legacyPort)
        } else {
            Log.d(tag, "reconnectSavedTv discovering wireless endpoint for host=$savedHost")
            discoverEndpoints()
        }
        if (endpoint.host != savedHost) throw IOException("Saved TV was not discovered")
        val connectPort = endpoint.connectPort ?: throw IOException("Saved TV has no reachable connect endpoint")
        try {
            if (!isConnected) {
                Log.d(tag, "reconnectSavedTv connect start host=$savedHost port=$connectPort")
                if (!connect(savedHost, connectPort)) throw IOException("Saved TV connection failed")
            }
        } catch (e: Throwable) {
            throw connectionFailure(e)
        }
        val model = runShell("getprop", "ro.product.model").trim()
        if (model != savedModel) {
            disconnect()
            throw AdbOperationException(
                AdbFailureKind.WRONG_DEVICE,
                "Discovered device model $model does not match saved TV $savedModel",
            )
        }
        saveTv(savedDevice.copy(connectPort = connectPort, pairingPort = endpoint.pairingPort ?: savedDevice.pairingPort))
        Log.d(tag, "reconnectSavedTv success host=$savedHost port=$connectPort model=$model")
        return endpoint
    }

    private fun discoverLocalLegacyEndpoint(): DiscoveredAdbEndpoints {
        val localHosts = Collections.list(NetworkInterface.getNetworkInterfaces())
            .flatMap { Collections.list(it.inetAddresses) }
            .mapNotNull { it.hostAddress?.substringBefore('%') }
            .distinct()
        val candidatePorts = listOf(5555, 5557)
        Log.d(tag, "discoverLocalLegacyEndpoint localHosts=$localHosts candidatePorts=$candidatePorts")
        var lastFailure: Throwable? = null
        for (host in localHosts) {
            if (!host.matches(Regex("""(\d{1,3}\.){3}\d{1,3}|[0-9a-fA-F:]+"""))) continue
            for (port in candidatePorts) {
                try {
                    Log.d(tag, "discoverLocalLegacyEndpoint probing host=$host port=$port")
                    val model = connectLegacy(host, port)
                    val device = getSavedTv() ?: throw IOException("Legacy ADB connect did not save a TV")
                    Log.d(tag, "discoverLocalLegacyEndpoint success host=$host port=$port model=$model")
                    return DiscoveredAdbEndpoints(device.host, device.pairingPort, device.connectPort ?: port)
                } catch (t: Throwable) {
                    lastFailure = t
                    Log.w(tag, "discoverLocalLegacyEndpoint failed host=$host port=$port type=${t.javaClass.simpleName} msg=${t.message}")
                    if (isConnected) disconnect()
                }
            }
        }
        throw IOException("Could not find the TV's local ADB endpoint", lastFailure)
    }

    fun connectLegacy(host: String, port: Int): String {
        require(host.isNotBlank()) { "TV IP address is required" }
        require(port in 1..65535) { "ADB port must be between 1 and 65535" }
        Log.d(tag, "connectLegacy start host=$host port=$port thread=${Thread.currentThread().name}")
        if (isConnected) disconnect()
        setThrowOnUnauthorised(false)
        setTimeout(60, TimeUnit.SECONDS)
        try {
            Log.d(tag, "connectLegacy waiting for legacy ADB authorization prompt")
            if (!connect(host.trim(), port)) throw IOException("Legacy ADB connection timed out")
            Log.d(tag, "connectLegacy transport connected; probing model")
            val model = runShell("getprop", "ro.product.model").trim()
            saveTv(SavedTv(host.trim(), model, legacyPort = port))
            Log.d(tag, "connectLegacy success host=${host.trim()} port=$port model=$model")
            return model
        } catch (e: Throwable) {
            Log.w(tag, "connectLegacy failed host=${host.trim()} port=$port type=${e.javaClass.simpleName} msg=${e.message}")
            if (isConnected) disconnect()
            throw connectionFailure(e)
        } finally {
            setTimeout(12, TimeUnit.SECONDS)
            setThrowOnUnauthorised(true)
        }
    }

    fun discoverPairAndConnect(pairingCode: String): String {
        require(pairingCode.length == 6 && pairingCode.all(Char::isDigit)) { "A 6-digit pairing code is required" }
        val devices = discoverDevices().filter { it.pairingPort != null && it.connectPort != null }
        if (devices.isEmpty()) throw AdbOperationException(
            AdbFailureKind.DISCOVERY,
            "No pairable wireless ADB device was discovered.",
        )
        var lastFailure: Throwable? = null
        for (device in devices) {
            try {
                if (isConnected) disconnect()
                Log.d(tag, "discoverPairAndConnect trying host=${device.host} pairingPort=${device.pairingPort} connectPort=${device.connectPort}")
                return pairAndConnect(device.host, device.pairingPort, requireNotNull(device.connectPort), pairingCode)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (isConnected) disconnect()
                lastFailure = t
                Log.w(tag, "discoverPairAndConnect failed host=${device.host} type=${t.javaClass.simpleName} msg=${t.message}")
            }
        }
        throw lastFailure ?: AdbOperationException(AdbFailureKind.PAIRING, "Could not pair with a discovered TV.")
    }

    fun pairAndConnect(host: String, pairingPort: Int?, connectPort: Int, pairingCode: String): String {
        require(host.isNotBlank()) { "TV IP address is required" }
        require(connectPort in 1..65535) { "Connect port must be between 1 and 65535" }
        require(pairingPort == null || pairingPort in 1..65535) { "Pairing port must be between 1 and 65535" }
        Log.d(tag, "pairAndConnect start host=$host pairingPort=$pairingPort connectPort=$connectPort codeLen=${pairingCode.length}")
        if (isConnected && (pairingPort != null || pairingCode.isNotBlank())) {
            Log.d(tag, "pairAndConnect explicit pairing requested; closing active connection")
            disconnect()
        }
        if (isConnected) {
            Log.d(tag, "pairAndConnect already connected; skipping pair() and connect()")
        } else {
            setHostAddress(host)
            if (pairingPort != null || pairingCode.isNotBlank()) {
                require(pairingPort != null && pairingCode.length == 6) { "Pairing port and 6-digit code must both be provided" }
                Log.d(tag, "pairAndConnect pairing start host=$host port=$pairingPort")
                try {
                    if (!pair(host, pairingPort, pairingCode)) {
                        throw AdbOperationException(AdbFailureKind.PAIRING, "Pairing was rejected")
                    }
                } catch (e: AdbOperationException) {
                    throw e
                } catch (e: Throwable) {
                    throw AdbOperationException(AdbFailureKind.PAIRING, "Could not pair with the TV.", e)
                }
                Log.d(tag, "pairAndConnect pairing done host=$host port=$pairingPort")
            } else {
                Log.d(tag, "pairAndConnect pairing skipped, using cached authorization")
            }
            Log.d(tag, "pairAndConnect connect start host=$host port=$connectPort")
            try {
                if (!connect(host, connectPort)) throw IOException("Connect returned false")
            } catch (e: Throwable) {
                throw connectionFailure(e)
            }
            Log.d(tag, "pairAndConnect connect done host=$host port=$connectPort")
        }
        Log.d(tag, "pairAndConnect shell probe start host=$host port=$connectPort")
        return runShell("getprop", "ro.product.model").trim().also { model ->
            saveTv(SavedTv(host, model, connectPort = connectPort, pairingPort = pairingPort))
            Log.d(tag, "saved TV host=$host model=$model")
        }
    }

    fun runShell(vararg command: String): String {
        Log.d(tag, "runShell command=${command.joinToString(" ")} connected=$isConnected")
        openStream(LocalServices.SHELL, *command).use { stream ->
            val input = stream.openInputStream()
            val bytes = ByteArray(4096)
            val out = StringBuilder()
            while (true) {
                val read = input.read(bytes)
                if (read <= 0) break
                out.append(String(bytes, 0, read))
            }
            val result = out.toString()
            Log.d(tag, "runShell result=${result.trim()}")
            if (result.startsWith("/system/bin/sh:")) throw IOException(result.trim())
            return result
        }
    }

    fun installApk(
        apk: InputStream,
        size: Long,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): String {
        require(size > 0) { "APK is empty" }
        Log.d(tag, "installApk start size=$size")
        val startedAt = SystemClock.elapsedRealtime()
        Log.d(tag, "installApk opening package-manager exec stream")
        openStream("exec:cmd package install -r -S $size").use { stream ->
            Log.d(tag, "installApk package-manager stream opened; transfer start")
            var nextLogAt = 10L * 1024 * 1024
            stream.openOutputStream().use { output ->
                val transferred = copyCancellable(apk, output, size, isCancelled) { copied, total ->
                    onProgress(copied, total)
                    if (copied >= nextLogAt || copied == size) {
                        Log.d(tag, "installApk transferred=$copied/$size elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                        nextLogAt += 10L * 1024 * 1024
                    }
                }
                if (transferred != size) throw IOException("APK size mismatch: expected $size, read $transferred")
                output.flush()
            }

            Log.d(tag, "installApk transfer complete; waiting for package-manager result")
            val result = stream.openInputStream().bufferedReader().readText().trim()
            Log.d(tag, "installApk result=$result elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            if (!packageManagerSucceeded(result)) {
                throw IOException("Package install failed: $result")
            }
            return result
        }
    }

    fun downloadApk(
        url: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): File {
        val target = File(appContext.cacheDir, "zstream-install.apk")
        val partial = File(appContext.cacheDir, "zstream-install.apk.part")
        target.delete()
        partial.delete()
        val validatedUrl = validateApkUrl(url)
        Log.d(tag, "downloadApk start url=$validatedUrl target=${target.absolutePath}")
        val startedAt = SystemClock.elapsedRealtime()
        try {
            httpClient.newCall(Request.Builder().url(validatedUrl).build()).execute().use { response ->
                Log.d(tag, "downloadApk HTTP ${response.code} ${response.message}")
                if (!response.isSuccessful) throw IOException("APK download failed: HTTP ${response.code}")
                val body = response.body ?: throw IOException("APK download returned an empty response")
                val expectedSize = body.contentLength()
                Log.d(tag, "downloadApk response received expectedSize=$expectedSize")
                var nextLogAt = 10L * 1024 * 1024
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val downloaded = copyCancellable(input, output, expectedSize, isCancelled) { copied, total ->
                            onProgress(copied, total)
                            if (copied >= nextLogAt || copied == expectedSize) {
                                Log.d(tag, "downloadApk downloaded=$copied/$expectedSize elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                                nextLogAt += 10L * 1024 * 1024
                            }
                        }
                        if (downloaded == 0L) throw IOException("APK download was empty")
                        if (expectedSize >= 0 && downloaded != expectedSize) {
                            throw IOException("APK download size mismatch: expected $expectedSize, read $downloaded")
                        }
                    }
                }
            }
            if (!partial.renameTo(target)) throw IOException("Could not finalize downloaded APK")
            Log.d(tag, "downloadApk complete size=${target.length()} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            return target
        } catch (t: Throwable) {
            Log.w(tag, "downloadApk failed type=${t.javaClass.simpleName} msg=${t.message}")
            partial.delete()
            throw t
        }
    }

    fun installFromUrl(
        url: String,
        onProgress: (InstallProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): InstallResult {
        if (isCancelled()) throw AdbOperationException(AdbFailureKind.CANCELLED, "Install cancelled")
        val validatedUrl = validateApkUrl(url)
        Log.d(tag, "installFromUrl start url=$validatedUrl cancelled=${isCancelled()}")
        onProgress(InstallProgress.Connecting)
        val endpoint = try {
            if (isConnected) {
                val model = runShell("getprop", "ro.product.model").trim()
                val saved = getSavedTv()
                val host = saved?.host ?: "unknown"
                val connectPort = saved?.connectPort ?: saved?.legacyPort
                Log.d(tag, "installFromUrl using active connection host=$host model=$model connectPort=$connectPort")
                DiscoveredAdbEndpoints(host, saved?.pairingPort, connectPort)
            } else {
                Log.d(tag, "installFromUrl discovering local legacy endpoint")
                discoverLocalLegacyEndpoint()
            }
        } catch (e: AdbOperationException) {
            Log.w(tag, "installFromUrl reconnect failed kind=${e.kind} msg=${e.message}")
            throw e
        } catch (e: Throwable) {
            Log.w(tag, "installFromUrl connect failed type=${e.javaClass.simpleName} msg=${e.message}")
            throw AdbOperationException(AdbFailureKind.DISCOVERY, "Could not find the TV's legacy ADB endpoint.", e)
        }
        Log.d(tag, "installFromUrl connected host=${endpoint.host} pairingPort=${endpoint.pairingPort} connectPort=${endpoint.connectPort}")
        val model = getSavedTv()?.model ?: endpoint.host
        val apk = try {
            Log.d(tag, "installFromUrl downloading APK")
            downloadApk(
                validatedUrl,
                onProgress = { bytes, total -> onProgress(InstallProgress.Downloading(bytes, total)) },
                isCancelled = isCancelled,
            )
        } catch (e: CancellationException) {
            Log.w(tag, "installFromUrl download cancelled")
            throw AdbOperationException(AdbFailureKind.CANCELLED, "Download cancelled", e)
        } catch (e: Throwable) {
            Log.w(tag, "installFromUrl download failed type=${e.javaClass.simpleName} msg=${e.message}")
            throw AdbOperationException(AdbFailureKind.DOWNLOAD, "APK download failed: ${e.message}", e)
        }
        return try {
            Log.d(tag, "installFromUrl installing APK size=${apk.length()}")
            val output = apk.inputStream().buffered().use { input ->
                installApk(
                    input,
                    apk.length(),
                    onProgress = { bytes, total ->
                        onProgress(if (bytes == total) InstallProgress.Installing else InstallProgress.Transferring(bytes, total))
                    },
                    isCancelled = isCancelled,
                )
            }
            Log.d(tag, "installFromUrl install finished model=$model")
            InstallResult(model, output)
        } catch (e: CancellationException) {
            Log.w(tag, "installFromUrl install cancelled")
            throw AdbOperationException(AdbFailureKind.CANCELLED, "Install cancelled", e)
        } catch (e: Throwable) {
            Log.w(tag, "installFromUrl install failed type=${e.javaClass.simpleName} msg=${e.message}")
            throw AdbOperationException(AdbFailureKind.INSTALL, "APK install failed: ${e.message}", e)
        } finally {
            Log.d(tag, "installFromUrl deleting cached APK deleted=${apk.delete()}")
        }
    }

    private fun loadOrCreateKeyPair(): Pair<PrivateKey, Certificate> {
        if (privateKeyFile.exists() && certificateFile.exists()) {
            Log.d(tag, "loading cached adb keypair")
            val keyBytes = privateKeyFile.readBytes()
            val certBytes = certificateFile.readBytes()
            val keyFactory = KeyFactory.getInstance("RSA")
            val key = keyFactory.generatePrivate(PKCS8EncodedKeySpec(keyBytes))
            val cert = java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(certBytes.inputStream())
            return key to cert
        }

        Log.d(tag, "creating new adb keypair")
        Security.addProvider(BouncyCastleProvider())
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()
        val publicKey = keyPair.public
        val privateKey = keyPair.private
        val now = Date()
        val expires = Date(now.time + 3650L * 24 * 60 * 60 * 1000)
        val subject = X500Name("CN=ZStream Phone")
        val serial = BigInteger.valueOf(now.time)
        val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            now,
            expires,
            subject,
            publicKey,
        )
        val signer: ContentSigner = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BouncyCastleProvider())
            .build(privateKey)
        val holder: X509CertificateHolder = builder.build(signer)
        val certificate = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider())
            .getCertificate(holder)
        privateKeyFile.writeBytes(privateKey.encoded)
        certificateFile.writeBytes(certificate.encoded)
        Log.d(tag, "saved adb keypair files")
        return privateKey to certificate
    }

    companion object {
        @Volatile
        private var instance: TvAdbManager? = null

        fun get(context: Context): TvAdbManager {
            return instance ?: synchronized(this) {
                instance ?: TvAdbManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

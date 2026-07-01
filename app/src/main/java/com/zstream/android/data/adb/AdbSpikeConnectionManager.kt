package com.zstream.android.data.adb

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
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
        val savedHost = savedTv.getString("host", null)
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

    fun getSavedTv(): SavedTv? {
        val host = savedTv.getString("host", null) ?: return null
        val model = savedTv.getString("model", null) ?: return null
        return SavedTv(host, model)
    }

    fun forgetSavedTv() {
        if (isConnected) disconnect()
        savedTv.edit().clear().apply()
        Log.d(tag, "forgot saved TV")
    }

    fun reconnectSavedTv(): DiscoveredAdbEndpoints {
        val savedHost = savedTv.getString("host", null) ?: throw IOException("No saved TV")
        val savedModel = savedTv.getString("model", null)
        Log.d(tag, "reconnectSavedTv start savedHost=$savedHost savedModel=$savedModel")
        if (isConnected) {
            val model = runShell("getprop", "ro.product.model").trim()
            if (savedModel != null && model != savedModel) {
                disconnect()
                throw AdbOperationException(AdbFailureKind.WRONG_DEVICE, "Connected device does not match saved TV")
            }
            Log.d(tag, "reconnectSavedTv reusing active connection model=$model")
            return DiscoveredAdbEndpoints(savedHost, null, null)
        }
        val endpoint = discoverEndpoints()
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
        if (savedModel != null && model != savedModel) {
            disconnect()
            throw AdbOperationException(
                AdbFailureKind.WRONG_DEVICE,
                "Discovered device model $model does not match saved TV $savedModel",
            )
        }
        Log.d(tag, "reconnectSavedTv success host=$savedHost port=$connectPort model=$model")
        return endpoint
    }

    fun pairAndConnect(host: String, pairingPort: Int?, connectPort: Int, pairingCode: String): String {
        Log.d(tag, "pairAndConnect host=$host pairingPort=$pairingPort connectPort=$connectPort codeLen=${pairingCode.length}")
        if (isConnected) {
            Log.d(tag, "already connected; skipping pair() and connect()")
        } else {
            setHostAddress(host)
            if (pairingPort != null || pairingCode.isNotBlank()) {
                require(pairingPort != null && pairingCode.length == 6) { "Pairing port and 6-digit code must both be provided" }
                Log.d(tag, "pair() start")
                try {
                    if (!pair(host, pairingPort, pairingCode)) {
                        throw AdbOperationException(AdbFailureKind.PAIRING, "Pairing was rejected")
                    }
                } catch (e: AdbOperationException) {
                    throw e
                } catch (e: Throwable) {
                    throw AdbOperationException(AdbFailureKind.PAIRING, "Could not pair with the TV.", e)
                }
                Log.d(tag, "pair() done")
            } else {
                Log.d(tag, "pair() skipped, using cached authorization")
            }
            Log.d(tag, "connect() start host=$host port=$connectPort")
            try {
                if (!connect(host, connectPort)) throw IOException("Connect returned false")
            } catch (e: Throwable) {
                throw connectionFailure(e)
            }
            Log.d(tag, "connect() done")
        }
        Log.d(tag, "shell probe start")
        return runShell("getprop", "ro.product.model").trim().also { model ->
            savedTv.edit().putString("host", host).putString("model", model).apply()
            Log.d(tag, "saved TV host=$host model=$model")
        }
    }

    fun runShell(vararg command: String): String {
        Log.d(tag, "runShell command=${command.joinToString(" ")}")
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
        Log.d(tag, "installApk opening package-manager stream")
        openStream(LocalServices.SHELL, "cmd", "package", "install", "-r", "-S", size.toString()).use { stream ->
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
        Log.d(tag, "downloadApk start url=$validatedUrl")
        val startedAt = SystemClock.elapsedRealtime()
        try {
            httpClient.newCall(Request.Builder().url(validatedUrl).build()).execute().use { response ->
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
        onProgress(InstallProgress.Connecting)
        val endpoint = try {
            reconnectSavedTv()
        } catch (e: AdbOperationException) {
            throw e
        } catch (e: Throwable) {
            throw AdbOperationException(AdbFailureKind.DISCOVERY, "Could not find the saved TV.", e)
        }
        val model = getSavedTv()?.model ?: endpoint.host
        val apk = try {
            downloadApk(
                validatedUrl,
                onProgress = { bytes, total -> onProgress(InstallProgress.Downloading(bytes, total)) },
                isCancelled = isCancelled,
            )
        } catch (e: CancellationException) {
            throw AdbOperationException(AdbFailureKind.CANCELLED, "Download cancelled", e)
        } catch (e: Throwable) {
            throw AdbOperationException(AdbFailureKind.DOWNLOAD, "APK download failed: ${e.message}", e)
        }
        return try {
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
            InstallResult(model, output)
        } catch (e: CancellationException) {
            throw AdbOperationException(AdbFailureKind.CANCELLED, "Install cancelled", e)
        } catch (e: Throwable) {
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

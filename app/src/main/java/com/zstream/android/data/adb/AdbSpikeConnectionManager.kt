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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class DiscoveredAdbEndpoints(
    val host: String,
    val pairingPort: Int?,
    val connectPort: Int?,
)

class AdbSpikeConnectionManager private constructor(
    private val appContext: Context,
) : AbsAdbConnectionManager() {
    private val tag = "AdbSpike"
    private val httpClient = OkHttpClient()
    private val savedTv = appContext.getSharedPreferences("adb_saved_tv", Context.MODE_PRIVATE)
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

    fun discoverEndpoints(timeoutMillis: Long = 10_000): DiscoveredAdbEndpoints {
        var lastFailure: IOException? = null
        for (attempt in 1..2) {
            try {
                return discoverEndpointsOnce(timeoutMillis, attempt)
            } catch (e: IOException) {
                lastFailure = e
                Log.w(tag, "discoverEndpoints attempt=$attempt failed: ${e.message}")
            }
        }
        throw requireNotNull(lastFailure)
    }

    private fun discoverEndpointsOnce(timeoutMillis: Long, attempt: Int): DiscoveredAdbEndpoints {
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
        // ponytail: select one reachable matching host until the real device-picker UI exists.
        val endpoint = reachableConnectEndpoints.firstOrNull { (host) -> host == savedHost }
            ?: reachableConnectEndpoints.firstOrNull { (host) ->
            host.startsWith("192.168.0.") && pairingByHost.containsKey(host)
        }
            ?: reachableConnectEndpoints.firstOrNull { (host) -> pairingByHost.containsKey(host) }
            ?: reachableConnectEndpoints.firstOrNull()
            ?: throw IOException("No reachable wireless ADB connect service discovered")
        val host = endpoint.first
        return DiscoveredAdbEndpoints(host, pairingByHost[host]?.lastOrNull(), endpoint.second).also {
            Log.d(tag, "discoverEndpoints done host=${it.host} pairingPort=${it.pairingPort} connectPort=${it.connectPort}")
        }
    }

    private fun isPortReachable(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), 750) }
        true
    } catch (_: IOException) {
        false
    }

    fun reconnectSavedTv(): DiscoveredAdbEndpoints {
        val savedHost = savedTv.getString("host", null) ?: throw IOException("No saved TV")
        val savedModel = savedTv.getString("model", null)
        Log.d(tag, "reconnectSavedTv start savedHost=$savedHost savedModel=$savedModel")
        val endpoint = discoverEndpoints()
        if (endpoint.host != savedHost) throw IOException("Saved TV was not discovered")
        val connectPort = endpoint.connectPort ?: throw IOException("Saved TV has no reachable connect endpoint")
        if (!isConnected) {
            Log.d(tag, "reconnectSavedTv connect start host=$savedHost port=$connectPort")
            if (!connect(savedHost, connectPort)) throw IOException("Saved TV connection failed")
        }
        val model = runShell("getprop", "ro.product.model").trim()
        if (savedModel != null && model != savedModel) {
            disconnect()
            throw IOException("Discovered device model $model does not match saved TV $savedModel")
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
                if (!pair(host, pairingPort, pairingCode)) {
                    throw IllegalStateException("Pairing returned false")
                }
                Log.d(tag, "pair() done")
            } else {
                Log.d(tag, "pair() skipped, using cached authorization")
            }
            Log.d(tag, "connect() start host=$host port=$connectPort")
            if (!connect(host, connectPort)) {
                throw IllegalStateException("Connect returned false")
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

    fun installApk(apk: InputStream, size: Long): String {
        require(size > 0) { "APK is empty" }
        Log.d(tag, "installApk start size=$size")
        val startedAt = SystemClock.elapsedRealtime()
        Log.d(tag, "installApk opening package-manager stream")
        openStream(LocalServices.SHELL, "cmd", "package", "install", "-r", "-S", size.toString()).use { stream ->
            Log.d(tag, "installApk package-manager stream opened; transfer start")
            val buffer = ByteArray(64 * 1024)
            var transferred = 0L
            var nextLogAt = 10L * 1024 * 1024
            stream.openOutputStream().use { output ->
                while (true) {
                    val read = apk.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    transferred += read
                    if (transferred >= nextLogAt || transferred == size) {
                        Log.d(tag, "installApk transferred=$transferred/$size elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                        nextLogAt += 10L * 1024 * 1024
                    }
                }
                output.flush()
            }
            if (transferred != size) throw IOException("APK size mismatch: expected $size, read $transferred")

            Log.d(tag, "installApk transfer complete; waiting for package-manager result")
            val result = stream.openInputStream().bufferedReader().readText().trim()
            Log.d(tag, "installApk result=$result elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
            if (result.lineSequence().none { it.trim() == "Success" }) {
                throw IOException("Package install failed: $result")
            }
            return result
        }
    }

    fun downloadApk(url: String): File {
        val target = File(appContext.cacheDir, "facer.apk")
        val partial = File(appContext.cacheDir, "facer.apk.part")
        target.delete()
        partial.delete()
        Log.d(tag, "downloadApk start url=$url")
        val startedAt = SystemClock.elapsedRealtime()
        try {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("APK download failed: HTTP ${response.code}")
                val body = response.body ?: throw IOException("APK download returned an empty response")
                val expectedSize = body.contentLength()
                Log.d(tag, "downloadApk response received expectedSize=$expectedSize")
                var downloaded = 0L
                var nextLogAt = 10L * 1024 * 1024
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded >= nextLogAt || downloaded == expectedSize) {
                                Log.d(tag, "downloadApk downloaded=$downloaded/$expectedSize elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
                                nextLogAt += 10L * 1024 * 1024
                            }
                        }
                    }
                }
                if (downloaded == 0L) throw IOException("APK download was empty")
                if (expectedSize >= 0 && downloaded != expectedSize) {
                    throw IOException("APK download size mismatch: expected $expectedSize, read $downloaded")
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
        private var instance: AdbSpikeConnectionManager? = null

        fun get(context: Context): AdbSpikeConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: AdbSpikeConnectionManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

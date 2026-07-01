package com.zstream.android.data.adb

import android.content.Context
import android.util.Log
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.LocalServices
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
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Security
import java.security.cert.Certificate
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

class AdbSpikeConnectionManager private constructor(
    private val appContext: Context,
) : AbsAdbConnectionManager() {
    private val tag = "AdbSpike"
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

    fun pairAndConnect(host: String, pairingPort: Int?, connectPort: Int, pairingCode: String): String {
        Log.d(tag, "pairAndConnect host=$host pairingPort=$pairingPort connectPort=$connectPort codeLen=${pairingCode.length}")
        setHostAddress(host)
        if (pairingPort != null || pairingCode.isNotBlank()) {
            require(pairingPort != null && pairingCode.length == 6) { "Pairing port and 6-digit code must both be provided" }
            Log.d(tag, "pair() start")
            if (!pair(host, pairingPort, pairingCode)) {
                throw IllegalStateException("Pairing returned false")
            }
            Log.d(tag, "pair() done, connect() start")
        } else {
            Log.d(tag, "pair() skipped, using cached authorization")
        }
        if (!connect(host, connectPort)) {
            throw IllegalStateException("Connect returned false")
        }
        Log.d(tag, "connect() done, shell probe start")
        return runShell("getprop", "ro.product.model").trim()
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

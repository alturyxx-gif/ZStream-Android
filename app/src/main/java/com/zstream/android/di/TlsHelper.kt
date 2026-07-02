package com.zstream.android.di

import org.conscrypt.Conscrypt
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import java.security.KeyStore

/**
 * Builds an SSLSocketFactory + X509TrustManager backed entirely by Conscrypt's own
 * certificate bundle, bypassing Android's NetworkSecurityTrustManager.
 *
 * On Android 7 (API 24) the system trust store is missing ISRG Root X1 (Let's Encrypt root),
 * so image.tmdb.org TLS handshakes fail even after inserting Conscrypt as the top security
 * provider — because Conscrypt still delegates chain validation to the platform TrustManager.
 * Using Conscrypt's own TrustManagerFactory points validation at its bundled roots instead.
 *
 * Safe on all API levels; on API 25+ the system roots already have ISRG Root X1,
 * but using Conscrypt's bundle is equally valid. Upgrade path: remove this helper entirely
 * once minSdk is raised to 26+.
 */
object TlsHelper {
    /** Pair of (SSLSocketFactory, X509TrustManager) for use with OkHttpClient.Builder. */
    data class TlsConfig(val socketFactory: SSLSocketFactory, val trustManager: X509TrustManager)

    fun build(): TlsConfig {
        val provider = Conscrypt.newProvider()
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm(), provider)
        tmf.init(null as KeyStore?) // null = use the provider's default trust store
        val trustManager = tmf.trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()
        val sslContext = SSLContext.getInstance("TLS", provider)
        sslContext.init(null, arrayOf(trustManager), null)
        return TlsConfig(sslContext.socketFactory, trustManager)
    }
}

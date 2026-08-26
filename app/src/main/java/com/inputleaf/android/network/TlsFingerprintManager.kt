package com.inputleaf.android.network

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager

object TlsFingerprintManager {

    fun fingerprintOf(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cert.encoded)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun buildPinningSSLContext(
        expectedFingerprint: String,
        clientCertificate: ByteArray? = null,
        clientCertificatePassword: CharArray? = null,
    ): SSLContext {
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (fingerprintOf(chain[0]) != expectedFingerprint) {
                    throw SSLException("Certificate fingerprint mismatch")
                }
            }
        }
        return SSLContext.getInstance("TLS").also {
            it.init(
                keyManagers(clientCertificate, clientCertificatePassword),
                arrayOf(trustManager),
                null,
            )
        }
    }

    /**
     * Creates an SSLContext for TOFU fingerprint capture.
     * Accepts any server certificate unconditionally — TLS chain validation is intentionally
     * bypassed. Use for exactly one connection to capture the server certificate fingerprint.
     * After the user confirms, build a pinning SSLContext for all subsequent connections.
     */
    fun buildCapturingSSLContext(
        clientCertificate: ByteArray? = null,
        clientCertificatePassword: CharArray? = null,
        onCertificate: (X509Certificate) -> Unit,
    ): SSLContext {
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                onCertificate(chain[0])
            }
        }
        return SSLContext.getInstance("TLS").also {
            it.init(
                keyManagers(clientCertificate, clientCertificatePassword),
                arrayOf(trustManager),
                null,
            )
        }
    }

    fun keyManagers(
        clientCertificate: ByteArray?,
        clientCertificatePassword: CharArray?,
    ): Array<KeyManager>? {
        if (clientCertificate == null || clientCertificate.isEmpty()) return null
        val password = clientCertificatePassword ?: CharArray(0)
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(ByteArrayInputStream(clientCertificate), password)
        }
        return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, password)
        }.keyManagers
    }
}

package com.inputleaf.android.network

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager

object TlsFingerprintManager {

    fun fingerprintOf(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cert.encoded)
        return hash.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
    }

    fun formatFingerprint(fingerprint: String): String =
        fingerprint.uppercase(Locale.US).chunked(2).joinToString(":")

    fun buildPinningSSLContext(
        expectedFingerprint: String,
        clientCertificate: ClientCertificateMaterial? = null,
    ): SSLContext = SSLContext.getInstance("TLS").also {
        it.init(
            keyManagers(clientCertificate),
            arrayOf(pinningTrustManager(expectedFingerprint)),
            null,
        )
    }

    internal fun pinningTrustManager(expectedFingerprint: String): X509TrustManager {
        val canonicalExpected = normalizeFingerprint(expectedFingerprint)
        return object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty() || fingerprintOf(chain[0]) != canonicalExpected) {
                    throw SSLException("Certificate fingerprint mismatch")
                }
            }
        }
    }

    internal fun normalizeFingerprint(fingerprint: String): String {
        val normalized = fingerprint.trim().replace(":", "").lowercase(Locale.ROOT)
        require(normalized.matches(Regex("[0-9a-f]{64}"))) {
            "Fingerprint must be a SHA-256 value"
        }
        return normalized
    }

    /**
     * Creates an SSLContext for TOFU fingerprint capture.
     * Accepts any server certificate unconditionally — TLS chain validation is intentionally
     * bypassed. Use for exactly one connection to capture the server certificate fingerprint.
     * After the user confirms, build a pinning SSLContext for all subsequent connections.
     */
    fun buildCapturingSSLContext(
        clientCertificate: ClientCertificateMaterial? = null,
        onCertificate: (X509Certificate) -> Unit,
    ): SSLContext = SSLContext.getInstance("TLS").also {
        it.init(
            keyManagers(clientCertificate),
            arrayOf(capturingTrustManager(onCertificate)),
            null,
        )
    }

    internal fun capturingTrustManager(
        onCertificate: (X509Certificate) -> Unit,
    ): X509TrustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            if (chain.isEmpty()) {
                throw SSLException("Server did not provide a certificate")
            }
            onCertificate(chain[0])
        }
    }

    internal fun keyManagers(
        clientCertificate: ClientCertificateMaterial?,
    ): Array<KeyManager>? {
        if (clientCertificate == null || clientCertificate.pkcs12.isEmpty()) return null
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(
                ByteArrayInputStream(clientCertificate.pkcs12),
                clientCertificate.password,
            )
        }
        return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, clientCertificate.password)
        }.keyManagers
    }
}

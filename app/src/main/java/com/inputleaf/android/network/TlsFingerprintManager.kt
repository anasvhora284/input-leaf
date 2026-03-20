package com.inputleaf.android.network

import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object TlsFingerprintManager {

    fun fingerprintOf(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cert.encoded)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Creates an SSLContext for TOFU fingerprint capture.
     * Accepts any server certificate unconditionally — TLS chain validation is intentionally
     * bypassed. Use for exactly one connection to capture the server certificate fingerprint.
     * After the user confirms, build a pinning SSLContext for all subsequent connections.
     */
    fun buildCapturingSSLContext(onCertificate: (X509Certificate) -> Unit): SSLContext {
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                onCertificate(chain[0])
            }
        }
        return SSLContext.getInstance("TLS").also {
            it.init(null, arrayOf(trustManager), null)
        }
    }
}

package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class TlsFingerprintManagerTest {
    private fun loadTestCert(): X509Certificate {
        val cf = java.security.cert.CertificateFactory.getInstance("X.509")
        val stream = javaClass.classLoader!!.getResourceAsStream("test_cert.pem")!!
        return cf.generateCertificate(stream) as X509Certificate
    }

    @Test fun `fingerprint of same cert is stable`() {
        val cert = loadTestCert()
        val fp1 = TlsFingerprintManager.fingerprintOf(cert)
        val fp2 = TlsFingerprintManager.fingerprintOf(cert)
        assertThat(fp1).isEqualTo(fp2)
    }

    @Test fun `fingerprint is 64 hex chars (SHA-256)`() {
        val cert = loadTestCert()
        val fp = TlsFingerprintManager.fingerprintOf(cert)
        assertThat(fp).matches("[0-9a-f]{64}")
    }

    @Test fun `buildCapturingSSLContext returns initialized TLS context`() {
        var captured: X509Certificate? = null
        val ctx = TlsFingerprintManager.buildCapturingSSLContext { cert -> captured = cert }
        assertThat(ctx).isNotNull()
        assertThat(ctx.protocol).isEqualTo("TLS")
        assertThat(ctx.socketFactory).isNotNull()
        // The callback fires during a TLS handshake when checkServerTrusted is called.
        // Full loopback TLS verification is covered by integration tests (Task 19).
    }
}

package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLException

class TlsFingerprintManagerTest {
    private fun loadTestCert(): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        val stream = javaClass.classLoader!!.getResourceAsStream("test_cert.pem")!!
        return stream.use { factory.generateCertificate(it) as X509Certificate }
    }

    @Test fun `fingerprint of a fixed certificate is deterministic`() {
        val cert = loadTestCert()

        assertThat(TlsFingerprintManager.fingerprintOf(cert))
            .isEqualTo(TlsFingerprintManager.fingerprintOf(loadTestCert()))
    }

    @Test fun `canonical fingerprint is lowercase colon-free SHA-256`() {
        val fingerprint = TlsFingerprintManager.fingerprintOf(loadTestCert())

        assertThat(fingerprint).matches("[0-9a-f]{64}")
        assertThat(TlsFingerprintManager.normalizeFingerprint(fingerprint))
            .isEqualTo(fingerprint)
    }

    @Test fun `formatFingerprint is uppercase colon-separated SHA-256`() {
        assertThat(TlsFingerprintManager.formatFingerprint("a2bde8271f3c"))
            .isEqualTo("A2:BD:E8:27:1F:3C")
    }

    @Test fun `uppercase stored fingerprint is normalized`() {
        val fingerprint = TlsFingerprintManager.fingerprintOf(loadTestCert())

        assertThat(TlsFingerprintManager.normalizeFingerprint(fingerprint.uppercase()))
            .isEqualTo(fingerprint)
    }

    @Test fun `colon-delimited stored fingerprint is normalized`() {
        val fingerprint = TlsFingerprintManager.fingerprintOf(loadTestCert())
        val legacy = fingerprint.uppercase().chunked(2).joinToString(":")

        assertThat(TlsFingerprintManager.normalizeFingerprint(legacy))
            .isEqualTo(fingerprint)
    }

    @Test fun `too-short fingerprint is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            TlsFingerprintManager.normalizeFingerprint("0".repeat(63))
        }
    }

    @Test fun `too-long fingerprint is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            TlsFingerprintManager.normalizeFingerprint("0".repeat(65))
        }
    }

    @Test fun `non-hex fingerprint is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            TlsFingerprintManager.normalizeFingerprint("0".repeat(63) + "g")
        }
    }

    @Test fun `capturing SSL context is initialized for TLS`() {
        val context = TlsFingerprintManager.buildCapturingSSLContext { }

        assertThat(context.protocol).isEqualTo("TLS")
        assertThat(context.socketFactory).isNotNull()
    }

    @Test fun `pinning SSL context is initialized for canonical fingerprint`() {
        val fingerprint = TlsFingerprintManager.fingerprintOf(loadTestCert())
        val context = TlsFingerprintManager.buildPinningSSLContext(fingerprint)

        assertThat(context.protocol).isEqualTo("TLS")
        assertThat(context.socketFactory).isNotNull()
    }

    @Test fun `pinning trust manager accepts a matching certificate`() {
        val certificate = loadTestCert()
        val trustManager = TlsFingerprintManager.pinningTrustManager(
            TlsFingerprintManager.fingerprintOf(certificate),
        )

        trustManager.checkServerTrusted(arrayOf(certificate), "RSA")
    }

    @Test fun `pinning trust manager rejects a mismatched certificate`() {
        val trustManager = TlsFingerprintManager.pinningTrustManager("0".repeat(64))

        val failure = assertThrows(SSLException::class.java) {
            trustManager.checkServerTrusted(arrayOf(loadTestCert()), "RSA")
        }
        assertThat(failure).hasMessageThat().isEqualTo("Certificate fingerprint mismatch")
    }

    @Test fun `pinning trust manager rejects an empty certificate chain`() {
        val trustManager = TlsFingerprintManager.pinningTrustManager("0".repeat(64))

        val failure = assertThrows(SSLException::class.java) {
            trustManager.checkServerTrusted(emptyArray(), "RSA")
        }
        assertThat(failure).hasMessageThat().isEqualTo("Certificate fingerprint mismatch")
    }

    @Test fun `capturing trust manager rejects an empty certificate chain`() {
        val trustManager = TlsFingerprintManager.capturingTrustManager { }

        val failure = assertThrows(SSLException::class.java) {
            trustManager.checkServerTrusted(emptyArray(), "RSA")
        }
        assertThat(failure).hasMessageThat().isEqualTo("Server did not provide a certificate")
    }
}

package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.testutil.ClientCertificateTestFixture
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

    @Test fun `fingerprint of fixed certificate is the expected SHA-256`() {
        val fingerprint = TlsFingerprintManager.fingerprintOf(loadTestCert())

        assertThat(fingerprint)
            .isEqualTo("d8ba33186b09408a149c21bfc8876ee0e3b1385c1c99eceeee7abfac8a48ee0d")
        assertThat(fingerprint).isEqualTo(TlsFingerprintManager.fingerprintOf(loadTestCert()))
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

    @Test fun `stored fingerprint whitespace is normalized`() {
        val fingerprint = TlsFingerprintManager.fingerprintOf(loadTestCert())
        val stored = " \t${TlsFingerprintManager.formatFingerprint(fingerprint)}\n"

        assertThat(TlsFingerprintManager.normalizeFingerprint(stored))
            .isEqualTo(fingerprint)
    }

    @Test fun `formatted fingerprint round trips through normalization`() {
        val fingerprint = TlsFingerprintManager.fingerprintOf(loadTestCert())

        assertThat(TlsFingerprintManager.normalizeFingerprint(
            TlsFingerprintManager.formatFingerprint(fingerprint),
        )).isEqualTo(fingerprint)
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

    @Test fun `capturing trust manager reports the leaf certificate once`() {
        val leaf = loadTestCert()
        var captured: X509Certificate? = null
        var callbackCount = 0
        val trustManager = TlsFingerprintManager.capturingTrustManager {
            captured = it
            callbackCount++
        }

        trustManager.checkServerTrusted(arrayOf(leaf, loadTestCert()), "RSA")

        assertThat(captured).isSameInstanceAs(leaf)
        assertThat(callbackCount).isEqualTo(1)
    }

    @Test fun `pinning trust manager accepts a matching leaf with an extra chain certificate`() {
        val leaf = loadTestCert()
        val trustManager = TlsFingerprintManager.pinningTrustManager(
            TlsFingerprintManager.fingerprintOf(leaf),
        )

        trustManager.checkServerTrusted(arrayOf(leaf, loadTestCert()), "RSA")
    }

    @Test fun `pinning trust manager rejects a nonmatching leaf certificate`() {
        val trustManager = TlsFingerprintManager.pinningTrustManager("0".repeat(64))

        val failure = assertThrows(SSLException::class.java) {
            trustManager.checkServerTrusted(arrayOf(loadTestCert(), loadTestCert()), "RSA")
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

    @Test fun `key managers return null without client certificate material`() {
        assertThat(TlsFingerprintManager.keyManagers(null)).isNull()
        assertThat(
            TlsFingerprintManager.keyManagers(
                ClientCertificateMaterial(ByteArray(0), CharArray(0)),
            ),
        ).isNull()
    }

    @Test fun `key managers load valid client certificate material repeatedly`() {
        val material = ClientCertificateTestFixture.material()
        try {
            assertThat(TlsFingerprintManager.keyManagers(material)).isNotEmpty()
            assertThat(TlsFingerprintManager.keyManagers(material)).isNotEmpty()
        } finally {
            material.clear()
        }
    }
}

package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.interfaces.RSAPublicKey

class SelfSignedRsaCertificateTest {
    @Test fun `generates a Deskflow-style identity that TLS can present`() {
        val material = SelfSignedRsaCertificate.generate()
        try {
            val result = ClientCertificateValidator.validate(material.pkcs12, material.password)
            assertThat(result).isInstanceOf(ClientCertificateValidationResult.Success::class.java)
            val summary = (result as ClientCertificateValidationResult.Success).summary
            assertThat(summary.subject).contains("CN=Input Leaf")
            assertThat(summary.fingerprint).matches("[0-9a-f]{64}")
            assertThat(TlsFingerprintManager.keyManagers(material)).isNotEmpty()
        } finally {
            material.clear()
        }
    }

    @Test fun `uses a 2048-bit RSA key and a 365-day lifetime`() {
        val material = SelfSignedRsaCertificate.generate()
        try {
            val certificate = ClientCertificateValidator.validate(material.pkcs12, material.password)
                as ClientCertificateValidationResult.Success
            val publicKey = publicKeyOf(material)
            assertThat(publicKey.modulus.bitLength()).isEqualTo(2048)
            val lifetimeMs = certificate.summary.validUntilEpochMillis - System.currentTimeMillis()
            val dayMs = 24L * 60L * 60L * 1000L
            assertThat(lifetimeMs).isGreaterThan(360 * dayMs)
            assertThat(lifetimeMs).isLessThan(366 * dayMs)
        } finally {
            material.clear()
        }
    }

    @Test fun `regenerating produces a different fingerprint`() {
        val first = SelfSignedRsaCertificate.generate()
        val second = SelfSignedRsaCertificate.generate()
        try {
            val firstFingerprint = (ClientCertificateValidator.validate(first.pkcs12, first.password)
                as ClientCertificateValidationResult.Success).summary.fingerprint
            val secondFingerprint = (ClientCertificateValidator.validate(second.pkcs12, second.password)
                as ClientCertificateValidationResult.Success).summary.fingerprint
            assertThat(firstFingerprint).isNotEqualTo(secondFingerprint)
        } finally {
            first.clear()
            second.clear()
        }
    }

    private fun publicKeyOf(material: ClientCertificateMaterial): RSAPublicKey {
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(ByteArrayInputStream(material.pkcs12), material.password)
        }
        val alias = keyStore.aliases().nextElement()
        return keyStore.getCertificate(alias).publicKey as RSAPublicKey
    }
}

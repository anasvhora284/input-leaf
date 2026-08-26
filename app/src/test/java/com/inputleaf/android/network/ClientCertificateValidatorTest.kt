package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.testutil.ClientCertificateTestFixture
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyStore

class ClientCertificateValidatorTest {
    @Test fun `accepts a valid PKCS12 identity and exposes its summary`() {
        val material = ClientCertificateTestFixture.material()
        try {
            val result = ClientCertificateValidator.validate(
                material.pkcs12,
                material.password,
            )

            assertThat(result)
                .isInstanceOf(ClientCertificateValidationResult.Success::class.java)
            val summary = (result as ClientCertificateValidationResult.Success).summary
            assertThat(summary.subject).isNotEmpty()
            assertThat(summary.fingerprint).hasLength(64)
            assertThat(summary.validUntilEpochMillis).isGreaterThan(System.currentTimeMillis())
            assertThat(TlsFingerprintManager.keyManagers(material)).isNotEmpty()
        } finally {
            material.clear()
        }
    }

    @Test fun `rejects an incorrect PKCS12 password`() {
        val material = ClientCertificateTestFixture.material()
        try {
            assertThat(
                ClientCertificateValidator.validate(
                    material.pkcs12,
                    "incorrect".toCharArray(),
                )
            ).isEqualTo(ClientCertificateValidationResult.IncorrectPassword)
        } finally {
            material.clear()
        }
    }

    @Test fun `rejects malformed and certificate-only bundles`() {
        assertThat(
            ClientCertificateValidator.validate(
                "not a PKCS12 file".toByteArray(),
                CharArray(0),
            )
        ).isEqualTo(ClientCertificateValidationResult.InvalidFormat)

        val material = ClientCertificateTestFixture.material()
        try {
            val sourceStore = KeyStore.getInstance("PKCS12").apply {
                load(ByteArrayInputStream(material.pkcs12), material.password)
            }
            val certificate = sourceStore.getCertificate(sourceStore.aliases().nextElement())
            val certificateOnly = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setCertificateEntry("certificate", certificate)
            }
            val encoded = ByteArrayOutputStream().use { output ->
                certificateOnly.store(output, material.password)
                output.toByteArray()
            }

            assertThat(ClientCertificateValidator.validate(encoded, material.password))
                .isEqualTo(ClientCertificateValidationResult.NoPrivateKey)
            encoded.fill(0)
        } finally {
            material.clear()
        }
    }
}

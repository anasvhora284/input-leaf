package com.inputleaf.android.storage

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.testutil.ClientCertificateTestFixture
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec

class AeadBlobTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    @Test fun `encrypts and decrypts a client certificate payload`() {
        val material = ClientCertificateTestFixture.material()
        val payload = ClientCertificatePayloadCodec.encode(material)
        val encrypted = AeadBlob.encrypt(payload, key)

        val decrypted = AeadBlob.decrypt(encrypted, key)
        val decoded = ClientCertificatePayloadCodec.decode(decrypted)

        try {
            assertThat(decoded.pkcs12).isEqualTo(material.pkcs12)
            assertThat(decoded.password).isEqualTo(material.password)
            assertThat(encrypted).isNotEqualTo(payload)
        } finally {
            material.clear()
            decoded.clear()
            payload.fill(0)
            decrypted.fill(0)
            encrypted.fill(0)
        }
    }

    @Test fun `rejects tampered encrypted payloads`() {
        val encrypted = AeadBlob.encrypt("secret".toByteArray(), key)
        encrypted[encrypted.lastIndex] = (encrypted.last() + 1).toByte()

        assertThrows(AEADBadTagException::class.java) {
            AeadBlob.decrypt(encrypted, key)
        }
        encrypted.fill(0)
    }
}

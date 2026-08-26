package com.inputleaf.android.storage

import com.inputleaf.android.network.ClientCertificateMaterial
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object AeadBlob {
    private const val VERSION: Byte = 1
    private const val IV_SIZE = 12
    private const val TAG_SIZE_BITS = 128

    fun encrypt(
        plaintext: ByteArray,
        key: SecretKey,
        secureRandom: SecureRandom = SecureRandom(),
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            // Android Keystore forbids caller-provided IVs when randomized encryption is required.
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } catch (_: Exception) {
            val iv = ByteArray(IV_SIZE).also(secureRandom::nextBytes)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        }
        val iv = checkNotNull(cipher.iv) { "AES-GCM encryption produced no IV" }
        require(iv.size == IV_SIZE) { "Unexpected AES-GCM IV size ${iv.size}" }
        return byteArrayOf(VERSION) + iv + cipher.doFinal(plaintext)
    }

    fun decrypt(blob: ByteArray, key: SecretKey): ByteArray {
        require(blob.size > 1 + IV_SIZE) { "Encrypted client certificate is truncated" }
        require(blob[0] == VERSION) { "Unsupported encrypted client certificate version" }
        val iv = blob.copyOfRange(1, 1 + IV_SIZE)
        val ciphertext = blob.copyOfRange(1 + IV_SIZE, blob.size)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
            doFinal(ciphertext)
        }
    }
}

internal object ClientCertificatePayloadCodec {
    private const val VERSION: Byte = 1
    private const val MAX_PKCS12_BYTES = 16 * 1024 * 1024
    private const val MAX_PASSWORD_BYTES = 64 * 1024

    fun encode(material: ClientCertificateMaterial): ByteArray {
        val passwordBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(material.password))
        val passwordBytes = ByteArray(passwordBuffer.remaining()).also(passwordBuffer::get)
        return try {
            ByteArrayOutputStream().use { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeByte(VERSION.toInt())
                    output.writeInt(material.pkcs12.size)
                    output.write(material.pkcs12)
                    output.writeInt(passwordBytes.size)
                    output.write(passwordBytes)
                }
                bytes.toByteArray()
            }
        } finally {
            passwordBytes.fill(0)
        }
    }

    fun decode(payload: ByteArray): ClientCertificateMaterial =
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            require(input.readByte() == VERSION) {
                "Unsupported client certificate payload version"
            }
            val pkcs12Length = input.readInt()
            require(pkcs12Length in 1..MAX_PKCS12_BYTES) {
                "Invalid PKCS12 payload length"
            }
            val pkcs12 = ByteArray(pkcs12Length).also(input::readFully)
            val passwordLength = input.readInt()
            require(passwordLength in 0..MAX_PASSWORD_BYTES) {
                "Invalid client certificate password length"
            }
            val passwordBytes = ByteArray(passwordLength).also(input::readFully)
            try {
                require(input.available() == 0) { "Trailing client certificate payload data" }
                val chars = Charsets.UTF_8.decode(ByteBuffer.wrap(passwordBytes))
                val password = CharArray(chars.remaining()).also(chars::get)
                ClientCertificateMaterial(pkcs12, password)
            } catch (error: Exception) {
                pkcs12.fill(0)
                throw error
            } finally {
                passwordBytes.fill(0)
            }
        }
}

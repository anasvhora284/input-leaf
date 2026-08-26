package com.inputleaf.android.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Log
import com.inputleaf.android.network.ClientCertificateMaterial
import com.inputleaf.android.network.ClientCertificateSummary
import com.inputleaf.android.network.ClientCertificateValidationResult
import com.inputleaf.android.network.ClientCertificateValidator
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class ClientCertificateStore(private val context: Context) {
    private val encryptedFile = AtomicFile(
        File(context.noBackupFilesDir, CLIENT_CERTIFICATE_FILE)
    )

    fun hasCertificate(): Boolean =
        encryptedFile.baseFile.isFile && encryptedFile.baseFile.length() > 0

    fun importCertificate(
        pkcs12: ByteArray,
        password: CharArray,
    ): ClientCertificateValidationResult {
        val validation = ClientCertificateValidator.validate(pkcs12, password)
        if (validation !is ClientCertificateValidationResult.Success) return validation

        val material = ClientCertificateMaterial(pkcs12.copyOf(), password.copyOf())
        val payload = ClientCertificatePayloadCodec.encode(material)
        material.clear()
        val encrypted = try {
            AeadBlob.encrypt(payload, getOrCreateKey())
        } catch (error: Exception) {
            Log.e(TAG, "Failed to encrypt client certificate", error)
            payload.fill(0)
            return ClientCertificateValidationResult.StorageError
        }
        payload.fill(0)

        val output = try {
            encryptedFile.startWrite()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to open client certificate storage", error)
            encrypted.fill(0)
            return ClientCertificateValidationResult.StorageError
        }
        return try {
            output.write(encrypted)
            encryptedFile.finishWrite(output)
            validation
        } catch (error: Exception) {
            runCatching { encryptedFile.failWrite(output) }
            Log.e(TAG, "Failed to store client certificate", error)
            ClientCertificateValidationResult.StorageError
        } finally {
            encrypted.fill(0)
        }
    }

    fun load(): ClientCertificateMaterial? {
        if (!hasCertificate()) return null
        val encrypted = try {
            encryptedFile.readFully()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to read client certificate", error)
            return null
        }
        val payload = try {
            AeadBlob.decrypt(encrypted, getOrCreateKey())
        } catch (error: Exception) {
            Log.e(TAG, "Failed to decrypt client certificate", error)
            return null
        } finally {
            encrypted.fill(0)
        }
        return try {
            ClientCertificatePayloadCodec.decode(payload)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to decode client certificate", error)
            null
        } finally {
            payload.fill(0)
        }
    }

    fun summary(): ClientCertificateSummary? {
        val material = load() ?: return null
        return try {
            (ClientCertificateValidator.validate(material.pkcs12, material.password) as?
                ClientCertificateValidationResult.Success)?.summary
        } finally {
            material.clear()
        }
    }

    fun clear() {
        encryptedFile.delete()
        runCatching {
            KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
                .deleteEntry(KEY_ALIAS)
        }.onFailure { Log.w(TAG, "Failed to remove client certificate key", it) }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val TAG = "ClientCertificateStore"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "inputleaf_client_certificate_aead"
        const val CLIENT_CERTIFICATE_FILE = "deskflow-client-certificate.bin"
    }
}

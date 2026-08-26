package com.inputleaf.android.network

import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.KeyManagerFactory

data class ClientCertificateSummary(
    val subject: String,
    val issuer: String,
    val validUntilEpochMillis: Long,
    val fingerprint: String,
)

sealed interface ClientCertificateValidationResult {
    data class Success(val summary: ClientCertificateSummary) :
        ClientCertificateValidationResult

    data object IncorrectPassword : ClientCertificateValidationResult
    data object InvalidFormat : ClientCertificateValidationResult
    data object NoPrivateKey : ClientCertificateValidationResult
    data object KeyMismatch : ClientCertificateValidationResult
    data object Expired : ClientCertificateValidationResult
    data object NotYetValid : ClientCertificateValidationResult
    data object UnsupportedKey : ClientCertificateValidationResult
    data object StorageError : ClientCertificateValidationResult
}

object ClientCertificateValidator {
    fun validate(
        pkcs12: ByteArray,
        password: CharArray,
    ): ClientCertificateValidationResult {
        if (pkcs12.isEmpty()) return ClientCertificateValidationResult.InvalidFormat
        val keyStore = try {
            KeyStore.getInstance("PKCS12").apply {
                load(ByteArrayInputStream(pkcs12), password)
            }
        } catch (error: Exception) {
            return if (isPasswordFailure(error)) {
                ClientCertificateValidationResult.IncorrectPassword
            } else {
                ClientCertificateValidationResult.InvalidFormat
            }
        }

        val aliases = keyStore.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            if (!keyStore.isKeyEntry(alias)) continue
            val privateKey = try {
                keyStore.getKey(alias, password) as? PrivateKey
            } catch (_: UnrecoverableKeyException) {
                return ClientCertificateValidationResult.IncorrectPassword
            } ?: continue
            val certificate = keyStore.getCertificate(alias) as? X509Certificate ?: continue

            try {
                certificate.checkValidity()
            } catch (_: CertificateExpiredException) {
                return ClientCertificateValidationResult.Expired
            } catch (_: CertificateNotYetValidException) {
                return ClientCertificateValidationResult.NotYetValid
            }

            when (verifyKeyMatches(privateKey, certificate)) {
                null -> return ClientCertificateValidationResult.UnsupportedKey
                false -> return ClientCertificateValidationResult.KeyMismatch
                true -> Unit
            }

            try {
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).init(
                    keyStore,
                    password,
                )
            } catch (_: UnrecoverableKeyException) {
                return ClientCertificateValidationResult.IncorrectPassword
            } catch (_: Exception) {
                return ClientCertificateValidationResult.InvalidFormat
            }

            return ClientCertificateValidationResult.Success(
                ClientCertificateSummary(
                    subject = certificate.subjectX500Principal.name,
                    issuer = certificate.issuerX500Principal.name,
                    validUntilEpochMillis = certificate.notAfter.time,
                    fingerprint = TlsFingerprintManager.fingerprintOf(certificate),
                )
            )
        }
        return ClientCertificateValidationResult.NoPrivateKey
    }

    private fun verifyKeyMatches(
        privateKey: PrivateKey,
        certificate: X509Certificate,
    ): Boolean? {
        val signatureAlgorithm = when (privateKey.algorithm.uppercase(Locale.ROOT)) {
            "RSA" -> "SHA256withRSA"
            "EC", "ECDSA" -> "SHA256withECDSA"
            "DSA" -> "SHA256withDSA"
            else -> return null
        }
        val challenge = ByteArray(32).also(SecureRandom()::nextBytes)
        return runCatching {
            val signer = Signature.getInstance(signatureAlgorithm).apply {
                initSign(privateKey)
                update(challenge)
            }
            val signature = signer.sign()
            Signature.getInstance(signatureAlgorithm).run {
                initVerify(certificate.publicKey)
                update(challenge)
                verify(signature)
            }
        }.getOrDefault(false)
    }

    private fun isPasswordFailure(error: Exception): Boolean {
        if (generateSequence<Throwable>(error) { it.cause }
                .any { it is UnrecoverableKeyException }
        ) {
            return true
        }
        if (error !is IOException) return false
        val message = error.message.orEmpty().lowercase(Locale.ROOT)
        return "password" in message || "tampered" in message
    }
}

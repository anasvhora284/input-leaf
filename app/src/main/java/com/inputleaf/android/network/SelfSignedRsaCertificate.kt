package com.inputleaf.android.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds a Deskflow-style self-signed RSA identity: SHA-256, 2048-bit, CN=Input Leaf,
 * 365-day validity. Stored as PKCS12 so the existing TLS KeyManager path can use it.
 */
object SelfSignedRsaCertificate {
    const val COMMON_NAME = "Input Leaf"
    const val KEY_SIZE = 2048
    const val VALIDITY_DAYS = 365
    const val PKCS12_ALIAS = "input-leaf"

    fun generate(
        commonName: String = COMMON_NAME,
        keySize: Int = KEY_SIZE,
        validityDays: Int = VALIDITY_DAYS,
        notBefore: Date = Date(),
        secureRandom: SecureRandom = SecureRandom(),
    ): ClientCertificateMaterial {
        require(keySize >= 2048) { "Deskflow requires RSA keys of at least 2048 bits" }
        require(validityDays > 0) { "Certificate validity must be positive" }

        val keyPair = KeyPairGenerator.getInstance("RSA").run {
            initialize(keySize, secureRandom)
            generateKeyPair()
        }
        val certificate = selfSignedCertificate(
            keyPair = keyPair,
            commonName = commonName,
            notBefore = notBefore,
            notAfter = Date(notBefore.time + validityDays * 24L * 60L * 60L * 1000L),
            serial = BigInteger(64, secureRandom).abs().let { if (it == BigInteger.ZERO) BigInteger.ONE else it },
        )
        val password = randomPassword(secureRandom)
        val pkcs12 = ByteArrayOutputStream().use { output ->
            KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry(PKCS12_ALIAS, keyPair.private, password, arrayOf(certificate))
                store(output, password)
            }
            output.toByteArray()
        }
        return ClientCertificateMaterial(pkcs12, password)
    }

    private fun selfSignedCertificate(
        keyPair: KeyPair,
        commonName: String,
        notBefore: Date,
        notAfter: Date,
        serial: BigInteger,
    ): X509Certificate {
        val signatureAlgorithm = Der.algorithmIdentifier(SHA256_WITH_RSA)
        val name = Der.directoryName(commonName)
        val tbs = Der.sequence(
            Der.integer(serial) +
                signatureAlgorithm +
                name +
                Der.sequence(Der.time(notBefore) + Der.time(notAfter)) +
                name +
                keyPair.public.encoded,
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(tbs)
            sign()
        }
        val encoded = Der.sequence(tbs + signatureAlgorithm + Der.bitString(signature))
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(encoded)) as X509Certificate
        certificate.verify(keyPair.public)
        certificate.checkValidity(notBefore)
        return certificate
    }

    private fun randomPassword(secureRandom: SecureRandom): CharArray {
        val bytes = ByteArray(16).also(secureRandom::nextBytes)
        return try {
            bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }.toCharArray()
        } finally {
            bytes.fill(0)
        }
    }

    private val SHA256_WITH_RSA = intArrayOf(1, 2, 840, 113549, 1, 1, 11)
}

private object Der {
    private val UTC = TimeZone.getTimeZone("UTC")
    private val COMMON_NAME_OID = intArrayOf(2, 5, 4, 3)

    fun sequence(content: ByteArray): ByteArray = tlv(0x30, content)

    fun integer(value: BigInteger): ByteArray = tlv(0x02, value.toByteArray())

    fun bitString(bytes: ByteArray): ByteArray = tlv(0x03, byteArrayOf(0) + bytes)

    fun algorithmIdentifier(oid: IntArray): ByteArray =
        sequence(objectIdentifier(oid) + tlv(0x05, ByteArray(0)))

    fun directoryName(commonName: String): ByteArray {
        val attribute = sequence(
            objectIdentifier(COMMON_NAME_OID) + tlv(0x0c, commonName.toByteArray(Charsets.UTF_8)),
        )
        return sequence(tlv(0x31, attribute))
    }

    fun time(date: Date): ByteArray {
        val calendarYear = Calendar.getInstance(UTC).apply { time = date }.get(Calendar.YEAR)
        val pattern = if (calendarYear in 1950..2049) "yyMMddHHmmss'Z'" else "yyyyMMddHHmmss'Z'"
        val encoded = SimpleDateFormat(pattern, Locale.US).apply { timeZone = UTC }.format(date)
        val tag = if (calendarYear in 1950..2049) 0x17 else 0x18
        return tlv(tag, encoded.toByteArray(Charsets.US_ASCII))
    }

    fun objectIdentifier(oid: IntArray): ByteArray {
        require(oid.size >= 2) { "OID must have at least two components" }
        val body = ByteArrayOutputStream()
        body.write(40 * oid[0] + oid[1])
        for (index in 2 until oid.size) {
            writeBase128(body, oid[index])
        }
        return tlv(0x06, body.toByteArray())
    }

    private fun writeBase128(output: ByteArrayOutputStream, value: Int) {
        require(value >= 0) { "OID component must be non-negative" }
        if (value < 128) {
            output.write(value)
            return
        }
        val encoded = ArrayDeque<Int>()
        var remaining = value
        encoded.addFirst(remaining and 0x7f)
        remaining = remaining ushr 7
        while (remaining > 0) {
            encoded.addFirst((remaining and 0x7f) or 0x80)
            remaining = remaining ushr 7
        }
        encoded.forEach(output::write)
    }

    private fun tlv(tag: Int, content: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + length(content.size) + content

    private fun length(size: Int): ByteArray {
        require(size >= 0) { "DER length must be non-negative" }
        if (size < 128) return byteArrayOf(size.toByte())
        val parts = ArrayList<Byte>()
        var remaining = size
        while (remaining > 0) {
            parts.add(0, (remaining and 0xff).toByte())
            remaining = remaining ushr 8
        }
        return byteArrayOf((0x80 or parts.size).toByte()) +
            ByteArray(parts.size) { index -> parts[index] }
    }
}

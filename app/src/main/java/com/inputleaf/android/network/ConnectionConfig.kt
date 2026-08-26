package com.inputleaf.android.network

/**
 * Immutable connection transport settings chosen by the user.
 *
 * @param tlsEnabled when false, only plain TCP; when true, only TLS (no fallback).
 * @param pinnedFingerprint SHA-256 of a previously trusted server cert, or null for TOFU.
 * @param clientCertificate PKCS12 bytes for optional mutual TLS, or null.
 * @param clientCertificatePassword password for [clientCertificate], or null.
 */
data class ConnectionConfig(
    val tlsEnabled: Boolean = false,
    val pinnedFingerprint: String? = null,
    val clientCertificate: ByteArray? = null,
    val clientCertificatePassword: CharArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConnectionConfig) return false
        return tlsEnabled == other.tlsEnabled &&
            pinnedFingerprint == other.pinnedFingerprint &&
            clientCertificate.contentEquals(other.clientCertificate) &&
            clientCertificatePassword.contentEquals(other.clientCertificatePassword)
    }

    override fun hashCode(): Int {
        var result = tlsEnabled.hashCode()
        result = 31 * result + (pinnedFingerprint?.hashCode() ?: 0)
        result = 31 * result + (clientCertificate?.contentHashCode() ?: 0)
        result = 31 * result + (clientCertificatePassword?.contentHashCode() ?: 0)
        return result
    }
}

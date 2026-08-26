package com.inputleaf.android.network

enum class ConnectionTransportPolicy(val storageValue: String) {
    AUTO("auto"),
    TLS_ONLY("tls_only"),
    PLAIN_ONLY("plain_only");

    companion object {
        fun fromStorage(value: String?): ConnectionTransportPolicy =
            entries.firstOrNull { it.storageValue == value } ?: AUTO
    }

    fun shouldRetry(reason: ConnectResult.FailureReason): Boolean =
        this == AUTO && when (reason) {
            ConnectResult.FailureReason.NETWORK,
            ConnectResult.FailureReason.HANDSHAKE -> true
            ConnectResult.FailureReason.TLS_AGAINST_PLAIN_SERVER,
            ConnectResult.FailureReason.CERTIFICATE_MISMATCH,
            ConnectResult.FailureReason.CLIENT_CERT_REQUIRED,
            ConnectResult.FailureReason.INCOMPATIBLE,
            ConnectResult.FailureReason.BUSY -> false
        }

    fun shouldFallbackWithinAttempt(reason: ConnectResult.FailureReason): Boolean =
        this == AUTO && when (reason) {
            // Plain TCP to a TLS Deskflow deadlocks until the protocol timeout.
            // Only a confirmed "this is not TLS" error should try plaintext.
            ConnectResult.FailureReason.TLS_AGAINST_PLAIN_SERVER -> true
            ConnectResult.FailureReason.NETWORK,
            ConnectResult.FailureReason.HANDSHAKE,
            ConnectResult.FailureReason.CERTIFICATE_MISMATCH,
            ConnectResult.FailureReason.CLIENT_CERT_REQUIRED,
            ConnectResult.FailureReason.INCOMPATIBLE,
            ConnectResult.FailureReason.BUSY -> false
        }
}

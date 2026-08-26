package com.inputleaf.android.network

sealed class ConnectResult {
    data class Ok(
        val banner: InputLeapConnection.ServerBanner,
        val transport: ServerTransport,
    ) : ConnectResult()

    data object RejectedByUser : ConnectResult()

    data class Failed(
        val reason: FailureReason,
        val detail: String? = null,
    ) : ConnectResult()

    enum class FailureReason {
        NETWORK,
        TLS_AGAINST_PLAIN_SERVER,
        CERTIFICATE_MISMATCH,
        HANDSHAKE,
        INCOMPATIBLE,
        BUSY,
    }
}

enum class ServerTransport {
    TLS,
    PLAIN,
}

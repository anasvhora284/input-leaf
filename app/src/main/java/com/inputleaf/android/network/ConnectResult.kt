package com.inputleaf.android.network

sealed class ConnectResult {
    data class Ok(
        val banner: InputLeapConnection.ServerBanner,
        val transport: ServerTransport,
    ) : ConnectResult()

    data object RejectedByUser : ConnectResult()

    data class Failed(val reason: FailureReason, val detail: String? = null) : ConnectResult()

    enum class FailureReason {
        NETWORK,
        /** TLS was enabled but the peer spoke plain Barrier/Deskflow. */
        TLS_AGAINST_PLAIN_SERVER,
        /** Protocol handshake incomplete or rejected (EICV/EBSY). */
        HANDSHAKE,
        INCOMPATIBLE,
        BUSY,
    }
}

enum class ServerTransport {
    TLS,
    PLAIN,
}

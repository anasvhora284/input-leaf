package com.inputleaf.android.network

sealed class ConnectResult {
    data class Ok(val banner: InputLeapConnection.ServerBanner, val transport: ServerTransport) : ConnectResult()
    data object RejectedByUser : ConnectResult()
    data object NetworkError : ConnectResult()
}

enum class ServerTransport {
    TLS,
    PLAIN,
}

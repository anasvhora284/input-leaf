package com.inputleaf.android.model

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val serverIp: String) : ConnectionState()
    data class Handshaking(val serverIp: String) : ConnectionState()
    data class Idle(val serverIp: String, val serverName: String) : ConnectionState()
    data class Active(val serverIp: String, val serverName: String) : ConnectionState()
}

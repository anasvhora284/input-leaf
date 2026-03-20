package com.inputleaf.android.service

import com.inputleaf.android.model.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ConnectionStateMachine {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    @Volatile private var keepAliveMissed = 0

    fun onConnecting(ip: String) { _state.value = ConnectionState.Connecting(ip) }

    fun onHandshaking(ip: String) { _state.value = ConnectionState.Handshaking(ip) }

    fun onIdle(ip: String, serverName: String) {
        keepAliveMissed = 0
        _state.value = ConnectionState.Idle(ip, serverName)
    }

    fun onActive() {
        val current = _state.value
        if (current is ConnectionState.Active) {
            println("StateMachine: Duplicate kMsgCEnter received — ignoring")
            return
        }
        val (ip, name) = when (current) {
            is ConnectionState.Idle -> current.serverIp to current.serverName
            else -> {
                println("StateMachine: kMsgCEnter received in unexpected state: $current — ignoring")
                return
            }
        }
        _state.value = ConnectionState.Active(ip, name)
    }

    fun onLeave() {
        val current = _state.value as? ConnectionState.Active ?: return
        _state.value = ConnectionState.Idle(current.serverIp, current.serverName)
    }

    fun onKeepAlive() { keepAliveMissed = 0 }

    fun onKeepAliveMiss(): Boolean {
        keepAliveMissed++
        return keepAliveMissed >= 3
    }

    fun onDisconnected() {
        keepAliveMissed = 0
        _state.value = ConnectionState.Disconnected
    }
}

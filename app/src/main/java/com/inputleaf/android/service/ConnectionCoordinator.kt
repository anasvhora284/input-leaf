package com.inputleaf.android.service

import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.InputLeapEvent
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns connection-generation, retry, keepalive, and input-routing decisions.
 * Android-specific effects remain the responsibility of [ConnectionService].
 */
class ConnectionCoordinator(
    private val stateMachine: ConnectionStateMachine = ConnectionStateMachine(),
) {
    sealed class Effect {
        data class RouteInput(val event: InputLeapEvent) : Effect()
        object SendKeepAlive : Effect()
        object ShowCursor : Effect()
        object HideCursor : Effect()
        object RestoreIme : Effect()
        object CloseConnection : Effect()
        object ScheduleRetry : Effect()
    }

    val state: StateFlow<ConnectionState> = stateMachine.state

    @Volatile
    private var generation = 0
    @Volatile
    private var mouseEnabled = true
    @Volatile
    private var keyboardEnabled = true

    fun beginConnection(): Int = ++generation

    fun isCurrent(connectionGeneration: Int): Boolean =
        connectionGeneration == generation

    fun onConnecting(connectionGeneration: Int, serverIp: String): Boolean {
        if (!isCurrent(connectionGeneration)) return false
        stateMachine.onConnecting(serverIp)
        return true
    }

    fun onConnected(connectionGeneration: Int, serverIp: String, screenName: String): Boolean {
        if (!isCurrent(connectionGeneration)) return false
        stateMachine.onHandshaking(serverIp)
        stateMachine.onIdle(serverIp, screenName)
        return true
    }

    fun onConnectionRejected(connectionGeneration: Int) {
        if (isCurrent(connectionGeneration)) stateMachine.onDisconnected()
    }

    fun onConnectionFailed(connectionGeneration: Int, retry: Boolean): List<Effect> {
        if (!isCurrent(connectionGeneration)) return emptyList()
        stateMachine.onDisconnected()
        return if (retry) listOf(Effect.ScheduleRetry) else emptyList()
    }

    fun onEvent(connectionGeneration: Int, event: InputLeapEvent): List<Effect> {
        if (!isCurrent(connectionGeneration)) return emptyList()
        return when (event) {
            is InputLeapEvent.Enter -> {
                stateMachine.onActive()
                stateMachine.onKeepAlive()
                if (mouseEnabled) listOf(Effect.ShowCursor) else emptyList()
            }
            is InputLeapEvent.Leave -> {
                stateMachine.onLeave()
                listOf(Effect.HideCursor)
            }
            is InputLeapEvent.KeepAlive -> {
                stateMachine.onKeepAlive()
                listOf(Effect.SendKeepAlive)
            }
            is InputLeapEvent.MouseMoveAbs,
            is InputLeapEvent.MouseMoveRel,
            is InputLeapEvent.MouseDown,
            is InputLeapEvent.MouseUp,
            is InputLeapEvent.MouseWheel,
            -> routeIfEnabled(event, mouseEnabled)
            is InputLeapEvent.KeyDown,
            is InputLeapEvent.KeyUp,
            is InputLeapEvent.KeyRepeat,
            -> routeIfEnabled(event, keyboardEnabled)
            is InputLeapEvent.Unhandled -> {
                if (event.tag == "__DISCONNECTED__") onUnexpectedDisconnect(connectionGeneration)
                else emptyList()
            }
            else -> route(event)
        }
    }

    fun onKeepAliveMiss(connectionGeneration: Int): List<Effect> {
        if (!isCurrent(connectionGeneration)) return emptyList()
        if (!stateMachine.onKeepAliveMiss()) return emptyList()
        stateMachine.onDisconnected()
        return listOf(Effect.CloseConnection, Effect.HideCursor, Effect.RestoreIme)
    }

    fun onUnexpectedDisconnect(connectionGeneration: Int): List<Effect> {
        if (!isCurrent(connectionGeneration)) return emptyList()
        stateMachine.onDisconnected()
        return listOf(Effect.HideCursor, Effect.RestoreIme, Effect.ScheduleRetry)
    }

    fun onUserDisconnect() {
        // Invalidating the generation makes all pending callbacks stale.
        generation++
        stateMachine.onDisconnected()
    }

    fun setMouseEnabled(enabled: Boolean): List<Effect> {
        mouseEnabled = enabled
        return if (enabled) emptyList() else listOf(Effect.HideCursor)
    }

    fun setKeyboardEnabled(enabled: Boolean) {
        keyboardEnabled = enabled
    }

    private fun routeIfEnabled(event: InputLeapEvent, enabled: Boolean): List<Effect> =
        if (enabled) route(event) else emptyList()

    private fun route(event: InputLeapEvent): List<Effect> {
        stateMachine.onKeepAlive()
        return listOf(Effect.RouteInput(event))
    }
}

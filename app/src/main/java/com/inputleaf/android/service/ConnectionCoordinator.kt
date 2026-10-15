package com.inputleaf.android.service

import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.network.ConnectResult
import com.inputleaf.android.network.ConnectionTransportPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface CoordinatorEventResult {
    data class Enter(val x: Int, val y: Int) : CoordinatorEventResult
    object Leave : CoordinatorEventResult
    object KeepAlive : CoordinatorEventResult
    object InfoAck : CoordinatorEventResult
    data class QueryInfo(val width: Int, val height: Int, val x: Int, val y: Int) : CoordinatorEventResult
    data class MouseMotion(val event: InputLeapEvent, val cursorX: Float, val cursorY: Float) : CoordinatorEventResult
    data class Input(val event: InputLeapEvent) : CoordinatorEventResult
    data class Disconnected(val retry: ConnectFailureAction.Retry?) : CoordinatorEventResult
    object Ignored : CoordinatorEventResult
}

data class ConnectAttemptPlan(
    val generation: Int,
    val initialDelayMs: Long,
)

sealed interface ConnectFailureAction {
    data class Retry(val delayMs: Long, val generation: Int) : ConnectFailureAction
    data class Terminal(val reason: ConnectResult.FailureReason, val detail: String?) : ConnectFailureAction
    object Ignore : ConnectFailureAction
}

sealed interface ScreenBoundsUpdatePlan {
    data class SendDinf(val width: Int, val height: Int, val x: Int, val y: Int) : ScreenBoundsUpdatePlan
    object None : ScreenBoundsUpdatePlan
}

/**
 * Pure domain coordinator for connection lifecycle, state transitions, retry backoff,
 * keepalive misses, coordinate clamping, and input event gating.
 *
 * Contains zero dependencies on Android framework classes (android.*) so all coordination
 * logic can be tested deterministically on the JVM.
 */
class ConnectionCoordinator {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    private var connectGeneration: Int = 0
    val generation: Int get() = connectGeneration

    private var retryAttempt: Int = 0
    val currentRetryAttempt: Int get() = retryAttempt

    private var keepAliveMissed: Int = 0
    val currentKeepAliveMissed: Int get() = keepAliveMissed

    private var userInitiatedDisconnect: Boolean = false
    val isUserInitiatedDisconnect: Boolean get() = userInitiatedDisconnect

    private var infoAckPending: Boolean = false
    val isInfoAckPending: Boolean get() = infoAckPending

    private var pointerOnScreen: Boolean = false
    val isPointerOnScreen: Boolean get() = pointerOnScreen

    var mouseEnabled: Boolean = true
    var keyboardEnabled: Boolean = true
    var absolutePointer: Boolean = false

    var screenWidth: Int = 0
        private set
    var screenHeight: Int = 0
        private set
    var currentMouseX: Float = 0f
        private set
    var currentMouseY: Float = 0f
        private set

    private var activeServerIp: String? = null
    val serverIp: String? get() = activeServerIp

    private var activeScreenName: String? = null
    val screenName: String? get() = activeScreenName

    fun isCurrentGeneration(generation: Int): Boolean = generation == connectGeneration

    fun startConnect(
        serverIp: String,
        screenName: String,
        forceReconnect: Boolean = false,
    ): ConnectAttemptPlan {
        connectGeneration++
        userInitiatedDisconnect = false
        activeServerIp = serverIp
        activeScreenName = screenName
        _state.value = ConnectionState.Connecting(serverIp)
        val initialDelayMs = if (forceReconnect) 150L else 0L
        return ConnectAttemptPlan(
            generation = connectGeneration,
            initialDelayMs = initialDelayMs,
        )
    }

    fun onConnectOk(
        serverIp: String,
        screenName: String,
        generation: Int,
    ): Boolean {
        if (generation != connectGeneration) return false
        retryAttempt = 0
        keepAliveMissed = 0
        activeServerIp = serverIp
        activeScreenName = screenName
        _state.value = ConnectionState.Handshaking(serverIp)
        _state.value = ConnectionState.Idle(serverIp, screenName)
        return true
    }

    fun onConnectRejected(generation: Int): Boolean {
        if (generation != connectGeneration) return false
        _state.value = ConnectionState.Disconnected
        clearActiveSession()
        return true
    }

    fun onConnectFailed(
        reason: ConnectResult.FailureReason,
        detail: String?,
        policy: ConnectionTransportPolicy,
        generation: Int,
    ): ConnectFailureAction {
        if (generation != connectGeneration || userInitiatedDisconnect) {
            return ConnectFailureAction.Ignore
        }
        _state.value = ConnectionState.Disconnected
        return if (policy.shouldRetry(reason)) {
            val delayMs = RetryDelayCalculator.getDelay(retryAttempt)
            retryAttempt++
            ConnectFailureAction.Retry(delayMs, generation)
        } else {
            clearActiveSession()
            ConnectFailureAction.Terminal(reason, detail)
        }
    }

    fun processEvent(
        event: InputLeapEvent,
        generation: Int,
        policy: ConnectionTransportPolicy = ConnectionTransportPolicy.AUTO,
    ): CoordinatorEventResult {
        if (generation != connectGeneration) return CoordinatorEventResult.Ignored

        return when (event) {
            is InputLeapEvent.Enter -> {
                pointerOnScreen = true
                keepAliveMissed = 0
                val current = _state.value
                if (current !is ConnectionState.Active) {
                    val ip = activeServerIp ?: (current as? ConnectionState.Idle)?.serverIp ?: ""
                    val name = activeScreenName ?: (current as? ConnectionState.Idle)?.serverName ?: ""
                    _state.value = ConnectionState.Active(ip, name)
                }
                CoordinatorEventResult.Enter(event.x, event.y)
            }
            is InputLeapEvent.Leave -> {
                CoordinatorEventResult.Leave
            }
            is InputLeapEvent.KeepAlive -> {
                keepAliveMissed = 0
                CoordinatorEventResult.KeepAlive
            }
            is InputLeapEvent.InfoAck -> {
                infoAckPending = false
                CoordinatorEventResult.InfoAck
            }
            is InputLeapEvent.QueryInfo -> {
                CoordinatorEventResult.QueryInfo(
                    width = screenWidth,
                    height = screenHeight,
                    x = currentMouseX.toInt(),
                    y = currentMouseY.toInt(),
                )
            }
            is InputLeapEvent.MouseMoveAbs -> {
                if (!mouseEnabled || infoAckPending) return CoordinatorEventResult.Ignored
                keepAliveMissed = 0
                val clampedX = if (screenWidth > 0) event.x.toFloat().coerceIn(0f, screenWidth.toFloat()) else event.x.toFloat()
                val clampedY = if (screenHeight > 0) event.y.toFloat().coerceIn(0f, screenHeight.toFloat()) else event.y.toFloat()
                currentMouseX = clampedX
                currentMouseY = clampedY
                CoordinatorEventResult.MouseMotion(event, clampedX, clampedY)
            }
            is InputLeapEvent.MouseMoveRel -> {
                if (!mouseEnabled || infoAckPending) return CoordinatorEventResult.Ignored
                keepAliveMissed = 0
                val newX = if (screenWidth > 0) (currentMouseX + event.dx).coerceIn(0f, screenWidth.toFloat()) else currentMouseX + event.dx
                val newY = if (screenHeight > 0) (currentMouseY + event.dy).coerceIn(0f, screenHeight.toFloat()) else currentMouseY + event.dy
                currentMouseX = newX
                currentMouseY = newY
                CoordinatorEventResult.MouseMotion(event, newX, newY)
            }
            is InputLeapEvent.MouseDown, is InputLeapEvent.MouseUp, is InputLeapEvent.MouseWheel -> {
                if (!mouseEnabled) return CoordinatorEventResult.Ignored
                keepAliveMissed = 0
                CoordinatorEventResult.Input(event)
            }
            is InputLeapEvent.KeyDown, is InputLeapEvent.KeyUp, is InputLeapEvent.KeyRepeat -> {
                if (!keyboardEnabled) return CoordinatorEventResult.Ignored
                keepAliveMissed = 0
                CoordinatorEventResult.Input(event)
            }
            is InputLeapEvent.Unhandled -> {
                if (event.tag == "__DISCONNECTED__") {
                    if (userInitiatedDisconnect) return CoordinatorEventResult.Ignored
                    pointerOnScreen = false
                    _state.value = ConnectionState.Disconnected
                    val retry = if (policy.shouldRetry(ConnectResult.FailureReason.NETWORK)) {
                        val delayMs = RetryDelayCalculator.getDelay(retryAttempt)
                        retryAttempt++
                        ConnectFailureAction.Retry(delayMs, generation)
                    } else null
                    CoordinatorEventResult.Disconnected(retry)
                } else {
                    keepAliveMissed = 0
                    CoordinatorEventResult.Input(event)
                }
            }
            else -> {
                keepAliveMissed = 0
                CoordinatorEventResult.Input(event)
            }
        }
    }

    fun onLeaveDebounceExpired(generation: Int): Boolean {
        if (generation != connectGeneration) return false
        pointerOnScreen = false
        val current = _state.value
        if (current is ConnectionState.Active) {
            _state.value = ConnectionState.Idle(current.serverIp, current.serverName)
        }
        return true
    }

    fun onKeepAliveMiss(generation: Int): Boolean {
        if (generation != connectGeneration) return false
        keepAliveMissed++
        if (keepAliveMissed >= 4) {
            _state.value = ConnectionState.Disconnected
            pointerOnScreen = false
            return true
        }
        return false
    }

    fun updateScreenBounds(width: Int, height: Int): ScreenBoundsUpdatePlan {
        if (screenWidth == width && screenHeight == height) return ScreenBoundsUpdatePlan.None
        screenWidth = width
        screenHeight = height
        if (width > 0) currentMouseX = currentMouseX.coerceIn(0f, width.toFloat())
        if (height > 0) currentMouseY = currentMouseY.coerceIn(0f, height.toFloat())
        val connected = _state.value is ConnectionState.Idle || _state.value is ConnectionState.Active
        return if (connected) {
            infoAckPending = true
            ScreenBoundsUpdatePlan.SendDinf(width, height, currentMouseX.toInt(), currentMouseY.toInt())
        } else {
            ScreenBoundsUpdatePlan.None
        }
    }

    fun clearActiveSession() {
        activeServerIp = null
        activeScreenName = null
    }

    fun setInitialCoordinates(x: Float, y: Float) {
        currentMouseX = if (screenWidth > 0) x.coerceIn(0f, screenWidth.toFloat()) else x
        currentMouseY = if (screenHeight > 0) y.coerceIn(0f, screenHeight.toFloat()) else y
    }

    fun disconnect(): Int {
        userInitiatedDisconnect = true
        infoAckPending = false
        pointerOnScreen = false
        clearActiveSession()
        connectGeneration++
        keepAliveMissed = 0
        _state.value = ConnectionState.Disconnected
        return connectGeneration
    }
}

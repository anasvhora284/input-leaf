package com.inputleaf.android.service

import android.app.Service
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.network.ConnectResult
import com.inputleaf.android.network.ConnectionTransportPolicy
import com.inputleaf.android.network.InputLeapConnection
import com.inputleaf.android.network.ServerTransport
import com.inputleaf.android.network.TlsFingerprintManager
import com.inputleaf.android.shizuku.ShizukuInputInjector
import com.inputleaf.android.storage.AppPreferences
import com.inputleaf.android.storage.ClientCertificateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import rikka.shizuku.Shizuku
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ConnectionService"
private const val KEEPALIVE_POLL_MS = 5_000L
private val RETRY_DELAYS_MS = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)

class ConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val stateMachine = ConnectionStateMachine()
    private var connection: InputLeapConnection? = null
    private var injector: com.inputleaf.android.inject.InputInjector? = null
    private var keepAliveJob: Job? = null
    private var connectJob: Job? = null
    private var eventLoopJob: Job? = null
    private var retryJob: Job? = null
    private var retryAttempt = 0
    private var connectGeneration = 0
    private var userInitiatedDisconnect = false
    private var cursorOverlayEnabled = false
    private var mouseEnabled = true
    private var keyboardEnabled = true
    private var previousImeId: String? = null
    private var previousImeLabel: String? = null
    private var isUsingAccessibilityIme = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var currentMouseX = 0f
    private var currentMouseY = 0f
    private var activeServerIp: String? = null
    private var activeScreenName: String? = null
    private var shizukuRecoveryJob: Job? = null
    private lateinit var prefs: AppPreferences

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku binder received in ConnectionService")
        handleShizukuRestarted()
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder died in ConnectionService")
        handleShizukuDied()
    }

    val state: StateFlow<ConnectionState> get() = stateMachine.state

    inner class LocalBinder : Binder() { fun getService() = this@ConnectionService }
    override fun onBind(intent: Intent): IBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        NotificationHelper.createChannel(this)
        observeState()

        try {
            Shizuku.addBinderReceivedListener(shizukuBinderReceivedListener)
            Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to register Shizuku binder listeners", e)
        }

        val bounds = getScreenBounds()
        screenWidth = bounds.width()
        screenHeight = bounds.height()

        scope.launch {
            cursorOverlayEnabled = prefs.showCursor.first()
            Log.d(TAG, "Cursor overlay initial value: $cursorOverlayEnabled")
            prefs.showCursor.collect { enabled ->
                cursorOverlayEnabled = enabled
                Log.d(TAG, "Cursor overlay enabled changed: $enabled")
            }
        }

        scope.launch {
            mouseEnabled = prefs.mouseEnabled.first()
            prefs.mouseEnabled.collect { enabled ->
                mouseEnabled = enabled
                if (!enabled) hideCursorOverlay()
            }
        }

        scope.launch {
            keyboardEnabled = prefs.keyboardEnabled.first()
            prefs.keyboardEnabled.collect { enabled ->
                keyboardEnabled = enabled
            }
        }

        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, CursorOverlayService::class.java))
        }
    }

    private fun observeState() = scope.launch {
        stateMachine.state.collect { state ->
            val notif = NotificationHelper.build(this@ConnectionService, state)
            getSystemService(android.app.NotificationManager::class.java)
                .notify(NOTIF_ID, notif)
        }
    }

    var onFingerprintConfirmationRequired: (suspend (ip: String, fp: String, oldFp: String?) -> Boolean)? = null
    var onConnectionRejected: (() -> Unit)? = null
    var onConnectionFailed: ((reason: ConnectResult.FailureReason, detail: String?) -> Unit)? = null

    fun connect(serverIp: String, screenName: String, force: Boolean = false) {
        val currentState = stateMachine.state.value
        if (!force) {
            if (currentState is ConnectionState.Connecting && currentState.serverIp == serverIp) return
            if (currentState is ConnectionState.Handshaking && currentState.serverIp == serverIp) return
            if (currentState is ConnectionState.Idle && currentState.serverIp == serverIp) return
            if (currentState is ConnectionState.Active && currentState.serverIp == serverIp) return
        }

        userInitiatedDisconnect = false
        activeServerIp = serverIp
        activeScreenName = screenName
        val generation = ++connectGeneration
        cancelPendingJobs(keepConnection = false)
        connection?.close()
        connection = null

        connectJob = scope.launch {
            if (force) {
                delay(150)
            }
            performConnect(serverIp, screenName, generation)
        }
    }

    fun reconnect(serverIp: String, screenName: String) {
        connect(serverIp, screenName, force = true)
    }

    private suspend fun performConnect(serverIp: String, screenName: String, generation: Int) {
        if (generation != connectGeneration) return
        var activePolicy = ConnectionTransportPolicy.AUTO
        try {
            startForeground(NOTIF_ID, NotificationHelper.build(this@ConnectionService, stateMachine.state.value))
            stateMachine.onConnecting(serverIp)

            val storedFp = prefs.fingerprintFor(serverIp).first()
            activePolicy = prefs.connectionTransportPolicy.first()
            val clientCertificate =
                if (activePolicy == ConnectionTransportPolicy.PLAIN_ONLY) {
                    null
                } else {
                    runCatching {
                        val store = ClientCertificateStore(this@ConnectionService)
                        store.ensureGenerated()
                        store.load()
                    }.onFailure { error ->
                        Log.w(TAG, "Failed to load client certificate", error)
                    }.getOrNull()
                }
            val cachedTransport =
                if (activePolicy == ConnectionTransportPolicy.AUTO) {
                    prefs.transportFor(serverIp).first()?.let { mode ->
                        when (mode.lowercase()) {
                            "tls" -> ServerTransport.TLS
                            "plain" -> ServerTransport.PLAIN
                            else -> null
                        }
                    }
                } else {
                    null
                }

            val conn = InputLeapConnection(
                ip = serverIp,
                preferredTransport = cachedTransport,
                pinnedFingerprint = storedFp,
                transportPolicy = activePolicy,
                clientCertificate = clientCertificate,
            ) { cert ->
                val newFp = TlsFingerprintManager.fingerprintOf(cert)
                val trusted = when {
                    storedFp == null -> {
                        onFingerprintConfirmationRequired?.invoke(serverIp, newFp, null) ?: false
                    }
                    storedFp == newFp -> true
                    else -> {
                        onFingerprintConfirmationRequired?.invoke(serverIp, newFp, storedFp) ?: false
                    }
                }
                if (trusted) prefs.saveFingerprint(serverIp, newFp)
                trusted
            }

            val bounds = getScreenBounds()
            val result = try {
                conn.connect(
                    screenName = screenName,
                    screenWidth = bounds.width(),
                    screenHeight = bounds.height(),
                )
            } finally {
                clientCertificate?.clear()
            }
            if (generation != connectGeneration) {
                conn.close()
                return
            }

            when (result) {
                is ConnectResult.Ok -> {
                    retryAttempt = 0
                    connection = conn
                    prefs.saveTransport(serverIp, result.transport.name.lowercase())
                    stateMachine.onHandshaking(serverIp)
                    stateMachine.onIdle(serverIp, screenName)
                    conn.clearHandshakeTimeout()
                    startEventLoop(conn, serverIp, screenName, generation)
                    startKeepAliveMonitor(conn, generation)
                    autoSwitchImeToOurs()
                }
                is ConnectResult.RejectedByUser -> {
                    conn.close()
                    stateMachine.onDisconnected()
                    onConnectionRejected?.invoke()
                    if (shouldClearActiveSession(ConnectAttemptOutcome.Rejected)) {
                        clearActiveSession()
                    }
                }
                is ConnectResult.Failed -> {
                    conn.close()
                    stateMachine.onDisconnected()
                    if (activePolicy.shouldRetry(result.reason)) {
                        prefs.clearTransport(serverIp)
                        scheduleRetry(serverIp, screenName, generation)
                    } else {
                        onConnectionFailed?.invoke(result.reason, result.detail)
                        if (shouldClearActiveSession(ConnectAttemptOutcome.TerminalFailure)) {
                            clearActiveSession()
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != connectGeneration) return
            Log.w(TAG, "Connection to $serverIp failed: ${e.javaClass.simpleName}: ${e.message}", e)
            stateMachine.onDisconnected()
            if (activePolicy.shouldRetry(ConnectResult.FailureReason.NETWORK)) {
                scheduleRetry(serverIp, screenName, generation)
            } else {
                onConnectionFailed?.invoke(ConnectResult.FailureReason.NETWORK, e.message)
                if (shouldClearActiveSession(ConnectAttemptOutcome.TerminalFailure)) {
                    clearActiveSession()
                }
            }
        }
    }

    private fun startEventLoop(
        conn: InputLeapConnection,
        ip: String,
        screenName: String,
        generation: Int,
    ) {
        eventLoopJob?.cancel()
        eventLoopJob = scope.launch(Dispatchers.IO) {
            conn.events.collect { event ->
                if (generation != connectGeneration) return@collect
                when (event) {
                    is InputLeapEvent.Enter -> {
                        stateMachine.onActive()
                        stateMachine.onKeepAlive()
                        if (mouseEnabled) showCursorOverlay()
                    }
                    is InputLeapEvent.Leave -> {
                        stateMachine.onLeave()
                        hideCursorOverlay()
                    }
                    is InputLeapEvent.KeepAlive -> {
                        stateMachine.onKeepAlive()
                        conn.sendKeepAlive()
                    }
                    is InputLeapEvent.MouseMoveAbs -> {
                        if (!mouseEnabled) return@collect
                        stateMachine.onKeepAlive()
                        currentMouseX = event.x.toFloat()
                        currentMouseY = event.y.toFloat()
                        updateCursorPosition(currentMouseX, currentMouseY)
                        dispatchInput(event)
                    }
                    is InputLeapEvent.MouseMoveRel -> {
                        if (!mouseEnabled) return@collect
                        stateMachine.onKeepAlive()
                        currentMouseX = (currentMouseX + event.dx).coerceIn(0f, screenWidth.toFloat())
                        currentMouseY = (currentMouseY + event.dy).coerceIn(0f, screenHeight.toFloat())
                        updateCursorPosition(currentMouseX, currentMouseY)
                        dispatchInput(event)
                    }
                    is InputLeapEvent.MouseDown, is InputLeapEvent.MouseUp, is InputLeapEvent.MouseWheel -> {
                        if (!mouseEnabled) return@collect
                        stateMachine.onKeepAlive()
                        dispatchInput(event)
                    }
                    is InputLeapEvent.KeyDown, is InputLeapEvent.KeyUp, is InputLeapEvent.KeyRepeat -> {
                        if (!keyboardEnabled) return@collect
                        stateMachine.onKeepAlive()
                        dispatchInput(event)
                    }
                    is InputLeapEvent.Unhandled -> if (event.tag == "__DISCONNECTED__") {
                        if (generation != connectGeneration || userInitiatedDisconnect) return@collect
                        stateMachine.onDisconnected()
                        hideCursorOverlay()
                        restorePreviousIme()
                        scheduleRetry(ip, screenName, generation)
                    }
                    else -> {
                        stateMachine.onKeepAlive()
                        dispatchInput(event)
                    }
                }
            }
        }
    }

    fun setCursorOverlayEnabled(enabled: Boolean) {
        cursorOverlayEnabled = enabled
        if (enabled && stateMachine.state.value is ConnectionState.Active) {
            showCursorOverlay()
        } else if (!enabled) {
            hideCursorOverlay()
        }
    }

    private fun showCursorOverlay() {
        if (!cursorOverlayEnabled) return
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlays - permission not granted")
            return
        }
        CursorOverlayService.show()
    }

    private fun hideCursorOverlay() {
        CursorOverlayService.hide()
    }

    private fun updateCursorPosition(x: Float, y: Float) {
        if (!cursorOverlayEnabled) return
        CursorOverlayService.updatePosition(x, y)
    }

    fun setInjector(injector: com.inputleaf.android.inject.InputInjector) {
        if (this.injector != null && this.injector != injector) {
            this.injector?.disconnect()
            if (this.injector is com.inputleaf.android.inject.AccessibilityInputInjector &&
                injector !is com.inputleaf.android.inject.AccessibilityInputInjector
            ) {
                restorePreviousIme()
            }
        }
        this.injector = injector
        if (injector is ShizukuInputInjector) {
            injector.onServiceDisconnectedCallback = {
                handleShizukuServiceDisconnected()
            }
        }
        Log.i(TAG, "Input injector set to: ${injector.name}")
    }

    private fun dispatchInput(event: InputLeapEvent) {
        injector?.send(event)
    }

    private fun startKeepAliveMonitor(conn: InputLeapConnection, generation: Int) {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (generation == connectGeneration) {
                delay(KEEPALIVE_POLL_MS)
                if (generation != connectGeneration) break
                if (stateMachine.onKeepAliveMiss()) {
                    Log.w(TAG, "Keep-alive timeout — disconnecting")
                    conn.close()
                    stateMachine.onDisconnected()
                    hideCursorOverlay()
                    restorePreviousIme()
                    break
                }
            }
        }
    }

    private fun scheduleRetry(ip: String, screenName: String, generation: Int) {
        if (userInitiatedDisconnect || generation != connectGeneration) return
        retryJob?.cancel()
        val delayMs = RETRY_DELAYS_MS[retryAttempt.coerceIn(0, RETRY_DELAYS_MS.lastIndex)]
        retryAttempt++
        retryJob = scope.launch {
            delay(delayMs)
            if (userInitiatedDisconnect || generation != connectGeneration) return@launch
            connect(ip, screenName)
        }
    }

    private fun cancelPendingJobs(keepConnection: Boolean) {
        retryJob?.cancel()
        retryJob = null
        eventLoopJob?.cancel()
        eventLoopJob = null
        connectJob?.cancel()
        connectJob = null
        keepAliveJob?.cancel()
        keepAliveJob = null
        if (!keepConnection) {
            connection?.close()
            connection = null
        }
    }

    private fun clearActiveSession() {
        activeServerIp = null
        activeScreenName = null
    }

    fun disconnect() {
        userInitiatedDisconnect = true
        clearActiveSession()
        shizukuRecoveryJob?.cancel()
        shizukuRecoveryJob = null
        connectGeneration++
        cancelPendingJobs(keepConnection = false)
        injector?.disconnect()
        injector = null
        hideCursorOverlay()
        restorePreviousIme()
        stateMachine.onDisconnected()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) disconnect()
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun getScreenBounds(): Rect {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds
        } else {
            val size = Point()
            wm.defaultDisplay.getSize(size)
            Rect(0, 0, size.x, size.y)
        }
    }

    private fun autoSwitchImeToOurs() {
        if (injector !is com.inputleaf.android.inject.AccessibilityInputInjector) {
            return
        }
        try {
            val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            val ourIme = android.content.ComponentName(this, com.inputleaf.android.inject.InputLeafIME::class.java).flattenToShortString()
            if (currentIme != ourIme) {
                isUsingAccessibilityIme = true
                previousImeId = currentIme
                val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                val list = imm.enabledInputMethodList
                for (info in list) {
                    if (info.id == currentIme) {
                        previousImeLabel = info.loadLabel(packageManager).toString()
                        break
                    }
                }
                if (com.inputleaf.android.inject.AccessibilityInputService.isServiceRunning()) {
                    com.inputleaf.android.inject.AccessibilityInputService.targetImeLabelToSelect = "Input Leaf Keyboard"
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    imm.showInputMethodPicker()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-switch IME", e)
        }
    }

    private fun restorePreviousIme() {
        if (!isUsingAccessibilityIme) {
            return
        }
        isUsingAccessibilityIme = false
        try {
            val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            val ourIme = android.content.ComponentName(this, com.inputleaf.android.inject.InputLeafIME::class.java).flattenToShortString()
            if (currentIme == ourIme && previousImeLabel != null) {
                if (com.inputleaf.android.inject.AccessibilityInputService.isServiceRunning()) {
                    com.inputleaf.android.inject.AccessibilityInputService.targetImeLabelToSelect = previousImeLabel
                }
                val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    imm.showInputMethodPicker()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore previous IME", e)
        }
    }

    private fun handleShizukuDied() {
        if (injector is ShizukuInputInjector) {
            Log.w(TAG, "Shizuku binder died while using Shizuku injector; disconnecting injector")
            injector?.disconnect()
            handleShizukuServiceDisconnected()
        }
    }

    private fun handleShizukuServiceDisconnected() {
        Log.w(TAG, "Shizuku UserService disconnected mid-session")
        triggerShizukuRecovery(delayMs = 300L)
    }

    private fun handleShizukuRestarted() {
        Log.i(TAG, "Shizuku service restarted")
        triggerShizukuRecovery(delayMs = 600L)
    }

    private fun triggerShizukuRecovery(delayMs: Long) {
        val ip = activeServerIp ?: return
        val name = activeScreenName ?: return
        if (userInitiatedDisconnect) return

        shizukuRecoveryJob?.cancel()
        shizukuRecoveryJob = scope.launch {
            // Check if user specifically configured accessibility mode in prefs
            val preferredMethod = prefs.inputMethod.first()
            if (preferredMethod == "accessibility") {
                Log.d(TAG, "Skipping Shizuku recovery because preferred method is Accessibility")
                return@launch
            }

            delay(delayMs)
            if (userInitiatedDisconnect || activeServerIp != ip) return@launch

            Log.i(TAG, "Attempting auto-recovery of Shizuku session to $ip")
            val bounds = getScreenBounds()
            val newInjector = ShizukuInputInjector(bounds.width(), bounds.height())

            if (newInjector.isAvailable() && newInjector.connect()) {
                Log.i(TAG, "Shizuku injector recovered successfully; reconnecting session to $ip")
                setInjector(newInjector)
                reconnect(ip, name)
            } else {
                Log.w(TAG, "Could not recover Shizuku injector (not ready or permission missing)")
                newInjector.disconnect()
            }
        }
    }

    override fun onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        } catch (e: Throwable) {
            // Ignore
        }
        shizukuRecoveryJob?.cancel()
        shizukuRecoveryJob = null
        connectGeneration++
        cancelPendingJobs(keepConnection = false)
        scope.cancel()
        injector?.disconnect()
        hideCursorOverlay()
        restorePreviousIme()
        stopService(Intent(this, CursorOverlayService::class.java))
        super.onDestroy()
    }
}

internal enum class ConnectAttemptOutcome {
    Success,
    Retrying,
    Rejected,
    TerminalFailure,
}

internal fun shouldClearActiveSession(outcome: ConnectAttemptOutcome): Boolean =
    outcome == ConnectAttemptOutcome.Rejected ||
        outcome == ConnectAttemptOutcome.TerminalFailure

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
        return keepAliveMissed >= 4
    }

    fun onDisconnected() {
        keepAliveMissed = 0
        _state.value = ConnectionState.Disconnected
    }
}



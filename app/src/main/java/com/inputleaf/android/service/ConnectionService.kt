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
import com.inputleaf.android.network.InputLeapConnection
import com.inputleaf.android.network.ServerTransport
import com.inputleaf.android.network.TlsFingerprintManager
import com.inputleaf.android.storage.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ConnectionService"
private const val KEEPALIVE_POLL_MS = 5_000L

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
    private var screenWidth = 0
    private var screenHeight = 0
    private var currentMouseX = 0f
    private var currentMouseY = 0f
    private lateinit var prefs: AppPreferences

    val state: StateFlow<ConnectionState> get() = stateMachine.state

    inner class LocalBinder : Binder() { fun getService() = this@ConnectionService }
    override fun onBind(intent: Intent): IBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        NotificationHelper.createChannel(this)
        observeState()

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

    fun connect(serverIp: String, screenName: String, force: Boolean = false) {
        val currentState = stateMachine.state.value
        if (!force) {
            if (currentState is ConnectionState.Connecting && currentState.serverIp == serverIp) return
            if (currentState is ConnectionState.Handshaking && currentState.serverIp == serverIp) return
            if (currentState is ConnectionState.Idle && currentState.serverIp == serverIp) return
            if (currentState is ConnectionState.Active && currentState.serverIp == serverIp) return
        }

        userInitiatedDisconnect = false
        val generation = ++connectGeneration
        cancelPendingJobs(keepConnection = false)
        connection?.close()
        connection = null

        connectJob = scope.launch {
            performConnect(serverIp, screenName, generation)
        }
    }

    fun reconnect(serverIp: String, screenName: String) {
        connect(serverIp, screenName, force = true)
    }

    private suspend fun performConnect(serverIp: String, screenName: String, generation: Int) {
        if (generation != connectGeneration) return
        try {
            startForeground(NOTIF_ID, NotificationHelper.build(this@ConnectionService, stateMachine.state.value))
            stateMachine.onConnecting(serverIp)

            val storedFp = prefs.fingerprintFor(serverIp).first()
            val cachedTransport = prefs.transportFor(serverIp).first()?.let { mode ->
                when (mode.lowercase()) {
                    "tls" -> ServerTransport.TLS
                    "plain" -> ServerTransport.PLAIN
                    else -> null
                }
            }

            val conn = InputLeapConnection(
                ip = serverIp,
                preferredTransport = cachedTransport,
                pinnedFingerprint = storedFp,
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
            val result = conn.connect(
                screenName = screenName,
                screenWidth = bounds.width(),
                screenHeight = bounds.height(),
            )
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
                }
                is ConnectResult.NetworkError -> {
                    conn.close()
                    prefs.clearTransport(serverIp)
                    stateMachine.onDisconnected()
                    scheduleRetry(serverIp, screenName, generation)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != connectGeneration) return
            Log.w(TAG, "Connection to $serverIp failed: ${e.javaClass.simpleName}: ${e.message}", e)
            stateMachine.onDisconnected()
            scheduleRetry(serverIp, screenName, generation)
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
        this.injector = injector
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
        val delayMs = RetryDelayCalculator.getDelay(retryAttempt++)
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

    fun disconnect() {
        userInitiatedDisconnect = true
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
        try {
            val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            val ourIme = android.content.ComponentName(this, com.inputleaf.android.inject.InputLeafIME::class.java).flattenToShortString()
            if (currentIme != ourIme) {
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

    override fun onDestroy() {
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

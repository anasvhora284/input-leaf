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
import com.inputleaf.android.storage.AppPreferences
import com.inputleaf.android.storage.ClientCertificateStore
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
    private val coordinator = ConnectionCoordinator()
    private var connection: InputLeapConnection? = null
    private var injector: com.inputleaf.android.inject.InputInjector? = null
    private var keepAliveJob: Job? = null
    private var connectJob: Job? = null
    private var eventLoopJob: Job? = null
    private var retryJob: Job? = null
    private var retryAttempt = 0
    private var cursorOverlayEnabled = false
    private var previousImeId: String? = null
    private var previousImeLabel: String? = null
    private var isUsingAccessibilityIme = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var currentMouseX = 0f
    private var currentMouseY = 0f
    private lateinit var prefs: AppPreferences

    val state: StateFlow<ConnectionState> get() = coordinator.state

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
            coordinator.setMouseEnabled(prefs.mouseEnabled.first())
            prefs.mouseEnabled.collect { enabled ->
                applyEffects(coordinator.setMouseEnabled(enabled))
            }
        }

        scope.launch {
            coordinator.setKeyboardEnabled(prefs.keyboardEnabled.first())
            prefs.keyboardEnabled.collect(coordinator::setKeyboardEnabled)
        }

        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, CursorOverlayService::class.java))
        }
    }

    private fun observeState() = scope.launch {
        coordinator.state.collect { state ->
            val notif = NotificationHelper.build(this@ConnectionService, state)
            getSystemService(android.app.NotificationManager::class.java)
                .notify(NOTIF_ID, notif)
        }
    }

    var onFingerprintConfirmationRequired: (suspend (ip: String, fp: String, oldFp: String?) -> Boolean)? = null
    var onConnectionRejected: (() -> Unit)? = null
    var onConnectionFailed: ((reason: ConnectResult.FailureReason, detail: String?) -> Unit)? = null

    fun connect(serverIp: String, screenName: String, force: Boolean = false) {
        val currentState = coordinator.state.value
        if (!force) {
            if (currentState is ConnectionState.Connecting && currentState.serverIp == serverIp) return
            if (currentState is ConnectionState.Handshaking && currentState.serverIp == serverIp) return
            if (currentState is ConnectionState.Idle && currentState.serverIp == serverIp) return
            if (currentState is ConnectionState.Active && currentState.serverIp == serverIp) return
        }

        val generation = coordinator.beginConnection()
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
        if (!coordinator.isCurrent(generation)) return
        var activePolicy = ConnectionTransportPolicy.AUTO
        try {
            startForeground(NOTIF_ID, NotificationHelper.build(this@ConnectionService, coordinator.state.value))
            coordinator.onConnecting(generation, serverIp)

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
            if (!coordinator.isCurrent(generation)) {
                conn.close()
                return
            }

            when (result) {
                is ConnectResult.Ok -> {
                    retryAttempt = 0
                    connection = conn
                    prefs.saveTransport(serverIp, result.transport.name.lowercase())
                    coordinator.onConnected(generation, serverIp, screenName)
                    conn.clearHandshakeTimeout()
                    startEventLoop(conn, serverIp, screenName, generation)
                    startKeepAliveMonitor(conn, generation)
                    autoSwitchImeToOurs()
                }
                is ConnectResult.RejectedByUser -> {
                    conn.close()
                    coordinator.onConnectionRejected(generation)
                    onConnectionRejected?.invoke()
                }
                is ConnectResult.Failed -> {
                    conn.close()
                    val retry = activePolicy.shouldRetry(result.reason)
                    if (retry) prefs.clearTransport(serverIp)
                    val effects = coordinator.onConnectionFailed(generation, retry)
                    if (ConnectionCoordinator.Effect.ScheduleRetry in effects) {
                        scheduleRetry(serverIp, screenName, generation)
                    } else if (!retry) {
                        onConnectionFailed?.invoke(result.reason, result.detail)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!coordinator.isCurrent(generation)) return
            Log.w(TAG, "Connection to $serverIp failed: ${e.javaClass.simpleName}: ${e.message}", e)
            val retry = activePolicy.shouldRetry(ConnectResult.FailureReason.NETWORK)
            val effects = coordinator.onConnectionFailed(generation, retry)
            if (ConnectionCoordinator.Effect.ScheduleRetry in effects) {
                scheduleRetry(serverIp, screenName, generation)
            } else if (!retry) {
                onConnectionFailed?.invoke(ConnectResult.FailureReason.NETWORK, e.message)
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
                val effects = coordinator.onEvent(generation, event)
                applyEffects(effects, conn, ip, screenName, generation)
            }
        }
    }

    private fun applyEffects(
        effects: List<ConnectionCoordinator.Effect>,
        conn: InputLeapConnection? = connection,
        ip: String? = null,
        screenName: String? = null,
        generation: Int? = null,
    ) {
        effects.forEach { effect ->
            when (effect) {
                is ConnectionCoordinator.Effect.RouteInput -> routeInput(effect.event)
                ConnectionCoordinator.Effect.SendKeepAlive -> conn?.sendKeepAlive()
                ConnectionCoordinator.Effect.ShowCursor -> showCursorOverlay()
                ConnectionCoordinator.Effect.HideCursor -> hideCursorOverlay()
                ConnectionCoordinator.Effect.RestoreIme -> restorePreviousIme()
                ConnectionCoordinator.Effect.CloseConnection -> conn?.close()
                ConnectionCoordinator.Effect.ScheduleRetry -> {
                    if (ip != null && screenName != null && generation != null) {
                        scheduleRetry(ip, screenName, generation)
                    }
                }
            }
        }
    }

    private fun routeInput(event: InputLeapEvent) {
        when (event) {
            is InputLeapEvent.MouseMoveAbs -> {
                currentMouseX = event.x.toFloat()
                currentMouseY = event.y.toFloat()
                updateCursorPosition(currentMouseX, currentMouseY)
            }
            is InputLeapEvent.MouseMoveRel -> {
                currentMouseX = (currentMouseX + event.dx).coerceIn(0f, screenWidth.toFloat())
                currentMouseY = (currentMouseY + event.dy).coerceIn(0f, screenHeight.toFloat())
                updateCursorPosition(currentMouseX, currentMouseY)
            }
            else -> Unit
        }
        dispatchInput(event)
    }

    fun setCursorOverlayEnabled(enabled: Boolean) {
        cursorOverlayEnabled = enabled
        if (enabled && coordinator.state.value is ConnectionState.Active) {
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
            while (coordinator.isCurrent(generation)) {
                delay(KEEPALIVE_POLL_MS)
                if (!coordinator.isCurrent(generation)) break
                val effects = coordinator.onKeepAliveMiss(generation)
                if (ConnectionCoordinator.Effect.CloseConnection in effects) {
                    Log.w(TAG, "Keep-alive timeout — disconnecting")
                    applyEffects(effects, conn)
                    break
                }
            }
        }
    }

    private fun scheduleRetry(ip: String, screenName: String, generation: Int) {
        if (!coordinator.isCurrent(generation)) return
        retryJob?.cancel()
        val delayMs = RetryDelayCalculator.getDelay(retryAttempt++)
        retryJob = scope.launch {
            delay(delayMs)
            if (!coordinator.isCurrent(generation)) return@launch
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
        coordinator.onUserDisconnect()
        cancelPendingJobs(keepConnection = false)
        injector?.disconnect()
        injector = null
        hideCursorOverlay()
        restorePreviousIme()
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

    override fun onDestroy() {
        coordinator.onUserDisconnect()
        cancelPendingJobs(keepConnection = false)
        scope.cancel()
        injector?.disconnect()
        hideCursorOverlay()
        restorePreviousIme()
        stopService(Intent(this, CursorOverlayService::class.java))
        super.onDestroy()
    }
}

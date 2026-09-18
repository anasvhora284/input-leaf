package com.inputleaf.android.service

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.database.ContentObserver
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import com.inputleaf.android.inject.AccessibilityInputService
import com.inputleaf.android.inject.NativePointerState
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
private const val LEAVE_DEBOUNCE_MS = 300L
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
    private var infoAckPending = false
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
    private var leaveDebounceJob: Job? = null
    private val hidKeyboardGate = HidAttachmentController()
    private val hidMouseGate = HidAttachmentController()
    @Volatile private var pointerOnScreen = false
    private lateinit var prefs: AppPreferences

    private val pointerSpeedObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            applyPointerSpeedFromSettings()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            applyPointerSpeedFromSettings()
        }
    }

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
                applyCursorOverlay()
            }
        }

        scope.launch {
            mouseEnabled = prefs.mouseEnabled.first()
            prefs.mouseEnabled.collect { enabled ->
                mouseEnabled = enabled
                applyCursorOverlay()
                setHidMouseAttached(enabled && stateMachine.state.value is ConnectionState.Active)
            }
        }

        scope.launch {
            keyboardEnabled = prefs.keyboardEnabled.first()
            prefs.keyboardEnabled.collect { enabled ->
                keyboardEnabled = enabled
                setHidKeyboardAttached(enabled && stateMachine.state.value is ConnectionState.Active)
            }
        }

        if (canShowCursor()) {
            startService(Intent(this, CursorOverlayService::class.java))
        }

        registerPointerSpeedObserver()
    }

    private fun registerPointerSpeedObserver() {
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(SETTINGS_POINTER_SPEED_KEY),
            false,
            pointerSpeedObserver,
        )
        applyPointerSpeedFromSettings()
    }

    private fun unregisterPointerSpeedObserver() {
        contentResolver.unregisterContentObserver(pointerSpeedObserver)
    }

    private fun applyPointerSpeedFromSettings() {
        val speed = readPointerSpeed()
        injector?.updatePointerSpeed(speed)
        Log.d(TAG, "Pointer speed updated to $speed")
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
        infoAckPending = false
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
                        cancelLeaveDebounce()
                        pointerOnScreen = true
                        Log.i(TAG, "Enter ${event.x},${event.y}")
                        stateMachine.onActive()
                        stateMachine.onKeepAlive()
                        injector?.updatePointerSpeed(readPointerSpeed())
                        injector?.onHidMouseEnter(event.x, event.y)
                        applyCursorOverlay()
                        setHidKeyboardAttached(keyboardEnabled)
                        setHidMouseAttached(mouseEnabled)
                    }
                    is InputLeapEvent.Leave -> {
                        injector?.onHidMouseLeave()
                        scheduleLeave(generation)
                    }
                    is InputLeapEvent.KeepAlive -> {
                        stateMachine.onKeepAlive()
                        conn.sendKeepAlive()
                    }
                    is InputLeapEvent.InfoAck -> {
                        infoAckPending = false
                    }
                    is InputLeapEvent.QueryInfo -> {
                        connection?.let {
                            if (generation == connectGeneration) {
                                it.sendDataInfo(
                                    screenWidth,
                                    screenHeight,
                                    currentMouseX.toInt(),
                                    currentMouseY.toInt(),
                                )
                            }
                        }
                    }
                    is InputLeapEvent.MouseMoveAbs -> {
                        if (!mouseEnabled) return@collect
                        if (infoAckPending) return@collect
                        stateMachine.onKeepAlive()
                        currentMouseX = event.x.toFloat()
                        currentMouseY = event.y.toFloat()
                        updateCursorPosition(currentMouseX, currentMouseY)
                        dispatchInput(event)
                    }
                    is InputLeapEvent.MouseMoveRel -> {
                        if (!mouseEnabled) return@collect
                        if (infoAckPending) return@collect
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
                        cancelLeaveDebounce()
                        pointerOnScreen = false
                        stateMachine.onDisconnected()
                        applyCursorOverlay()
                        setHidKeyboardAttached(false)
                        setHidMouseAttached(false)
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
        applyCursorOverlay()
    }

    private fun applyCursorOverlay() {
        val inj = injector
        val show = CursorOverlayPolicy.shouldShowOverlay(
            cursorSettingEnabled = cursorOverlayEnabled,
            onScreen = pointerOnScreen,
            mouseEnabled = mouseEnabled,
            native = inj?.nativePointerState() ?: NativePointerState.NONE,
            expectsNativePointer = inj?.expectsNativePointer() == true,
        )
        if (show) {
            showCursorOverlay()
        } else {
            hideCursorOverlay()
        }
    }

    private fun showCursorOverlay() {
        if (!canShowCursor()) {
            Log.w(TAG, "Cannot show cursor — no overlay permission and accessibility is off")
            return
        }
        CursorOverlayService.show()
    }

    private fun canShowCursor(): Boolean =
        Settings.canDrawOverlays(this) ||
            AccessibilityInputService.isServiceRunning() ||
            isAccessibilityEnabled()

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val component = android.content.ComponentName(this, AccessibilityInputService::class.java)
        return enabled.contains(component.flattenToShortString()) ||
            enabled.contains(component.flattenToString())
    }

    private fun hideCursorOverlay() {
        CursorOverlayService.hide()
    }

    private fun cancelLeaveDebounce() {
        leaveDebounceJob?.cancel()
        leaveDebounceJob = null
    }

    private fun scheduleLeave(generation: Int) {
        leaveDebounceJob?.cancel()
        leaveDebounceJob = scope.launch {
            delay(LEAVE_DEBOUNCE_MS)
            if (generation != connectGeneration) return@launch
            Log.i(TAG, "Leave")
            pointerOnScreen = false
            stateMachine.onLeave()
            applyCursorOverlay()
            setHidKeyboardAttached(false)
            setHidMouseAttached(false)
            leaveDebounceJob = null
        }
    }

    private fun setHidKeyboardAttached(attached: Boolean) {
        hidKeyboardGate.setWanted(attached)
        scope.launch(Dispatchers.IO) {
            hidKeyboardGate.applyLatest { wanted ->
                injector?.setHidKeyboardAttached(wanted)
            }
        }
    }

    private fun setHidMouseAttached(attached: Boolean) {
        hidMouseGate.setWanted(attached)
        scope.launch(Dispatchers.IO) {
            hidMouseGate.applyLatest { wanted ->
                injector?.setHidMouseAttached(wanted)
            }
        }
    }

    private fun updateCursorPosition(x: Float, y: Float) {
        if (!cursorOverlayEnabled) return
        CursorOverlayService.updatePosition(x, y)
    }

    fun setInjector(injector: com.inputleaf.android.inject.InputInjector) {
        if (this.injector != null && this.injector != injector) {
            this.injector?.setOnNativePointerStateChanged(null)
            this.injector?.disconnect()
            if (this.injector is com.inputleaf.android.inject.AccessibilityInputInjector &&
                injector !is com.inputleaf.android.inject.AccessibilityInputInjector
            ) {
                restorePreviousIme()
            }
        }
        this.injector = injector
        hidKeyboardGate.noteInjectorChanged()
        hidMouseGate.noteInjectorChanged()
        // The service's bounds are authoritative (proven by DINF); application-context
        // WindowManager metrics can disagree on some OEMs (seen: portrait from app
        // context while landscape on ColorOS).
        injector.updateScreenSize(screenWidth, screenHeight)
        injector.updatePointerSpeed(readPointerSpeed())
        injector.setOnNativePointerStateChanged {
            scope.launch { applyCursorOverlay() }
        }
        if (injector is ShizukuInputInjector) {
            injector.onServiceDisconnectedCallback = {
                handleShizukuServiceDisconnected()
            }
        }
        if (pointerOnScreen) {
            setHidKeyboardAttached(keyboardEnabled)
            setHidMouseAttached(mouseEnabled)
        }
        Log.i(TAG, "Input injector set to: ${injector.name}")
    }

    private fun readPointerSpeed(): Int = readSystemPointerSpeed(contentResolver)

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
                    cancelLeaveDebounce()
                    pointerOnScreen = false
                    stateMachine.onDisconnected()
                    applyCursorOverlay()
                    setHidKeyboardAttached(false)
                    setHidMouseAttached(false)
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
        cancelLeaveDebounce()
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
        infoAckPending = false
        clearActiveSession()
        shizukuRecoveryJob?.cancel()
        shizukuRecoveryJob = null
        connectGeneration++
        cancelPendingJobs(keepConnection = false)
        pointerOnScreen = false
        setHidKeyboardAttached(false)
        setHidMouseAttached(false)
        injector?.setOnNativePointerStateChanged(null)
        injector?.disconnect()
        injector = null
        applyCursorOverlay()
        restorePreviousIme()
        stateMachine.onDisconnected()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) disconnect()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val bounds = getScreenBounds()
        val w = bounds.width()
        val h = bounds.height()
        if (w == screenWidth && h == screenHeight) return
        Log.i(TAG, "Screen bounds changed ${screenWidth}x$screenHeight -> ${w}x$h")
        screenWidth = w
        screenHeight = h
        currentMouseX = currentMouseX.coerceIn(0f, w.toFloat())
        currentMouseY = currentMouseY.coerceIn(0f, h.toFloat())
        injector?.updateScreenSize(w, h)
        val connected = connection != null && stateMachine.state.value.let {
            it is ConnectionState.Idle || it is ConnectionState.Active
        }
        if (connected) {
            infoAckPending = true
            connection?.sendDataInfo(w, h, currentMouseX.toInt(), currentMouseY.toInt())
            Log.i(TAG, "Sent DINF update ${w}x${h}")
        }
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
        unregisterPointerSpeedObserver()
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
        injector?.setOnNativePointerStateChanged(null)
        injector?.disconnect()
        pointerOnScreen = false
        applyCursorOverlay()
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



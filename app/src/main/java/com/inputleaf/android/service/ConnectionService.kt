package com.inputleaf.android.service

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.content.pm.ServiceInfo
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
import androidx.core.app.ServiceCompat
import com.inputleaf.android.inject.AccessibilityInputService
import com.inputleaf.android.inject.NativePointerState
import com.inputleaf.android.inject.PointerDevice
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.network.ConnectResult
import com.inputleaf.android.network.ConnectionTransportPolicy
import com.inputleaf.android.network.InputLeapConnection
import com.inputleaf.android.network.ServerTransport
import com.inputleaf.android.network.TlsFingerprintManager
import com.inputleaf.android.privilege.PrivilegeKind
import com.inputleaf.android.privilege.PrivilegedInjectorFactory
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
// How long the cursor must stay away before the HID mouse is actually destroyed.
private const val HID_MOUSE_IDLE_DETACH_MS = 30_000L

class ConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val coordinator = ConnectionCoordinator()
    private var connection: InputLeapConnection? = null
    private var injector: com.inputleaf.android.inject.InputInjector? = null
    private var keepAliveJob: Job? = null
    private var connectJob: Job? = null
    private var eventLoopJob: Job? = null
    private var retryJob: Job? = null
    private var activePolicy = ConnectionTransportPolicy.AUTO
    private var cursorOverlayEnabled = false
    private var previousImeId: String? = null
    private var previousImeLabel: String? = null
    private var isUsingAccessibilityIme = false
    private var shizukuRecoveryJob: Job? = null
    @Volatile private var leaveDebounceJob: Job? = null
    @Volatile private var hidMouseIdleJob: Job? = null
    private val hidKeyboardGate = HidAttachmentController()
    private val hidMouseGate = HidAttachmentController()
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

    val state: StateFlow<ConnectionState> get() = coordinator.state

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
        coordinator.updateScreenBounds(bounds.width(), bounds.height())

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
            coordinator.mouseEnabled = prefs.mouseEnabled.first()
            prefs.mouseEnabled.collect { enabled ->
                coordinator.mouseEnabled = enabled
                applyCursorOverlay()
                setHidMouseAttached(enabled && coordinator.state.value is ConnectionState.Active)
            }
        }

        scope.launch {
            coordinator.keyboardEnabled = prefs.keyboardEnabled.first()
            prefs.keyboardEnabled.collect { enabled ->
                coordinator.keyboardEnabled = enabled
                setHidKeyboardAttached(enabled && coordinator.state.value is ConnectionState.Active)
            }
        }

        scope.launch {
            prefs.pointerDevice.collect { device ->
                val isStylus = device == PointerDevice.STYLUS
                coordinator.absolutePointer = isStylus
                injector?.setAbsolutePointer(isStylus)
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

        val plan = coordinator.startConnect(serverIp, screenName, forceReconnect = force)
        cancelPendingJobs(keepConnection = false)
        connection?.close()
        connection = null

        connectJob = scope.launch {
            if (plan.initialDelayMs > 0) {
                delay(plan.initialDelayMs)
            }
            performConnect(serverIp, screenName, plan.generation)
        }
    }

    fun reconnect(serverIp: String, screenName: String) {
        connect(serverIp, screenName, force = true)
    }

    private suspend fun performConnect(serverIp: String, screenName: String, generation: Int) {
        if (!coordinator.isCurrentGeneration(generation)) return
        try {
            runCatching {
                ServiceCompat.startForeground(
                    this@ConnectionService,
                    NOTIF_ID,
                    NotificationHelper.build(this@ConnectionService, coordinator.state.value),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    } else {
                        0
                    },
                )
            }.onFailure { error ->
                Log.w(TAG, "Failed to start foreground service: ${error.message}", error)
            }

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
            coordinator.updateScreenBounds(bounds.width(), bounds.height())
            val result = try {
                conn.connect(
                    screenName = screenName,
                    screenWidth = bounds.width(),
                    screenHeight = bounds.height(),
                )
            } finally {
                clientCertificate?.clear()
            }
            if (!coordinator.isCurrentGeneration(generation)) {
                conn.close()
                return
            }

            when (result) {
                is ConnectResult.Ok -> {
                    connection = conn
                    if (result.banner.name.isNotBlank()) {
                        prefs.saveServerName(serverIp, result.banner.name)
                    }
                    prefs.saveTransport(serverIp, result.transport.name.lowercase())
                    coordinator.onConnectOk(serverIp, screenName, generation)
                    conn.clearHandshakeTimeout()
                    startEventLoop(conn, serverIp, screenName, generation)
                    startKeepAliveMonitor(conn, generation)
                    autoSwitchImeToOurs()
                }
                is ConnectResult.RejectedByUser -> {
                    conn.close()
                    coordinator.onConnectRejected(generation)
                    onConnectionRejected?.invoke()
                }
                is ConnectResult.Failed -> {
                    conn.close()
                    val action = coordinator.onConnectFailed(result.reason, result.detail, activePolicy, generation)
                    when (action) {
                        is ConnectFailureAction.Retry -> {
                            prefs.clearTransport(serverIp)
                            scheduleRetry(serverIp, screenName, action.delayMs, action.generation)
                        }
                        is ConnectFailureAction.Terminal -> {
                            onConnectionFailed?.invoke(action.reason, action.detail)
                        }
                        ConnectFailureAction.Ignore -> Unit
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!coordinator.isCurrentGeneration(generation)) return
            Log.w(TAG, "Connection to $serverIp failed: ${e.javaClass.simpleName}: ${e.message}", e)
            val action = coordinator.onConnectFailed(ConnectResult.FailureReason.NETWORK, e.message, activePolicy, generation)
            when (action) {
                is ConnectFailureAction.Retry -> {
                    scheduleRetry(serverIp, screenName, action.delayMs, action.generation)
                }
                is ConnectFailureAction.Terminal -> {
                    onConnectionFailed?.invoke(action.reason, action.detail)
                }
                ConnectFailureAction.Ignore -> Unit
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
                if (!coordinator.isCurrentGeneration(generation)) return@collect
                when (val result = coordinator.processEvent(event, generation, activePolicy)) {
                    is CoordinatorEventResult.Enter -> {
                        cancelLeaveDebounce()
                        Log.i(TAG, "Enter ${result.x},${result.y}")
                        injector?.updatePointerSpeed(readPointerSpeed())
                        injector?.onHidMouseEnter(result.x, result.y)
                        setHidKeyboardAttached(coordinator.keyboardEnabled)
                        setHidMouseAttached(coordinator.mouseEnabled)
                        applyCursorOverlay()
                    }
                    is CoordinatorEventResult.Leave -> {
                        injector?.onHidMouseLeave()
                        scheduleLeave(generation)
                    }
                    is CoordinatorEventResult.KeepAlive -> {
                        conn.sendKeepAlive()
                    }
                    is CoordinatorEventResult.InfoAck -> Unit
                    is CoordinatorEventResult.QueryInfo -> {
                        connection?.sendDataInfo(
                            result.width,
                            result.height,
                            result.x,
                            result.y,
                        )
                    }
                    is CoordinatorEventResult.MouseMotion -> {
                        updateCursorPosition(result.cursorX, result.cursorY)
                        dispatchInput(result.event)
                    }
                    is CoordinatorEventResult.Input -> {
                        dispatchInput(result.event)
                    }
                    is CoordinatorEventResult.Disconnected -> {
                        cancelLeaveDebounce()
                        applyCursorOverlay()
                        setHidKeyboardAttached(false)
                        setHidMouseAttached(false)
                        restorePreviousIme()
                        if (result.retry != null) {
                            scheduleRetry(ip, screenName, result.retry.delayMs, result.retry.generation)
                        }
                    }
                    CoordinatorEventResult.Ignored -> Unit
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
            onScreen = coordinator.isPointerOnScreen,
            mouseEnabled = coordinator.mouseEnabled,
            native = inj?.nativePointerState() ?: NativePointerState.NONE,
            expectsNativePointer = inj?.expectsNativePointer() == true,
        )
        Log.i(
            TAG,
            "Cursor overlay show=$show native=${inj?.nativePointerState()} " +
                "expectsNative=${inj?.expectsNativePointer() == true} onScreen=${coordinator.isPointerOnScreen}",
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
        hidMouseIdleJob?.cancel()
        hidMouseIdleJob = null
    }

    private fun scheduleLeave(generation: Int) {
        leaveDebounceJob?.cancel()
        leaveDebounceJob = scope.launch {
            delay(LEAVE_DEBOUNCE_MS)
            if (!coordinator.onLeaveDebounceExpired(generation)) return@launch
            Log.i(TAG, "Leave")
            applyCursorOverlay()
            // The keyboard must go: while it is registered Android believes a physical
            // keyboard is attached and keeps the soft keyboard suppressed.
            setHidKeyboardAttached(false)
            scheduleHidMouseIdleDetach(generation)
            leaveDebounceJob = null
        }
    }

    /**
     * Keep the HID mouse registered across a Leave.
     *
     * Destroying it means the next Enter creates a new device, and AOSP seeds a new
     * pointer at display centre. Warping away from that seed is a race against AOSP's
     * own asynchronous initialisation, and one that cannot be won reliably -- there is
     * no signal for "the seed has landed". Keeping the device sidesteps the race
     * entirely: the pointer does not move while the cursor is away, so the position
     * model stays true and Enter becomes an ordinary delta.
     *
     * The device is still dropped once the cursor has been away long enough that a
     * parked pointer is more annoying than paying for a re-create.
     */
    private fun scheduleHidMouseIdleDetach(generation: Int) {
        hidMouseIdleJob?.cancel()
        hidMouseIdleJob = scope.launch {
            delay(HID_MOUSE_IDLE_DETACH_MS)
            if (!coordinator.isCurrentGeneration(generation) || coordinator.isPointerOnScreen) return@launch
            Log.i(TAG, "HID mouse idle ${HID_MOUSE_IDLE_DETACH_MS}ms; detaching")
            setHidMouseAttached(false)
            hidMouseIdleJob = null
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
        injector.updateScreenSize(coordinator.screenWidth, coordinator.screenHeight)
        injector.updatePointerSpeed(readPointerSpeed())
        injector.setAbsolutePointer(coordinator.absolutePointer)
        injector.setOnNativePointerStateChanged {
            scope.launch { applyCursorOverlay() }
        }
        if (injector is ShizukuInputInjector) {
            injector.onServiceDisconnectedCallback = {
                handlePrivilegedServiceDisconnected()
            }
        }
        if (coordinator.isPointerOnScreen) {
            setHidKeyboardAttached(coordinator.keyboardEnabled)
            setHidMouseAttached(coordinator.mouseEnabled)
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
            while (coordinator.isCurrentGeneration(generation)) {
                delay(KEEPALIVE_POLL_MS)
                if (!coordinator.isCurrentGeneration(generation)) break
                if (coordinator.onKeepAliveMiss(generation)) {
                    Log.w(TAG, "Keep-alive timeout — disconnecting")
                    conn.close()
                    cancelLeaveDebounce()
                    applyCursorOverlay()
                    setHidKeyboardAttached(false)
                    setHidMouseAttached(false)
                    restorePreviousIme()
                    break
                }
            }
        }
    }

    private fun scheduleRetry(ip: String, screenName: String, delayMs: Long, generation: Int) {
        if (coordinator.isUserInitiatedDisconnect || !coordinator.isCurrentGeneration(generation)) return
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delayMs)
            if (coordinator.isUserInitiatedDisconnect || !coordinator.isCurrentGeneration(generation)) return@launch
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

    fun disconnect() {
        coordinator.disconnect()
        shizukuRecoveryJob?.cancel()
        shizukuRecoveryJob = null
        cancelPendingJobs(keepConnection = false)
        setHidKeyboardAttached(false)
        setHidMouseAttached(false)
        injector?.setOnNativePointerStateChanged(null)
        injector?.disconnect()
        injector = null
        applyCursorOverlay()
        restorePreviousIme()
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }.onFailure { error ->
            Log.w(TAG, "Failed to stop foreground service cleanly: ${error.message}", error)
        }
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
        val plan = coordinator.updateScreenBounds(w, h)
        if (plan is ScreenBoundsUpdatePlan.None && w == coordinator.screenWidth && h == coordinator.screenHeight) {
            return
        }
        Log.i(TAG, "Screen bounds changed -> ${w}x$h")
        injector?.updateScreenSize(w, h)
        if (plan is ScreenBoundsUpdatePlan.SendDinf) {
            connection?.sendDataInfo(plan.width, plan.height, plan.x, plan.y)
            Log.i(TAG, "Sent DINF update ${w}x${h}")
        }
    }

    internal fun getScreenBounds(): Rect = ScreenBoundsResolver.resolveFromContext(this)

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
        // AccessibilityInputInjector also reports SHIZUKU (it holds a latent Shizuku host),
        // so the kind alone would tear down a working Accessibility session.
        val inj = injector
        if (inj is ShizukuInputInjector && inj.privilegeKind() == PrivilegeKind.SHIZUKU) {
            Log.w(TAG, "Shizuku binder died while using Shizuku injector; disconnecting injector")
            injector?.disconnect()
            handlePrivilegedServiceDisconnected()
        }
    }

    private fun handlePrivilegedServiceDisconnected() {
        Log.w(TAG, "Privileged UserService disconnected mid-session")
        triggerPrivilegedRecovery(delayMs = 300L)
    }

    private fun handleShizukuRestarted() {
        if (shouldIgnoreShizukuRestart(injector)) {
            Log.d(TAG, "Ignoring Shizuku restart because root HID injector is active")
            return
        }
        Log.i(TAG, "Shizuku service restarted")
        triggerPrivilegedRecovery(delayMs = 600L)
    }

    private fun triggerPrivilegedRecovery(delayMs: Long) {
        val ip = coordinator.serverIp ?: return
        val name = coordinator.screenName ?: return
        if (coordinator.isUserInitiatedDisconnect) return

        shizukuRecoveryJob?.cancel()
        shizukuRecoveryJob = scope.launch {
            val preferredMethod = prefs.inputMethod.first()
            if (preferredMethod == "accessibility") {
                Log.d(TAG, "Skipping privileged recovery because preferred method is Accessibility")
                return@launch
            }

            delay(delayMs)
            if (coordinator.isUserInitiatedDisconnect || coordinator.serverIp != ip) return@launch

            Log.i(TAG, "Attempting auto-recovery of privileged session to $ip")
            val bounds = getScreenBounds()
            val snapshot = PrivilegedInjectorFactory.currentSnapshot()
            val recoveryKind = privilegedRecoveryKind(preferredMethod, snapshot)
            if (recoveryKind == null) {
                Log.w(TAG, "Skipping privileged recovery; $preferredMethod cannot attach")
                return@launch
            }
            val newInjector = PrivilegedInjectorFactory.create(
                this@ConnectionService,
                bounds.width(),
                bounds.height(),
                snapshot,
                preferredMethod,
            )

            if (newInjector.isAvailable() && newInjector.connect()) {
                Log.i(TAG, "Privileged injector recovered (${newInjector.name}); reconnecting session to $ip")
                setInjector(newInjector)
                reconnect(ip, name)
            } else {
                Log.w(TAG, "Could not recover privileged injector (Shizuku/root not ready)")
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
        coordinator.disconnect()
        cancelPendingJobs(keepConnection = false)
        scope.cancel()
        injector?.setOnNativePointerStateChanged(null)
        injector?.disconnect()
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

/** True only for the root host itself. Accessibility reports that host's kind too. */
internal fun shouldIgnoreShizukuRestart(injector: com.inputleaf.android.inject.InputInjector?): Boolean {
    return injector is ShizukuInputInjector && injector.privilegeKind() == PrivilegeKind.ROOT
}

/**
 * Kind to reconnect, or null when the saved method cannot attach.
 * [PrivilegeKind.NONE] must not fall through to the other host.
 */
internal fun privilegedRecoveryKind(
    method: String,
    snapshot: com.inputleaf.android.privilege.PrivilegeSnapshot,
): PrivilegeKind? {
    val kind = PrivilegedInjectorFactory.kindFor(method, snapshot)
    return if (kind == PrivilegeKind.NONE) null else kind
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



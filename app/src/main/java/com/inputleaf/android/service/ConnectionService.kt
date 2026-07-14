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
import com.inputleaf.android.network.InputLeapConnection
import com.inputleaf.android.network.TlsFingerprintManager
import com.inputleaf.android.storage.AppPreferences
import com.inputleaf.android.shizuku.ShizukuInputInjector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

private const val TAG = "ConnectionService"

class ConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val stateMachine = ConnectionStateMachine()
    private var connection: InputLeapConnection? = null
    private var injector: com.inputleaf.android.inject.InputInjector? = null
    private var keepAliveJob: Job? = null
    private var retryAttempt = 0
    private var cursorOverlayEnabled = false
    private var mouseEnabled = true
    private var keyboardEnabled = true
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
        
        // Get screen dimensions
        val bounds = getScreenBounds()
        screenWidth = bounds.width()
        screenHeight = bounds.height()
        
        // Load cursor overlay preference - get initial value synchronously first
        scope.launch {
            // Get initial value immediately
            cursorOverlayEnabled = prefs.showCursor.first()
            Log.d(TAG, "Cursor overlay initial value: $cursorOverlayEnabled")
            
            // Then observe for changes
            prefs.showCursor.collect { enabled ->
                cursorOverlayEnabled = enabled
                Log.d(TAG, "Cursor overlay enabled changed: $enabled")
            }
        }
        
        // Observe mouse enabled preference
        scope.launch {
            mouseEnabled = prefs.mouseEnabled.first()
            Log.d(TAG, "Mouse enabled initial value: $mouseEnabled")
            prefs.mouseEnabled.collect { enabled ->
                mouseEnabled = enabled
                Log.d(TAG, "Mouse enabled changed: $enabled")
                // Hide cursor if mouse is disabled while active
                if (!enabled) hideCursorOverlay()
            }
        }
        
        // Observe keyboard enabled preference
        scope.launch {
            keyboardEnabled = prefs.keyboardEnabled.first()
            Log.d(TAG, "Keyboard enabled initial value: $keyboardEnabled")
            prefs.keyboardEnabled.collect { enabled ->
                keyboardEnabled = enabled
                Log.d(TAG, "Keyboard enabled changed: $enabled")
            }
        }
        
        // Pre-start the cursor overlay service so it's ready when needed
        if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "Pre-starting CursorOverlayService")
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

    // TOFU callback: ViewModel sets this before calling connect()
    var onFingerprintConfirmationRequired: (suspend (ip: String, fp: String, oldFp: String?) -> Boolean)? = null

    fun connect(serverIp: String, screenName: String) {
        val currentState = stateMachine.state.value
        if (currentState is ConnectionState.Idle || currentState is ConnectionState.Active) {
            if ((currentState as? ConnectionState.Idle)?.serverIp == serverIp || (currentState as? ConnectionState.Active)?.serverIp == serverIp) {
                Log.w(TAG, "Ignoring connect request because we are already connected to $serverIp")
                return
            }
        }
        
        // Abort any ongoing stale connection attempt
        connection?.close()
        keepAliveJob?.cancel()
        
        scope.launch {
            try {
                startForeground(NOTIF_ID, NotificationHelper.build(this@ConnectionService, stateMachine.state.value))
                stateMachine.onConnecting(serverIp)
                val conn = InputLeapConnection(serverIp) { cert ->
                    val newFp = TlsFingerprintManager.fingerprintOf(cert)
                    val storedFp = prefs.fingerprintFor(serverIp).first()
                    val trusted = when {
                        storedFp == null -> {
                            // First connect: ask user to confirm
                            onFingerprintConfirmationRequired?.invoke(serverIp, newFp, null) ?: false
                        }
                        storedFp == newFp -> true  // auto-trust — same cert
                        else -> {
                            // Cert changed: warn user
                            onFingerprintConfirmationRequired?.invoke(serverIp, newFp, storedFp) ?: false
                        }
                    }
                    if (trusted) prefs.saveFingerprint(serverIp, newFp)
                    trusted
                }
                val banner = conn.connect()
                if (banner == null) { stateMachine.onDisconnected(); scheduleRetry(serverIp, screenName); return@launch }
                retryAttempt = 0
                connection = conn
                stateMachine.onHandshaking(serverIp)
                withContext(Dispatchers.IO) { conn.sendHelloBack(screenName) }
                startEventLoop(conn, serverIp, screenName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Connection to $serverIp failed: ${e.javaClass.simpleName}: ${e.message}", e)
                stateMachine.onDisconnected()
                scheduleRetry(serverIp, screenName)
            }
        }
    }

    private fun startEventLoop(conn: InputLeapConnection, ip: String, screenName: String) {
        scope.launch(Dispatchers.IO) {
            val bounds = getScreenBounds()
            var serverName = ""
            conn.events.collect { event ->
                when (event) {
                    is InputLeapEvent.Hello -> {
                        serverName = event.serverName
                        conn.sendHelloBack(screenName)
                    }
                    is InputLeapEvent.QueryInfo -> {
                        conn.sendDataInfo(bounds.width(), bounds.height())
                        stateMachine.onIdle(ip, serverName)
                        startKeepAliveMonitor(conn)
                    }
                    is InputLeapEvent.Enter -> {
                        stateMachine.onActive()
                        // Show cursor when entering (only if mouse is enabled)
                        if (mouseEnabled) showCursorOverlay()
                    }
                    is InputLeapEvent.Leave -> { 
                        stateMachine.onLeave()
                        // Hide cursor when leaving
                        hideCursorOverlay()
                    }
                    is InputLeapEvent.KeepAlive -> { stateMachine.onKeepAlive(); conn.sendKeepAlive() }
                    is InputLeapEvent.MouseMoveAbs -> {
                        if (!mouseEnabled) return@collect  // Skip if mouse disabled
                        // Update cursor overlay position
                        currentMouseX = event.x.toFloat()
                        currentMouseY = event.y.toFloat()
                        updateCursorPosition(currentMouseX, currentMouseY)
                        
                        dispatchInput(event)
                    }
                    is InputLeapEvent.MouseMoveRel -> {
                        if (!mouseEnabled) return@collect  // Skip if mouse disabled
                        // Update cursor overlay position
                        currentMouseX = (currentMouseX + event.dx).coerceIn(0f, screenWidth.toFloat())
                        currentMouseY = (currentMouseY + event.dy).coerceIn(0f, screenHeight.toFloat())
                        updateCursorPosition(currentMouseX, currentMouseY)
                        
                        dispatchInput(event)
                    }
                    is InputLeapEvent.MouseDown, is InputLeapEvent.MouseUp, is InputLeapEvent.MouseWheel -> {
                        if (!mouseEnabled) return@collect  // Skip if mouse disabled
                        dispatchInput(event)
                    }
                    is InputLeapEvent.KeyDown, is InputLeapEvent.KeyUp, is InputLeapEvent.KeyRepeat -> {
                        if (!keyboardEnabled) return@collect  // Skip if keyboard disabled
                        dispatchInput(event)
                    }
                    is InputLeapEvent.Unhandled -> if (event.tag == "__DISCONNECTED__") {
                        stateMachine.onDisconnected()
                        hideCursorOverlay()
                        scheduleRetry(ip, screenName)
                    }
                    else -> dispatchInput(event)
                }
            }
        }
    }
    
    /**
     * Enable or disable the cursor overlay.
     */
    fun setCursorOverlayEnabled(enabled: Boolean) {
        cursorOverlayEnabled = enabled
        if (enabled && stateMachine.state.value is ConnectionState.Active) {
            showCursorOverlay()
        } else if (!enabled) {
            hideCursorOverlay()
        }
    }
    
    private fun showCursorOverlay() {
        Log.d(TAG, "showCursorOverlay() called - cursorOverlayEnabled=$cursorOverlayEnabled")
        if (!cursorOverlayEnabled) {
            Log.d(TAG, "Cursor overlay disabled, not showing")
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlays - permission not granted")
            return
        }
        
        Log.d(TAG, "Calling CursorOverlayService.show()")
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

    private fun startKeepAliveMonitor(conn: InputLeapConnection) {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (true) {
                delay(4000) // slightly longer than 3s server interval
                if (stateMachine.onKeepAliveMiss()) {
                    Log.w(TAG, "Keep-alive timeout — disconnecting")
                    conn.close()
                    stateMachine.onDisconnected()
                    hideCursorOverlay()
                    break
                }
            }
        }
    }

    private fun scheduleRetry(ip: String, screenName: String) {
        val delayMs = RetryDelayCalculator.getDelay(retryAttempt++)
        scope.launch {
            delay(delayMs)
            connect(ip, screenName)
        }
    }

    fun disconnect() {
        keepAliveJob?.cancel()
        connection?.close()
        connection = null
        injector?.disconnect()
        injector = null
        hideCursorOverlay()
        promptImeSwitchIfActive()
        stateMachine.onDisconnected()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) disconnect()
        return START_STICKY
    }

    /**
     * Returns the screen bounds in a backward-compatible way.
     * Uses WindowManager.currentWindowMetrics (API 30+) when available,
     * otherwise falls back to the deprecated Display.getSize().
     */
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

    private fun promptImeSwitchIfActive() {
        try {
            val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            val ourIme = android.content.ComponentName(this, com.inputleaf.android.inject.InputLeafIME::class.java).flattenToShortString()
            if (currentIme == ourIme) {
                // Open Accessibility Settings so user can disable the service
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)

                // Show the IME picker over the settings screen after a slight delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    imm.showInputMethodPicker()
                }, 400)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prompt IME switch", e)
        }
    }

    override fun onDestroy() { 
        scope.cancel()
        injector?.disconnect()
        hideCursorOverlay()
        promptImeSwitchIfActive()
        stopService(Intent(this, CursorOverlayService::class.java))
        super.onDestroy() 
    }
}

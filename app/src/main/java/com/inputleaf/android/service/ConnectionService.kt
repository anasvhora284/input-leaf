package com.inputleaf.android.service

import android.app.Service
import android.content.Intent
import android.os.Binder
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
import com.inputleaf.android.uhid.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

private const val TAG = "ConnectionService"

class ConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val stateMachine = ConnectionStateMachine()
    private val uhidQueue = UhidEventQueue()
    private val mouseTracker = MousePositionTracker()
    private var connection: InputLeapConnection? = null
    private var uhidSocket: UhidEventSocket? = null
    private var shizukuInjector: ShizukuInputInjector? = null
    private var useShizuku = false
    private var keepAliveJob: Job? = null
    private var retryAttempt = 0
    private var cursorOverlayEnabled = false
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
        val wm = getSystemService(WindowManager::class.java)
        val bounds = wm.currentWindowMetrics.bounds
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
        scope.launch {
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
        }
    }

    private fun startEventLoop(conn: InputLeapConnection, ip: String, screenName: String) {
        scope.launch(Dispatchers.IO) {
            val wm = getSystemService(WindowManager::class.java)
            val bounds = wm.currentWindowMetrics.bounds
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
                        // Show cursor when entering
                        showCursorOverlay()
                    }
                    is InputLeapEvent.Leave -> { 
                        stateMachine.onLeave()
                        mouseTracker.reset()
                        // Hide cursor when leaving
                        hideCursorOverlay()
                    }
                    is InputLeapEvent.KeepAlive -> { stateMachine.onKeepAlive(); conn.sendKeepAlive() }
                    is InputLeapEvent.MouseMoveAbs -> {
                        // Update cursor overlay position
                        currentMouseX = event.x.toFloat()
                        currentMouseY = event.y.toFloat()
                        updateCursorPosition(currentMouseX, currentMouseY)
                        
                        // Shizuku uses absolute coordinates directly, UHID needs relative
                        if (useShizuku) {
                            dispatchInput(event)
                        } else {
                            val (dx, dy) = mouseTracker.updateAbsolute(event.x, event.y)
                            dispatchInput(InputLeapEvent.MouseMoveRel(dx, dy))
                        }
                    }
                    is InputLeapEvent.MouseMoveRel -> {
                        // Update cursor overlay position
                        currentMouseX = (currentMouseX + event.dx).coerceIn(0f, screenWidth.toFloat())
                        currentMouseY = (currentMouseY + event.dy).coerceIn(0f, screenHeight.toFloat())
                        updateCursorPosition(currentMouseX, currentMouseY)
                        
                        mouseTracker.updateRelative(event.dx, event.dy)
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

    /**
     * Connect to Shizuku input injector. Call this before connect() for non-root input injection.
     * @return true if Shizuku is available and bound successfully
     */
    suspend fun connectShizukuInjector(): Boolean = withContext(Dispatchers.IO) {
        val wm = getSystemService(WindowManager::class.java)
        val bounds = wm.currentWindowMetrics.bounds
        val injector = ShizukuInputInjector(bounds.width(), bounds.height())
        
        if (injector.isAvailable()) {
            val bound = injector.bind()
            if (bound) {
                shizukuInjector = injector
                useShizuku = true
                Log.i(TAG, "Shizuku input injector connected")
                return@withContext true
            }
        }
        Log.w(TAG, "Shizuku not available, will fall back to UHID")
        false
    }

    fun connectUhidSocket() {
        scope.launch(Dispatchers.IO) {
            val s = UhidEventSocket()
            if (s.connect()) {
                uhidSocket = s
                // Drain queued events that arrived before socket was ready
                uhidQueue.dequeueAll().forEach { s.send(it) }
            }
        }
    }

    private fun dispatchInput(event: InputLeapEvent) {
        // Prefer Shizuku if available, fall back to UHID
        val injector = shizukuInjector
        if (useShizuku && injector != null) {
            injector.send(event)
        } else {
            val socket = uhidSocket
            if (socket?.isConnected == true) socket.send(event)
            else uhidQueue.enqueue(event)
        }
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
        val delays = listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L)
        scope.launch {
            delay(delays[minOf(retryAttempt++, delays.lastIndex)])
            connect(ip, screenName)
        }
    }

    fun disconnect() {
        keepAliveJob?.cancel()
        connection?.close()
        connection = null
        shizukuInjector?.unbind()
        shizukuInjector = null
        useShizuku = false
        hideCursorOverlay()
        stateMachine.onDisconnected()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) disconnect()
        return START_STICKY
    }

    override fun onDestroy() { 
        scope.cancel()
        uhidSocket?.close()
        shizukuInjector?.unbind()
        hideCursorOverlay()
        stopService(Intent(this, CursorOverlayService::class.java))
        super.onDestroy() 
    }
}

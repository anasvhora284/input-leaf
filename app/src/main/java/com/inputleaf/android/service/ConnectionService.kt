package com.inputleaf.android.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.network.InputLeapConnection
import com.inputleaf.android.network.TlsFingerprintManager
import com.inputleaf.android.storage.AppPreferences
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
    private var keepAliveJob: Job? = null
    private var retryAttempt = 0
    private lateinit var prefs: AppPreferences

    val state: StateFlow<ConnectionState> get() = stateMachine.state

    inner class LocalBinder : Binder() { fun getService() = this@ConnectionService }
    override fun onBind(intent: Intent): IBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        NotificationHelper.createChannel(this)
        startForeground(NOTIF_ID, NotificationHelper.build(this, stateMachine.state.value))
        observeState()
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
            val connected = conn.connect()
            if (!connected) { stateMachine.onDisconnected(); scheduleRetry(serverIp, screenName); return@launch }
            retryAttempt = 0
            connection = conn
            stateMachine.onHandshaking(serverIp)
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
                    is InputLeapEvent.Enter -> stateMachine.onActive()
                    is InputLeapEvent.Leave -> { stateMachine.onLeave(); mouseTracker.reset() }
                    is InputLeapEvent.KeepAlive -> { stateMachine.onKeepAlive(); conn.sendKeepAlive() }
                    is InputLeapEvent.MouseMoveAbs -> {
                        val (dx, dy) = mouseTracker.updateAbsolute(event.x, event.y)
                        dispatchToUhid(InputLeapEvent.MouseMoveRel(dx, dy))
                    }
                    is InputLeapEvent.MouseMoveRel -> {
                        mouseTracker.updateRelative(event.dx, event.dy)
                        dispatchToUhid(event)
                    }
                    is InputLeapEvent.Unhandled -> if (event.tag == "__DISCONNECTED__") {
                        stateMachine.onDisconnected()
                        scheduleRetry(ip, screenName)
                    }
                    else -> dispatchToUhid(event)
                }
            }
        }
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

    private fun dispatchToUhid(event: InputLeapEvent) {
        val socket = uhidSocket
        if (socket?.isConnected == true) socket.send(event)
        else uhidQueue.enqueue(event)
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
        stateMachine.onDisconnected()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) disconnect()
        return START_STICKY
    }

    override fun onDestroy() { scope.cancel(); uhidSocket?.close(); super.onDestroy() }
}

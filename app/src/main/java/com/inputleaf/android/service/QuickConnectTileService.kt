package com.inputleaf.android.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

const val ACTION_QUICK_CONNECT = "com.inputleaf.android.QUICK_CONNECT"

/**
 * Quick Settings tile (#81). Tapped while connected it disconnects from the shade, with
 * no app. Tapped while not connected it opens the app, which connects to the last server
 * (following it to a new IP) and then returns to the background: the connection setup
 * (input method, certificate prompt) lives in the app, not the service.
 */
class QuickConnectTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null
    private var bound = false
    private var state: ConnectionState = ConnectionState.Disconnected

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as ConnectionService.LocalBinder).getService()
            stateJob?.cancel()
            stateJob = scope.launch {
                service.state.collect {
                    state = it
                    render()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            stateJob?.cancel()
            state = ConnectionState.Disconnected
            render()
        }
    }

    override fun onStartListening() {
        // No BIND_AUTO_CREATE: a tile must not start the service just to read its state.
        bound = bindService(Intent(this, ConnectionService::class.java), connection, 0)
        if (!bound) state = ConnectionState.Disconnected
        render()
    }

    override fun onStopListening() {
        stateJob?.cancel()
        if (bound) unbindService(connection)
        bound = false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        if (state is ConnectionState.Disconnected) openAppToConnect()
        else startService(Intent(this, ConnectionService::class.java).setAction(ACTION_DISCONNECT))
    }

    private fun openAppToConnect() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(ACTION_QUICK_CONNECT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val connected = state is ConnectionState.Idle || state is ConnectionState.Active
        val connecting = state is ConnectionState.Connecting || state is ConnectionState.Handshaking
        tile.label = "Input Leaf"
        tile.state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when {
                connected -> "Connected · tap to disconnect"
                connecting -> "Connecting…"
                else -> "Tap to connect"
            }
        }
        tile.updateTile()
    }
}

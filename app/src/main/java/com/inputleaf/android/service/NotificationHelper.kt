package com.inputleaf.android.service

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.ui.MainActivity

const val CHANNEL_ID = "inputleaf_status"
const val NOTIF_ID = 1001
const val ACTION_DISCONNECT = "com.inputleaf.android.DISCONNECT"

object NotificationHelper {
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Input-Leaf Status", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows Input-Leaf connection status" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(context: Context, state: ConnectionState): Notification {
        val text = when (state) {
            is ConnectionState.Active -> "ACTIVE · ${state.serverName}"
            is ConnectionState.Idle   -> "IDLE · ${state.serverName}"
            is ConnectionState.Connecting, is ConnectionState.Handshaking -> "Connecting…"
            is ConnectionState.Disconnected -> "Disconnected"
        }
        val tapIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectIntent = PendingIntent.getService(
            context, 0,
            Intent(context, ConnectionService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("Input-Leaf")
            .setContentText(text)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectIntent)
            .build()
    }
}

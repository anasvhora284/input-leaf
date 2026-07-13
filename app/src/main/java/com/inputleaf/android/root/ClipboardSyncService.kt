package com.inputleaf.android.root

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ClipboardSyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var applyingRemote = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("Root companion idle"))
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener {
            if (applyingRemote) return@addPrimaryClipChangedListener
            val text = clipboard.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                ?: return@addPrimaryClipChangedListener
            if (text.length > ControlFrame.MAX_CLIPBOARD_BYTES) return@addPrimaryClipChangedListener
            val client = RootCompanionState.client ?: return@addPrimaryClipChangedListener
            if (!client.isConnected) return@addPrimaryClipChangedListener
            scope.launch(Dispatchers.IO) {
                runCatching {
                    client.send(ControlMessage.ClipboardText(text, "android"))
                }
            }
        }

        collectJob = scope.launch {
            RootCompanionState.client?.messages?.collectLatest { msg ->
                if (msg is ControlMessage.ClipboardText && msg.source == "pc") {
                    applyingRemote = true
                    try {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("input-root", msg.text))
                        startForeground(NOTIF_ID, buildNotification("Clipboard from PC"))
                    } finally {
                        applyingRemote = false
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(content: String): Notification {
        val channelId = "root_companion"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(channelId, "Input Root", NotificationManager.IMPORTANCE_LOW),
            )
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Input Root companion")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 42
    }
}

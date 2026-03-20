package com.inputleaf.android.uhid

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.protocol.KeysymTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataOutputStream

private const val TAG = "UhidEventSocket"
private const val SOCKET_NAME = "inputleaf_uhid"

class UhidEventSocket {
    private var socket: LocalSocket? = null
    private var out: DataOutputStream? = null
    val isConnected get() = socket?.isConnected == true

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val s = LocalSocket()
            s.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
            // Wait for socket-level READY acknowledgment
            val ready = withTimeoutOrNull(5000L) {
                s.inputStream.bufferedReader().readLine()
            }
            if (ready != "READY") { s.close(); return@withContext false }
            socket = s
            out = DataOutputStream(s.outputStream)
            true
        } catch (e: Exception) {
            Log.e(TAG, "UHID socket connect failed: ${e.message}")
            false
        }
    }

    fun send(event: InputLeapEvent) {
        val o = out ?: return
        try {
            synchronized(o) {
                when (event) {
                    is InputLeapEvent.KeyDown -> {
                        KeysymTable.toHidUsage(event.keyId) ?: return
                        o.writeByte(0x01); o.writeInt(event.keyId)
                        o.writeByte(0x00); o.writeByte(event.mask.toInt())
                    }
                    is InputLeapEvent.KeyUp -> {
                        KeysymTable.toHidUsage(event.keyId) ?: return
                        o.writeByte(0x01); o.writeInt(event.keyId)
                        o.writeByte(0x01); o.writeByte(event.mask.toInt())
                    }
                    is InputLeapEvent.MouseMoveRel -> {
                        o.writeByte(0x02); o.writeInt(event.dx); o.writeInt(event.dy)
                    }
                    is InputLeapEvent.MouseDown -> {
                        o.writeByte(0x03); o.writeByte(event.buttonId); o.writeByte(0x00)
                    }
                    is InputLeapEvent.MouseUp -> {
                        o.writeByte(0x03); o.writeByte(event.buttonId); o.writeByte(0x01)
                    }
                    is InputLeapEvent.MouseWheel -> {
                        o.writeByte(0x04)
                        o.writeShort(event.xDelta); o.writeShort(event.yDelta)
                    }
                    else -> return
                }
                o.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "UHID send failed: ${e.message}")
        }
    }

    fun close() {
        runCatching { out?.writeByte(0xFF); out?.flush() }
        runCatching { socket?.close() }
        socket = null; out = null
    }
}

package com.inputleaf.android.root

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ControlChannelClient(
    private val adapter: BluetoothAdapter,
) {
    private val _messages = MutableSharedFlow<ControlMessage>(extraBufferCapacity = 32)
    val messages: SharedFlow<ControlMessage> = _messages

    @Volatile
    private var socket: BluetoothSocket? = null
    @Volatile
    private var output: OutputStream? = null

    val isConnected: Boolean get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        disconnect()
        val sock = createSocket(device)
        sock.connect()
        socket = sock
        output = sock.outputStream
        val input = BufferedInputStream(sock.inputStream)
        val buf = ArrayList<Byte>()
        val tmp = ByteArray(4096)
        while (true) {
            val n = input.read(tmp)
            if (n <= 0) break
            for (i in 0 until n) buf.add(tmp[i])
            while (true) {
                val frame = popFrame(buf) ?: break
                val msg = ControlFrame.decode(frame)
                if (msg is ControlMessage.Ping) {
                    send(ControlMessage.Pong(msg.nonce))
                }
                _messages.emit(msg)
            }
        }
        disconnect()
    }

    suspend fun send(msg: ControlMessage) = withContext(Dispatchers.IO) {
        val out = output ?: error("not connected")
        out.write(ControlFrame.encode(msg))
        out.flush()
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        output = null
    }

    @SuppressLint("MissingPermission")
    private fun createSocket(device: BluetoothDevice): BluetoothSocket {
        // Channel 22 matches input-root RFCOMM listener (no SDP in v1).
        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return method.invoke(device, ControlFrame.RFCOMM_CHANNEL) as BluetoothSocket
    }

    private fun popFrame(buf: ArrayList<Byte>): ByteArray? {
        if (buf.size < 5) return null
        if (buf[0] != ControlFrame.VERSION) error("bad version")
        val lenBytes = byteArrayOf(buf[1], buf[2], buf[3], buf[4])
        val len = ByteBuffer.wrap(lenBytes).order(ByteOrder.BIG_ENDIAN).int
        if (buf.size < 5 + len) return null
        val frame = ByteArray(5 + len)
        for (i in frame.indices) {
            frame[i] = buf.removeAt(0)
        }
        return frame
    }
}

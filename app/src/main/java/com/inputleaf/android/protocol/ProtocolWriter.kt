package com.inputleaf.android.protocol

import java.io.DataOutputStream
import java.io.OutputStream

class ProtocolWriter(output: OutputStream) {
    private val dout = DataOutputStream(output)

    fun writeHelloBack(screenName: String, major: Int, minor: Int) {
        val nameBytes = screenName.toByteArray(Charsets.UTF_8)
        frame("HELO") {
            writeShort(major); writeShort(minor)
            writeInt(nameBytes.size); write(nameBytes)
        }
    }

    // Field order per Input-Leap protocol_types.h kMsgDInfo: w, h, x, y, warp_x, warp_y
    fun writeDataInfo(w: Int, h: Int, x: Int, y: Int, wx: Int, wy: Int) {
        frame("DINF") {
            writeShort(w); writeShort(h)
            writeShort(x); writeShort(y)
            writeShort(wx); writeShort(wy)
        }
    }

    fun writeKeepAlive() = frame("CALV") {}

    fun writeInfoAck() = frame("CIAK") {}

    private fun frame(tag: String, block: DataOutputStream.() -> Unit) {
        val buf = java.io.ByteArrayOutputStream()
        val tmp = DataOutputStream(buf)
        tmp.write(tag.toByteArray(Charsets.US_ASCII))
        tmp.block()
        tmp.flush()
        val bytes = buf.toByteArray()
        synchronized(dout) {
            dout.writeInt(bytes.size)
            dout.write(bytes)
            dout.flush()
        }
    }
}

package com.inputleaf.android.protocol

import java.io.DataOutputStream
import java.io.OutputStream
import android.util.Log

class ProtocolWriter(output: OutputStream) {
    private val dout = DataOutputStream(output)

    fun writeHelloBack(screenName: String, major: Int, minor: Int) {
        val nameBytes = screenName.toByteArray(Charsets.UTF_8)
        Log.d("ProtocolWriter", "Sending HelloBack: name=$screenName, major=$major, minor=$minor, nameLen=${nameBytes.size}")
        frame("Barrier") {
            writeShort(major); writeShort(minor)
            writeInt(nameBytes.size); write(nameBytes)
        }
        Log.d("ProtocolWriter", "HelloBack sent successfully")
    }

    // Field order per kMsgDInfo: x, y, w, h, warp_size, mx, my (7 shorts)
    fun writeDataInfo(w: Int, h: Int, x: Int, y: Int, mx: Int, my: Int) {
        frame("DINF") {
            writeShort(x); writeShort(y)
            writeShort(w); writeShort(h)
            writeShort(0) // warp_size (obsolete)
            writeShort(mx); writeShort(my)
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

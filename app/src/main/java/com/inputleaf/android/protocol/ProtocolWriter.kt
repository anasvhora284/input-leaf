package com.inputleaf.android.protocol

import java.io.DataOutputStream
import java.io.OutputStream

class ProtocolWriter(output: OutputStream) {
    private val dout = DataOutputStream(output)

    fun writeHelloBack(screenName: String, major: Int, minor: Int) {
        val nameBytes = screenName.toByteArray(Charsets.UTF_8)
        // Input Leap HelloBack uses "Barrier" as the frame tag (not "HELO")
        frame("Barrier") {
            writeShort(major); writeShort(minor)
            writeInt(nameBytes.size); write(nameBytes)
        }
    }

    // Field order per Input Leap kMsgDInfo: sx(2), sy(2), sw(2), sh(2), warp(2), mx(2), my(2)
    // sx,sy = screen position; sw,sh = screen size; warp = 0 (obsolete); mx,my = mouse pos
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

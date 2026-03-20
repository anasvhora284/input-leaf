package com.inputleaf.android.protocol

import com.inputleaf.android.model.InputLeapEvent
import java.io.DataInputStream
import java.io.InputStream

class ProtocolParser(input: InputStream) {
    private val din = DataInputStream(input)

    fun readNext(): InputLeapEvent {
        val len = din.readInt()
        require(len in 4..ProtocolConstants.MAX_MESSAGE_LEN) { "Bad message length: $len" }
        val body = ByteArray(len).also { din.readFully(it) }
        val tag = String(body, 0, 4, Charsets.US_ASCII)
        val payload = body.drop(4).toByteArray()
        return parse(tag, payload)
    }

    private fun parse(tag: String, p: ByteArray): InputLeapEvent = when (tag) {
        ProtocolConstants.TAG_HELLO -> {
            val major = p.u16(0); val minor = p.u16(2)
            val nameLen = p.u32(4)
            val name = String(p, 8, nameLen, Charsets.UTF_8)
            InputLeapEvent.Hello(major, minor, name)
        }
        ProtocolConstants.TAG_QUERY_INFO -> InputLeapEvent.QueryInfo()
        ProtocolConstants.TAG_ENTER -> InputLeapEvent.Enter(p.u16(0), p.u16(2), p.u32(4), p.u16(8))
        ProtocolConstants.TAG_LEAVE -> InputLeapEvent.Leave
        ProtocolConstants.TAG_KEEPALIVE -> InputLeapEvent.KeepAlive
        ProtocolConstants.TAG_RESET_OPTIONS -> InputLeapEvent.ResetOptions
        ProtocolConstants.TAG_KEY_DOWN -> InputLeapEvent.KeyDown(p.u16(0), p.u16(2), p.u16(4))
        ProtocolConstants.TAG_KEY_UP -> InputLeapEvent.KeyUp(p.u16(0), p.u16(2), p.u16(4))
        ProtocolConstants.TAG_KEY_REPEAT -> InputLeapEvent.KeyRepeat(p.u16(0), p.u16(2), p.u16(4), p.u16(6))
        ProtocolConstants.TAG_MOUSE_MOVE -> InputLeapEvent.MouseMoveAbs(p.u16(0), p.u16(2))
        ProtocolConstants.TAG_MOUSE_REL -> InputLeapEvent.MouseMoveRel(p.s32(0), p.s32(4))
        ProtocolConstants.TAG_MOUSE_DOWN -> InputLeapEvent.MouseDown(p.u8(0))
        ProtocolConstants.TAG_MOUSE_UP -> InputLeapEvent.MouseUp(p.u8(0))
        ProtocolConstants.TAG_MOUSE_WHEEL -> InputLeapEvent.MouseWheel(p.s16(0), p.s16(2))
        ProtocolConstants.TAG_INCOMPATIBLE -> InputLeapEvent.Incompatible(p.u16(0), p.u16(2))
        ProtocolConstants.TAG_BUSY -> InputLeapEvent.Busy
        ProtocolConstants.TAG_UNKNOWN -> InputLeapEvent.Unknown
        ProtocolConstants.TAG_BAD -> InputLeapEvent.BadMessage
        else -> InputLeapEvent.Unhandled(tag)
    }

    private fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF
    private fun ByteArray.u16(i: Int) = (u8(i) shl 8) or u8(i+1)
    private fun ByteArray.s16(i: Int) = u16(i).let { if (it > 0x7FFF) it - 0x10000 else it }
    private fun ByteArray.u32(i: Int) = ((u8(i).toLong() shl 24) or (u8(i+1).toLong() shl 16) or
        (u8(i+2).toLong() shl 8) or u8(i+3).toLong()).toInt()
    private fun ByteArray.s32(i: Int) = u32(i)
}

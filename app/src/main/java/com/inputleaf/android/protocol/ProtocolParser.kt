package com.inputleaf.android.protocol

import com.inputleaf.android.model.InputLeapEvent
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

class ProtocolParser(input: DataInputStream) {
    constructor(input: InputStream) : this(DataInputStream(input))
    private val din = input

    fun readNext(): InputLeapEvent = try {
        val len = din.readInt()
        require(len in 4..ProtocolConstants.MAX_MESSAGE_LEN) { "Bad message length: $len" }
        val body = ByteArray(len).also { din.readFully(it) }
        when {
            body.size >= 7 && String(body, 0, 7, Charsets.US_ASCII) == "Barrier" -> parseHello(body, "Barrier")
            body.size >= 7 && String(body, 0, 7, Charsets.US_ASCII) == "Synergy" -> parseHello(body, "Synergy")
            else -> {
                val tag = String(body, 0, 4, Charsets.US_ASCII)
                parse(tag, body.copyOfRange(4, body.size))
            }
        }
    } catch (e: EOFException) {
        throw IllegalArgumentException("Truncated protocol message", e)
    }

    private fun parseHello(body: ByteArray, tag: String): InputLeapEvent {
        requireMessage(body.size >= 11, tag)
        val major = body.u16(7)
        val minor = body.u16(9)
        val name = if (body.size == 11) {
            ""
        } else {
            requireMessage(body.size >= 15, tag)
            val nameLength = body.u32(11)
            requireMessage(nameLength == body.size - 15L, tag)
            body.utf8(15, nameLength, tag)
        }
        return InputLeapEvent.Hello(major, minor, name)
    }

    private fun parse(tag: String, p: ByteArray): InputLeapEvent = when (tag) {
        ProtocolConstants.TAG_HELLO -> {
            requireMessage(p.size >= 8, tag)
            val nameLength = p.u32(4)
            requireMessage(nameLength == p.size - 8L, tag)
            InputLeapEvent.Hello(p.u16(0), p.u16(2), p.utf8(8, nameLength, tag))
        }
        ProtocolConstants.TAG_QUERY_INFO -> {
            requireMessage(p.isEmpty(), tag)
            InputLeapEvent.QueryInfo()
        }
        ProtocolConstants.TAG_ENTER -> {
            requireMessage(p.size == 10, tag)
            InputLeapEvent.Enter(p.u16(0), p.u16(2), p.u32(4).toInt(), p.u16(8))
        }
        ProtocolConstants.TAG_LEAVE -> {
            requireMessage(p.isEmpty(), tag)
            InputLeapEvent.Leave
        }
        ProtocolConstants.TAG_KEEPALIVE -> {
            requireMessage(p.isEmpty(), tag)
            InputLeapEvent.KeepAlive
        }
        ProtocolConstants.TAG_RESET_OPTIONS -> {
            requireMessage(p.isEmpty(), tag)
            InputLeapEvent.ResetOptions
        }
        ProtocolConstants.TAG_KEY_DOWN -> {
            requireMessage(p.size == 6, tag)
            InputLeapEvent.KeyDown(p.u16(0), p.u16(2), p.u16(4))
        }
        ProtocolConstants.TAG_KEY_UP -> {
            requireMessage(p.size == 6, tag)
            InputLeapEvent.KeyUp(p.u16(0), p.u16(2), p.u16(4))
        }
        ProtocolConstants.TAG_KEY_REPEAT -> {
            requireMessage(p.size == 6 || p.size == 8, tag)
            InputLeapEvent.KeyRepeat(p.u16(0), p.u16(2), p.u16(4), if (p.size == 8) p.u16(6) else 0)
        }
        ProtocolConstants.TAG_MOUSE_MOVE -> {
            requireMessage(p.size == 4, tag)
            InputLeapEvent.MouseMoveAbs(p.u16(0), p.u16(2))
        }
        ProtocolConstants.TAG_MOUSE_REL -> {
            requireMessage(p.size == 8, tag)
            InputLeapEvent.MouseMoveRel(p.s32(0), p.s32(4))
        }
        ProtocolConstants.TAG_MOUSE_DOWN -> {
            requireMessage(p.size == 1, tag)
            InputLeapEvent.MouseDown(p.u8(0))
        }
        ProtocolConstants.TAG_MOUSE_UP -> {
            requireMessage(p.size == 1, tag)
            InputLeapEvent.MouseUp(p.u8(0))
        }
        ProtocolConstants.TAG_MOUSE_WHEEL -> {
            requireMessage(p.size == 4, tag)
            InputLeapEvent.MouseWheel(p.s16(0), p.s16(2))
        }
        ProtocolConstants.TAG_INCOMPATIBLE -> {
            requireMessage(p.size == 4, tag)
            InputLeapEvent.Incompatible(p.u16(0), p.u16(2))
        }
        ProtocolConstants.TAG_BUSY -> {
            requireMessage(p.isEmpty(), tag)
            InputLeapEvent.Busy
        }
        ProtocolConstants.TAG_UNKNOWN -> {
            requireMessage(p.isEmpty(), tag)
            InputLeapEvent.Unknown
        }
        ProtocolConstants.TAG_BAD -> {
            requireMessage(p.isEmpty(), tag)
            InputLeapEvent.BadMessage
        }
        else -> InputLeapEvent.Unhandled(tag)
    }

    private fun requireMessage(valid: Boolean, tag: String) {
        require(valid) { "Malformed $tag message" }
    }

    private fun ByteArray.utf8(offset: Int, length: Long, tag: String): String {
        requireMessage(length <= size - offset, tag)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(this, offset, length.toInt()))
                .toString()
        } catch (e: CharacterCodingException) {
            throw IllegalArgumentException("Malformed UTF-8 in $tag message", e)
        }
    }

    private fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF
    private fun ByteArray.u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
    private fun ByteArray.s16(i: Int) = u16(i).let { if (it > 0x7FFF) it - 0x10000 else it }
    private fun ByteArray.u32(i: Int) = (u8(i).toLong() shl 24) or (u8(i + 1).toLong() shl 16) or
        (u8(i + 2).toLong() shl 8) or u8(i + 3).toLong()
    private fun ByteArray.s32(i: Int) = (u32(i) and 0xFFFF_FFFFL).toInt()
}

package com.inputleaf.android.protocol

import com.inputleaf.android.model.InputLeapEvent
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.net.ProtocolException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

class ProtocolParser(input: DataInputStream) {
    constructor(input: InputStream) : this(DataInputStream(input))
    private val input = input

    fun readNext(): InputLeapEvent = try {
        val length = input.readInt()
        if (length !in 4..ProtocolConstants.MAX_MESSAGE_LEN) {
            protocolError("Bad message length: $length")
        }
        val body = ByteArray(length).also { input.readFully(it) }
        when {
            body.startsWithAscii("Barrier") -> parseHello(body, "Barrier")
            body.startsWithAscii("Synergy") -> parseHello(body, "Synergy")
            else -> parse(body.protocolTag(), body.copyOfRange(4, body.size))
        }
    } catch (e: EOFException) {
        protocolError("Truncated protocol message", e)
    }

    private fun parseHello(body: ByteArray, tag: String): InputLeapEvent {
        requireAtLeast(body.size, 11, "body", tag)
        val major = body.u16(7)
        val minor = body.u16(9)
        val name = if (body.size == 11) {
            ""
        } else {
            if (body.size < 15) {
                protocolError(
                    "Malformed $tag message: expected 11 or at least 15 body bytes, got ${body.size}"
                )
            }
            val nameLength = body.u32(11)
            requireExact(body.size.toLong(), 15L + nameLength, "body", tag)
            body.utf8(15, nameLength, tag)
        }
        return InputLeapEvent.Hello(major, minor, name)
    }

    private fun parse(tag: String, payload: ByteArray): InputLeapEvent = when (tag) {
        ProtocolConstants.TAG_HELLO -> {
            requireAtLeast(payload.size, 8, "payload", tag)
            val nameLength = payload.u32(4)
            requireExact(payload.size.toLong(), 8L + nameLength, "payload", tag)
            InputLeapEvent.Hello(
                payload.u16(0), payload.u16(2), payload.utf8(8, nameLength, tag)
            )
        }
        ProtocolConstants.TAG_QUERY_INFO -> {
            requireExact(payload.size, 0, tag)
            InputLeapEvent.QueryInfo()
        }
        ProtocolConstants.TAG_ENTER -> {
            requireExact(payload.size, 10, tag)
            InputLeapEvent.Enter(
                payload.u16(0), payload.u16(2), payload.s32(4), payload.u16(8)
            )
        }
        ProtocolConstants.TAG_LEAVE -> {
            requireExact(payload.size, 0, tag)
            InputLeapEvent.Leave
        }
        ProtocolConstants.TAG_KEEPALIVE -> {
            requireExact(payload.size, 0, tag)
            InputLeapEvent.KeepAlive
        }
        ProtocolConstants.TAG_RESET_OPTIONS -> {
            requireExact(payload.size, 0, tag)
            InputLeapEvent.ResetOptions
        }
        ProtocolConstants.TAG_KEY_DOWN -> {
            requireExact(payload.size, 6, tag)
            InputLeapEvent.KeyDown(payload.u16(0), payload.u16(2), payload.u16(4))
        }
        ProtocolConstants.TAG_KEY_UP -> {
            requireExact(payload.size, 6, tag)
            InputLeapEvent.KeyUp(payload.u16(0), payload.u16(2), payload.u16(4))
        }
        ProtocolConstants.TAG_KEY_REPEAT -> {
            requireOneOf(payload.size, 6, 8, tag)
            InputLeapEvent.KeyRepeat(
                payload.u16(0), payload.u16(2), payload.u16(4),
                if (payload.size == 8) payload.u16(6) else 0
            )
        }
        ProtocolConstants.TAG_MOUSE_MOVE -> {
            requireExact(payload.size, 4, tag)
            InputLeapEvent.MouseMoveAbs(payload.u16(0), payload.u16(2))
        }
        ProtocolConstants.TAG_MOUSE_REL -> {
            requireExact(payload.size, 8, tag)
            InputLeapEvent.MouseMoveRel(payload.s32(0), payload.s32(4))
        }
        ProtocolConstants.TAG_MOUSE_DOWN -> {
            requireExact(payload.size, 1, tag)
            InputLeapEvent.MouseDown(payload.u8(0))
        }
        ProtocolConstants.TAG_MOUSE_UP -> {
            requireExact(payload.size, 1, tag)
            InputLeapEvent.MouseUp(payload.u8(0))
        }
        ProtocolConstants.TAG_MOUSE_WHEEL -> {
            requireExact(payload.size, 4, tag)
            InputLeapEvent.MouseWheel(payload.s16(0), payload.s16(2))
        }
        ProtocolConstants.TAG_INCOMPATIBLE -> {
            requireExact(payload.size, 4, tag)
            InputLeapEvent.Incompatible(payload.u16(0), payload.u16(2))
        }
        ProtocolConstants.TAG_BUSY -> {
            requireExact(payload.size, 0, tag)
            InputLeapEvent.Busy
        }
        ProtocolConstants.TAG_UNKNOWN -> {
            requireExact(payload.size, 0, tag)
            InputLeapEvent.Unknown
        }
        ProtocolConstants.TAG_BAD -> {
            requireExact(payload.size, 0, tag)
            InputLeapEvent.BadMessage
        }
        else -> InputLeapEvent.Unhandled(tag)
    }

    private fun requireExact(actual: Int, expected: Int, tag: String) {
        requireExact(actual.toLong(), expected.toLong(), "payload", tag)
    }

    private fun requireExact(actual: Long, expected: Long, unit: String, tag: String) {
        if (actual != expected) {
            protocolError("Malformed $tag message: expected $expected $unit bytes, got $actual")
        }
    }

    private fun requireAtLeast(actual: Int, minimum: Int, unit: String, tag: String) {
        if (actual < minimum) {
            protocolError("Malformed $tag message: expected at least $minimum $unit bytes, got $actual")
        }
    }

    private fun requireOneOf(actual: Int, first: Int, second: Int, tag: String) {
        if (actual != first && actual != second) {
            protocolError(
                "Malformed $tag message: expected $first or $second payload bytes, got $actual"
            )
        }
    }

    private fun ByteArray.startsWithAscii(prefix: String): Boolean =
        size >= prefix.length && prefix.indices.all { this[it] == prefix[it].code.toByte() }

    private fun ByteArray.protocolTag(): String {
        for (index in 0 until 4) {
            if (u8(index) !in 0x20..0x7E) {
                protocolError("Malformed protocol tag: expected four printable ASCII bytes")
            }
        }
        return String(this, 0, 4, Charsets.US_ASCII)
    }

    private fun ByteArray.utf8(offset: Int, length: Long, tag: String): String {
        if (length > size - offset) {
            protocolError("Malformed $tag message: name exceeds payload")
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(this, offset, length.toInt()))
                .toString()
        } catch (e: CharacterCodingException) {
            protocolError("Malformed UTF-8 in $tag message", e)
        }
    }

    private fun protocolError(message: String, cause: Throwable? = null): Nothing {
        throw ProtocolException(message).also { if (cause != null) it.initCause(cause) }
    }

    private fun ByteArray.u8(index: Int) = this[index].toInt() and 0xFF
    private fun ByteArray.u16(index: Int) = (u8(index) shl 8) or u8(index + 1)
    private fun ByteArray.s16(index: Int) = u16(index).let {
        if (it > 0x7FFF) it - 0x10000 else it
    }
    private fun ByteArray.u32(index: Int) =
        (u8(index).toLong() shl 24) or (u8(index + 1).toLong() shl 16) or
            (u8(index + 2).toLong() shl 8) or u8(index + 3).toLong()

    // Converting to Int preserves all 32 wire bits; a set high bit produces a negative value.
    private fun ByteArray.s32(index: Int) = u32(index).toInt()
}

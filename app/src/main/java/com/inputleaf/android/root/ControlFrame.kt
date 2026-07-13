package com.inputleaf.android.root

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed class ControlMessage {
    data class Ping(val nonce: Int) : ControlMessage()
    data class Pong(val nonce: Int) : ControlMessage()
    data class ClipboardText(val text: String, val source: String) : ControlMessage()
    data class Error(val code: String, val message: String) : ControlMessage()
}

object ControlFrame {
    const val VERSION: Byte = 1
    const val MAX_CLIPBOARD_BYTES = 64 * 1024
    /** RFCOMM channel matching input-root Linux listener */
    const val RFCOMM_CHANNEL = 22

    fun encode(msg: ControlMessage): ByteArray {
        val json = when (msg) {
            is ControlMessage.Ping -> JSONObject()
                .put("type", "ping").put("nonce", msg.nonce)
            is ControlMessage.Pong -> JSONObject()
                .put("type", "pong").put("nonce", msg.nonce)
            is ControlMessage.ClipboardText -> JSONObject()
                .put("type", "clipboard_text")
                .put("text", msg.text)
                .put("source", msg.source)
            is ControlMessage.Error -> JSONObject()
                .put("type", "error")
                .put("code", msg.code)
                .put("message", msg.message)
        }.toString().toByteArray(Charsets.UTF_8)

        require(json.size <= MAX_CLIPBOARD_BYTES + 512) { "frame too large" }

        val out = ByteBuffer.allocate(5 + json.size).order(ByteOrder.BIG_ENDIAN)
        out.put(VERSION)
        out.putInt(json.size)
        out.put(json)
        return out.array()
    }

    fun decode(bytes: ByteArray): ControlMessage {
        require(bytes.size >= 5) { "short frame" }
        require(bytes[0] == VERSION) { "bad version" }
        val len = ByteBuffer.wrap(bytes, 1, 4).order(ByteOrder.BIG_ENDIAN).int
        require(bytes.size == 5 + len) { "length mismatch" }
        val json = JSONObject(String(bytes, 5, len, Charsets.UTF_8))
        return when (json.getString("type")) {
            "ping" -> ControlMessage.Ping(json.getInt("nonce"))
            "pong" -> ControlMessage.Pong(json.getInt("nonce"))
            "clipboard_text" -> ControlMessage.ClipboardText(
                json.getString("text"),
                json.getString("source"),
            )
            "error" -> ControlMessage.Error(
                json.getString("code"),
                json.getString("message"),
            )
            else -> error("unknown type")
        }
    }
}

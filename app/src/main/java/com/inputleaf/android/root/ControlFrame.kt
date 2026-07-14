package com.inputleaf.android.root

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
            is ControlMessage.Ping ->
                """{"type":"ping","nonce":${msg.nonce}}"""
            is ControlMessage.Pong ->
                """{"type":"pong","nonce":${msg.nonce}}"""
            is ControlMessage.ClipboardText ->
                """{"type":"clipboard_text","text":${jsonString(msg.text)},"source":${jsonString(msg.source)}}"""
            is ControlMessage.Error ->
                """{"type":"error","code":${jsonString(msg.code)},"message":${jsonString(msg.message)}}"""
        }.toByteArray(Charsets.UTF_8)

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
        val json = String(bytes, 5, len, Charsets.UTF_8)
        val type = stringField(json, "type") ?: error("missing type")
        return when (type) {
            "ping" -> ControlMessage.Ping(intField(json, "nonce") ?: error("missing nonce"))
            "pong" -> ControlMessage.Pong(intField(json, "nonce") ?: error("missing nonce"))
            "clipboard_text" -> ControlMessage.ClipboardText(
                stringField(json, "text") ?: error("missing text"),
                stringField(json, "source") ?: error("missing source"),
            )
            "error" -> ControlMessage.Error(
                stringField(json, "code") ?: error("missing code"),
                stringField(json, "message") ?: error("missing message"),
            )
            else -> error("unknown type")
        }
    }

    private fun jsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun stringField(json: String, key: String): String? {
        val keyToken = "\"$key\""
        val keyIndex = json.indexOf(keyToken)
        if (keyIndex < 0) return null
        var i = keyIndex + keyToken.length
        while (i < json.length && (json[i] == ' ' || json[i] == ':' || json[i] == '\n' || json[i] == '\t')) {
            i++
        }
        if (i >= json.length || json[i] != '"') return null
        i++
        val sb = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            when {
                c == '\\' && i + 1 < json.length -> {
                    when (json[i + 1]) {
                        '"', '\\', '/' -> {
                            sb.append(json[i + 1]); i += 2
                        }
                        'n' -> {
                            sb.append('\n'); i += 2
                        }
                        'r' -> {
                            sb.append('\r'); i += 2
                        }
                        't' -> {
                            sb.append('\t'); i += 2
                        }
                        else -> {
                            sb.append(json[i + 1]); i += 2
                        }
                    }
                }
                c == '"' -> return sb.toString()
                else -> {
                    sb.append(c); i++
                }
            }
        }
        return null
    }

    private fun intField(json: String, key: String): Int? {
        val keyToken = "\"$key\""
        val keyIndex = json.indexOf(keyToken)
        if (keyIndex < 0) return null
        var i = keyIndex + keyToken.length
        while (i < json.length && (json[i] == ' ' || json[i] == ':' || json[i] == '\n' || json[i] == '\t')) {
            i++
        }
        val start = i
        while (i < json.length && (json[i].isDigit() || json[i] == '-')) {
            i++
        }
        if (start == i) return null
        return json.substring(start, i).toIntOrNull()
    }
}

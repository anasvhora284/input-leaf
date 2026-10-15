package com.inputleaf.android.storage

import java.util.Base64

/**
 * Encodes and decodes server-keyed preference records (such as TLS fingerprints
 * and transport modes) in DataStore.
 *
 * Canonical representation (v2):
 * `v2|<base64url-server>|<base64url-value>`
 *
 * Backwards compatibility:
 * Legacy records are stored as `server:value`. For IPv6 literal addresses containing
 * colons (e.g. `2001:db8::1`), the record is safely split on the last colon, because
 * fingerprints (64 hex characters) and transport modes (`tls`, `plain`, `auto`) do not
 * contain colons.
 */
object ServerRecordCodec {

    private const val PREFIX_V2 = "v2|"
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    fun encode(server: String, value: String): String {
        val s = encoder.encodeToString(server.trim().toByteArray(Charsets.UTF_8))
        val v = encoder.encodeToString(value.trim().toByteArray(Charsets.UTF_8))
        return "$PREFIX_V2$s|$v"
    }

    fun decode(line: String): Pair<String, String>? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith(PREFIX_V2)) {
            val parts = trimmed.split("|")
            if (parts.size != 3) return null
            return try {
                val server = String(decoder.decode(parts[1]), Charsets.UTF_8).trim()
                val value = String(decoder.decode(parts[2]), Charsets.UTF_8).trim()
                if (server.isEmpty() || value.isEmpty()) null else server to value
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        // Legacy format: server:value
        val lastColon = trimmed.lastIndexOf(':')
        if (lastColon <= 0 || lastColon == trimmed.length - 1) return null

        val server = trimmed.substring(0, lastColon).trim()
        val value = trimmed.substring(lastColon + 1).trim()
        return if (server.isEmpty() || value.isEmpty()) null else server to value
    }
}

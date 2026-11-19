package com.inputleaf.android.clipboard

import java.nio.ByteBuffer

/** Incoming clipboard formats. Images take precedence when text is also present. */
data class ClipboardContent(val text: String? = null, val bitmap: ByteBuffer? = null)

/** Input Leap / Deskflow's big-endian format count, followed by (format, size, bytes). */
object ClipboardCodec {
    const val FORMAT_TEXT = 0
    const val FORMAT_BITMAP = 2
    const val MAX_TEXT_BYTES = 4 * 1024 * 1024

    fun marshallText(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(12 + bytes.size)
            .putInt(1).putInt(FORMAT_TEXT).putInt(bytes.size).put(bytes)
            .array()
    }

    fun unmarshallText(blob: ByteArray): String? = unmarshall(blob)?.text

    /** Validate the entire format list before exposing any of it. Unknown formats are skipped. */
    fun unmarshall(blob: ByteArray): ClipboardContent? {
        if (blob.size < 4) return null
        val buffer = ByteBuffer.wrap(blob)
        val count = buffer.int
        if (count < 0 || count > buffer.remaining() / 8) return null
        var text: String? = null
        var bitmap: ByteBuffer? = null
        repeat(count) {
            if (buffer.remaining() < 8) return null
            val format = buffer.int
            val size = buffer.int
            if (size < 0 || size > buffer.remaining()) return null
            when (format) {
                FORMAT_TEXT -> if (size <= MAX_TEXT_BYTES) {
                    text = String(blob, buffer.position(), size, Charsets.UTF_8)
                }
                // Keep a read-only view of the completed transfer, avoiding another image-sized copy.
                FORMAT_BITMAP -> bitmap = buffer.slice().apply { limit(size) }.asReadOnlyBuffer()
            }
            buffer.position(buffer.position() + size)
        }
        if (buffer.hasRemaining() || (text == null && bitmap == null)) return null
        return ClipboardContent(text, bitmap)
    }
}

/** Reassembles DCLP start/data/end chunks into one marshalled clipboard. */
class ClipboardAssembler(private val maxBytes: Int = MAX_BYTES) {
    private var expected = -1L
    private var buffer: ByteArray? = null
    private var received = 0

    fun reset() {
        expected = -1L
        buffer = null
        received = 0
    }

    /** @return the complete marshalled clipboard on the end chunk, else null. */
    fun accept(mark: Int, data: ByteArray): ByteArray? {
        when (mark) {
            MARK_START -> {
                reset()
                expected = String(data, Charsets.US_ASCII).trim('\u0000').trim().toLongOrNull() ?: -1L
                buffer = if (expected in 0..maxBytes.toLong()) ByteArray(expected.toInt()) else null
            }
            MARK_DATA -> buffer?.let {
                if (received.toLong() + data.size > expected) {
                    reset()
                } else {
                    data.copyInto(it, received)
                    received += data.size
                }
            }
            MARK_END -> {
                val done = buffer?.takeIf { received.toLong() == expected }
                reset()
                return done
            }
        }
        return null
    }

    companion object {
        const val MARK_START = 1
        const val MARK_DATA = 2
        const val MARK_END = 3
        /** Includes format headers; enough for a 3840x2160 32-bit screenshot. */
        const val MAX_BYTES = 32 * 1024 * 1024
        const val CHUNK_BYTES = 512 * 1024
    }
}

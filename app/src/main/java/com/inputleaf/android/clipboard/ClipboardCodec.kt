package com.inputleaf.android.clipboard

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Input Leap / Deskflow clipboard payloads (#56).
 *
 * A clipboard is marshalled as a big-endian u32 format count, then per format a u32
 * format id, a u32 size and the bytes. Only text (format 0, UTF-8 with \n newlines)
 * is exchanged; HTML and bitmaps are ignored.
 */
object ClipboardCodec {
    const val FORMAT_TEXT = 0

    fun marshallText(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(12 + bytes.size)
            .putInt(1).putInt(FORMAT_TEXT).putInt(bytes.size).put(bytes)
            .array()
    }

    /** @return the text format, or null when there is none or the blob is malformed. */
    fun unmarshallText(blob: ByteArray): String? {
        if (blob.size < 4) return null
        val buffer = ByteBuffer.wrap(blob)
        val count = buffer.int
        repeat(count) {
            if (buffer.remaining() < 8) return null
            val format = buffer.int
            val size = buffer.int
            if (size < 0 || size > buffer.remaining()) return null
            if (format == FORMAT_TEXT) {
                return String(blob, buffer.position(), size, Charsets.UTF_8)
            }
            buffer.position(buffer.position() + size)
        }
        return null
    }
}

/** Reassembles DCLP start/data/end chunks into one marshalled clipboard. */
class ClipboardAssembler(private val maxBytes: Int = MAX_BYTES) {
    private var expected = -1L
    private var buffer: ByteArrayOutputStream? = null

    /** @return the complete marshalled clipboard on the end chunk, else null. */
    fun accept(mark: Int, data: ByteArray): ByteArray? {
        when (mark) {
            MARK_START -> {
                expected = String(data, Charsets.US_ASCII).trim('\u0000').trim().toLongOrNull() ?: -1L
                // Oversize or unreadable transfers are skipped to the end chunk.
                buffer = if (expected in 0..maxBytes.toLong()) ByteArrayOutputStream(expected.toInt()) else null
            }
            MARK_DATA -> buffer?.let {
                if (it.size() + data.size > expected) buffer = null else it.write(data)
            }
            MARK_END -> {
                val done = buffer?.takeIf { it.size().toLong() == expected }?.toByteArray()
                buffer = null
                expected = -1L
                return done
            }
        }
        return null
    }

    companion object {
        const val MARK_START = 1
        const val MARK_DATA = 2
        const val MARK_END = 3
        /** Deskflow's default clipboard limit is far larger; text beyond this is not useful. */
        const val MAX_BYTES = 4 * 1024 * 1024
        /** Matches Deskflow's StreamChunker. */
        const val CHUNK_BYTES = 512 * 1024
    }
}

package com.inputleaf.android.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream

class ProtocolWriterTest {
    private fun writerWith(): Pair<ProtocolWriter, ByteArrayOutputStream> {
        val output = ByteArrayOutputStream()
        return ProtocolWriter(output) to output
    }

    private fun frame(tag: String, payload: ByteArray = byteArrayOf()): ByteArray {
        val body = tag.toByteArray(Charsets.US_ASCII) + payload
        val length = body.size
        return byteArrayOf(
            (length shr 24).toByte(), (length shr 16).toByte(),
            (length shr 8).toByte(), length.toByte()
        ) + body
    }

    private fun u16(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())
    private fun u32(value: Int) = byteArrayOf(
        (value shr 24).toByte(), (value shr 16).toByte(),
        (value shr 8).toByte(), value.toByte()
    )

    @Test fun `writes complete Barrier hello frame with Unicode name`() {
        val (writer, output) = writerWith()
        val name = "café"
        val nameBytes = name.toByteArray(Charsets.UTF_8)

        writer.writeHelloBack(name, 1, 6)

        assertThat(output.toByteArray()).isEqualTo(
            frame("Barrier", u16(1) + u16(6) + u32(nameBytes.size) + nameBytes)
        )
    }

    @Test fun `writes complete data information frame in protocol order`() {
        val (writer, output) = writerWith()

        writer.writeDataInfo(1080, 2400, 50, 100, -1, 32767)

        assertThat(output.toByteArray()).isEqualTo(
            frame("DINF", u16(50) + u16(100) + u16(1080) + u16(2400) +
                u16(0) + u16(-1) + u16(32767))
        )
    }

    @Test fun `writes complete empty keepalive and information acknowledgement frames`() {
        val (writer, output) = writerWith()

        writer.writeKeepAlive()
        writer.writeInfoAck()

        assertThat(output.toByteArray()).isEqualTo(frame("CALV") + frame("CIAK"))
    }
}

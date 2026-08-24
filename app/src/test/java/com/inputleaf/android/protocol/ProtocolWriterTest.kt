package com.inputleaf.android.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream

class ProtocolWriterTest {
    private data class DecodedFrame(val declaredLength: Int, val tag: String, val payload: ByteArray)

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

    private fun decodeSingleFrame(bytes: ByteArray): DecodedFrame {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val declaredLength = input.readInt()
        val body = ByteArray(declaredLength).also(input::readFully)
        assertThat(input.available()).isEqualTo(0)
        return DecodedFrame(
            declaredLength = declaredLength,
            tag = String(body, 0, 4, Charsets.US_ASCII),
            payload = body.copyOfRange(4, body.size)
        )
    }

    private fun decodeShorts(payload: ByteArray): List<Int> {
        val input = DataInputStream(ByteArrayInputStream(payload))
        val values = buildList {
            while (input.available() > 0) add(input.readShort().toInt())
        }
        assertThat(input.available()).isEqualTo(0)
        return values
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

    @Test fun `writes empty screen name as a literal complete frame`() {
        val (writer, output) = writerWith()

        writer.writeHelloBack("", 1, 6)

        assertThat(output.toByteArray()).isEqualTo(byteArrayOf(
            0, 0, 0, 15,
            66, 97, 114, 114, 105, 101, 114,
            0, 1, 0, 6,
            0, 0, 0, 0
        ))
    }

    @Test fun `writes complete data information frame in protocol order`() {
        val (writer, output) = writerWith()

        writer.writeDataInfo(1080, 2400, 50, 100, -1, 32767)

        val decoded = decodeSingleFrame(output.toByteArray())
        assertThat(decoded.declaredLength).isEqualTo(18)
        assertThat(decoded.tag).isEqualTo("DINF")
        assertThat(decodeShorts(decoded.payload))
            .containsExactly(50, 100, 1080, 2400, 0, -1, 32767).inOrder()
    }

    @Test fun `writes signed two-byte boundary values without changing field order`() {
        val (writer, output) = writerWith()

        writer.writeDataInfo(0, 32767, -32768, -1, 0, 32767)

        val decoded = decodeSingleFrame(output.toByteArray())
        assertThat(decoded.tag).isEqualTo("DINF")
        assertThat(decodeShorts(decoded.payload))
            .containsExactly(-32768, -1, 0, 32767, 0, 0, 32767).inOrder()
    }

    @Test fun `preserves frame boundaries and order across consecutive writes`() {
        val (writer, output) = writerWith()

        writer.writeKeepAlive()
        writer.writeInfoAck()

        assertThat(output.toByteArray()).isEqualTo(frame("CALV") + frame("CIAK"))
    }

    @Test fun `propagates output failures to the caller`() {
        val writer = ProtocolWriter(FailingOutputStream())

        assertThrows(IOException::class.java) { writer.writeKeepAlive() }
    }

    private class FailingOutputStream : OutputStream() {
        override fun write(value: Int) {
            throw IOException("write failed")
        }
    }
}

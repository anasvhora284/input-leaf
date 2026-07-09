package com.inputleaf.android.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream

class ProtocolWriterTest {
    private fun writerWith(): Pair<ProtocolWriter, ByteArrayOutputStream> {
        val out = ByteArrayOutputStream()
        return ProtocolWriter(out) to out
    }

    @Test fun `writes HelloBack`() {
        val (writer, out) = writerWith()
        writer.writeHelloBack("android-phone", 1, 6)
        val bytes = out.toByteArray()
        val tag = String(bytes, 4, 7, Charsets.US_ASCII)
        assertThat(tag).isEqualTo("Barrier")
    }

    @Test fun `writes DataInfo with x,y,w,h order`() {
        val (writer, out) = writerWith()
        writer.writeDataInfo(1080, 2400, 50, 100, 0, 0)
        val bytes = out.toByteArray()
        assertThat(String(bytes, 4, 4, Charsets.US_ASCII)).isEqualTo("DINF")
        // protocol: [x(2), y(2), w(2), h(2), wx(2), wy(2)]
        val x = ((bytes[8].toInt() and 0xFF) shl 8) or (bytes[9].toInt() and 0xFF)
        assertThat(x).isEqualTo(50)
        val y = ((bytes[10].toInt() and 0xFF) shl 8) or (bytes[11].toInt() and 0xFF)
        assertThat(y).isEqualTo(100)
        val width = ((bytes[12].toInt() and 0xFF) shl 8) or (bytes[13].toInt() and 0xFF)
        assertThat(width).isEqualTo(1080)
        val height = ((bytes[14].toInt() and 0xFF) shl 8) or (bytes[15].toInt() and 0xFF)
        assertThat(height).isEqualTo(2400)
    }

    @Test fun `writes KeepAlive`() {
        val (writer, out) = writerWith()
        writer.writeKeepAlive()
        val bytes = out.toByteArray()
        assertThat(String(bytes, 4, 4, Charsets.US_ASCII)).isEqualTo("CALV")
    }
}

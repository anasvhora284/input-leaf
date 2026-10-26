package com.inputleaf.android.clipboard

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.protocol.ProtocolParser
import com.inputleaf.android.protocol.ProtocolWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import org.junit.Test

class ClipboardSyncTest {

    @Test
    fun `text marshals as Deskflow's big-endian format list and back`() {
        val blob = ClipboardCodec.marshallText("héllo\nworld")
        val buffer = ByteBuffer.wrap(blob)
        assertThat(buffer.int).isEqualTo(1) // one format
        assertThat(buffer.int).isEqualTo(ClipboardCodec.FORMAT_TEXT)
        assertThat(buffer.int).isEqualTo("héllo\nworld".toByteArray().size)
        assertThat(ClipboardCodec.unmarshallText(blob)).isEqualTo("héllo\nworld")
    }

    @Test
    fun `the text format is found after other formats, and junk is rejected`() {
        val html = "<b>x</b>".toByteArray()
        val text = "plain".toByteArray()
        val blob = ByteBuffer.allocate(4 + 8 + html.size + 8 + text.size)
            .putInt(2).putInt(1).putInt(html.size).put(html)
            .putInt(0).putInt(text.size).put(text).array()
        assertThat(ClipboardCodec.unmarshallText(blob)).isEqualTo("plain")

        assertThat(ClipboardCodec.unmarshallText(byteArrayOf(0, 0))).isNull()
        val overrun = ByteBuffer.allocate(12).putInt(1).putInt(0).putInt(99).array()
        assertThat(ClipboardCodec.unmarshallText(overrun)).isNull()
        val bitmapOnly = ByteBuffer.allocate(13).putInt(1).putInt(2).putInt(1).put(7).array()
        assertThat(ClipboardCodec.unmarshallText(bitmapOnly)).isNull()
    }

    @Test
    fun `chunks reassemble and only a complete transfer is returned`() {
        val blob = ClipboardCodec.marshallText("chunked text")
        val assembler = ClipboardAssembler()
        assertThat(assembler.accept(ClipboardAssembler.MARK_START, blob.size.toString().toByteArray())).isNull()
        assertThat(assembler.accept(ClipboardAssembler.MARK_DATA, blob.copyOfRange(0, 5))).isNull()
        assertThat(assembler.accept(ClipboardAssembler.MARK_DATA, blob.copyOfRange(5, blob.size))).isNull()
        assertThat(assembler.accept(ClipboardAssembler.MARK_END, ByteArray(0))).isEqualTo(blob)
    }

    @Test
    fun `oversize, short and overlong transfers are dropped`() {
        val small = ClipboardAssembler(maxBytes = 10)
        small.accept(ClipboardAssembler.MARK_START, "11".toByteArray())
        small.accept(ClipboardAssembler.MARK_DATA, ByteArray(11))
        assertThat(small.accept(ClipboardAssembler.MARK_END, ByteArray(0))).isNull()

        val short = ClipboardAssembler()
        short.accept(ClipboardAssembler.MARK_START, "8".toByteArray())
        short.accept(ClipboardAssembler.MARK_DATA, ByteArray(4))
        assertThat(short.accept(ClipboardAssembler.MARK_END, ByteArray(0))).isNull()

        val long = ClipboardAssembler()
        long.accept(ClipboardAssembler.MARK_START, "2".toByteArray())
        long.accept(ClipboardAssembler.MARK_DATA, ByteArray(3))
        assertThat(long.accept(ClipboardAssembler.MARK_END, ByteArray(0))).isNull()

        val garbage = ClipboardAssembler()
        garbage.accept(ClipboardAssembler.MARK_START, "abc".toByteArray())
        assertThat(garbage.accept(ClipboardAssembler.MARK_END, ByteArray(0))).isNull()
    }

    @Test
    fun `server text is applied, and the same text is never echoed back`() {
        val sync = ClipboardSync()
        val text = feed(sync, "from the PC")
        assertThat(text).isEqualTo("from the PC")
        assertThat(sync.onLeave("from the PC")).isNull()
        assertThat(sync.onLeave("copied on the phone")).isEqualTo("copied on the phone")
        // Already sent: leaving again without a new copy sends nothing.
        assertThat(sync.onLeave("copied on the phone")).isNull()
        assertThat(sync.onLeave(null)).isNull()
        assertThat(sync.onLeave("")).isNull()
    }

    @Test
    fun `the X11 selection clipboard and other events are ignored`() {
        val sync = ClipboardSync()
        val blob = ClipboardCodec.marshallText("selection")
        listOf(
            InputLeapEvent.ClipboardData(1, 0, 1, blob.size.toString().toByteArray()),
            InputLeapEvent.ClipboardData(1, 0, 2, blob),
            InputLeapEvent.ClipboardData(1, 0, 3, ByteArray(0)),
        ).forEach { assertThat(sync.onServerEvent(it)).isNull() }
        assertThat(sync.onServerEvent(InputLeapEvent.ClipboardGrab(0, 0))).isNull()
    }

    @Test
    fun `writer output parses back into the same clipboard events`() {
        val out = ByteArrayOutputStream()
        val writer = ProtocolWriter(out)
        writer.writeClipboardGrab(id = 0, seqNum = 7)
        writer.writeClipboardData(id = 0, seqNum = 7, mark = 2, data = "abc".toByteArray())

        val parser = ProtocolParser(DataInputStream(ByteArrayInputStream(out.toByteArray())))
        val grab = parser.readNext() as InputLeapEvent.ClipboardGrab
        assertThat(grab).isEqualTo(InputLeapEvent.ClipboardGrab(0, 7))
        val data = parser.readNext() as InputLeapEvent.ClipboardData
        assertThat(listOf(data.id, data.seqNum, data.mark)).containsExactly(0, 7, 2).inOrder()
        assertThat(String(data.data)).isEqualTo("abc")
    }

    private fun feed(sync: ClipboardSync, text: String): String? {
        val blob = ClipboardCodec.marshallText(text)
        sync.onServerEvent(InputLeapEvent.ClipboardData(0, 0, 1, blob.size.toString().toByteArray()))
        sync.onServerEvent(InputLeapEvent.ClipboardData(0, 0, 2, blob))
        return sync.onServerEvent(InputLeapEvent.ClipboardData(0, 0, 3, ByteArray(0)))
    }
}

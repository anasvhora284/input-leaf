package com.inputleaf.android.clipboard

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.protocol.ProtocolParser
import com.inputleaf.android.protocol.ProtocolWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import org.junit.Test

class ClipboardImageSyncTest {
    @Test fun `image only and mixed format lists retain the bitmap independently of format order`() {
        val dib = ClipboardFixtures.dib()
        val image = 2 to dib
        val text = 0 to "caption".toByteArray()
        for (formats in listOf(listOf(image), listOf(text, image), listOf(image, text))) {
            val content = ClipboardCodec.unmarshall(ClipboardFixtures.blob(*formats.toTypedArray()))!!
            assertThat(DibImage.decode(content.bitmap!!)!!.pixels).isEqualTo(ClipboardFixtures.colors)
            assertThat(content.bitmap.isReadOnly).isTrue()
            assertThat(content.text).isEqualTo(if (formats.size == 1) null else "caption")
        }
    }

    @Test fun `malformed format lists are rejected including junk after a valid image`() {
        val valid = ClipboardFixtures.blob(2 to ClipboardFixtures.dib())
        assertThat(ClipboardCodec.unmarshall(valid + 1.toByte())).isNull()
        for (count in listOf(-1, Int.MAX_VALUE)) {
            val bytes = valid.clone()
            ByteBuffer.wrap(bytes).putInt(count)
            assertThat(ClipboardCodec.unmarshall(bytes)).isNull()
        }
        for (size in listOf(-1, Int.MAX_VALUE)) {
            val bytes = valid.clone()
            ByteBuffer.wrap(bytes).putInt(8, size)
            assertThat(ClipboardCodec.unmarshall(bytes)).isNull()
        }
        val invalidSecond = ByteBuffer.allocate(valid.size + 8).putInt(2)
            .put(valid, 4, valid.size - 4).putInt(0).putInt(99).array()
        assertThat(ClipboardCodec.unmarshall(invalidSecond)).isNull()
    }

    @Test fun `unknown formats are skipped and outgoing marshaling stays text only`() {
        val content = ClipboardCodec.unmarshall(ClipboardFixtures.blob(33 to byteArrayOf(1, 2), 2 to ClipboardFixtures.dib()))!!
        assertThat(content.bitmap).isNotNull()
        val sync = ClipboardSync()
        ClipboardFixtures.events(ClipboardFixtures.blob(2 to ClipboardFixtures.dib())).forEach(sync::onServerEvent)
        assertThat(sync.onLeave(null)).isNull()
        val text = sync.onLeave("new local text")!!
        val outgoing = ByteBuffer.wrap(ClipboardCodec.marshallText(text))
        assertThat(outgoing.int).isEqualTo(1)
        assertThat(outgoing.int).isEqualTo(0)
    }

    @Test fun `a real framed chunked clipboard transfer parses through to exact image pixels`() {
        val blob = ClipboardFixtures.blob(0 to "caption".toByteArray(), 2 to ClipboardFixtures.dib())
        val out = ByteArrayOutputStream()
        val writer = ProtocolWriter(out)
        val events = ClipboardFixtures.events(blob)
        events.forEach { writer.writeClipboardData(it.id, it.seqNum, it.mark, it.data) }
        val parser = ProtocolParser(ByteArrayInputStream(out.toByteArray()))
        val sync = ClipboardSync()
        var content: ClipboardContent? = null
        repeat(events.size) { content = sync.onServerEvent(parser.readNext()) ?: content }
        assertThat(content!!.text).isEqualTo("caption")
        assertThat(DibImage.decode(content.bitmap!!)!!.pixels).isEqualTo(ClipboardFixtures.colors)
    }

    @Test fun `image transfers above the old 4 MiB limit work up to a 4K screenshot`() {
        val dib = ClipboardFixtures.dib(width = 3840, height = 2160, depth = 32)
        val blob = ClipboardFixtures.blob(2 to dib)
        assertThat(blob.size).isGreaterThan(4 * 1024 * 1024)
        val sync = ClipboardSync()
        var content: ClipboardContent? = null
        ClipboardFixtures.events(blob, chunk = ClipboardAssembler.CHUNK_BYTES)
            .forEach { content = sync.onServerEvent(it) ?: content }
        val image = DibImage.decode(requireNotNull(content).bitmap!!)!!
        assertThat(image.width).isEqualTo(3840)
        assertThat(image.height).isEqualTo(2160)
        assertThat(image.pixels.last()).isEqualTo(ClipboardFixtures.colors.last())
    }

    @Test fun `oversize start is dropped without poisoning the next clipboard`() {
        val sync = ClipboardSync()
        sync.onServerEvent(InputLeapEvent.ClipboardData(0, 1, 1, (ClipboardAssembler.MAX_BYTES.toLong() + 1).toString().toByteArray()))
        sync.onServerEvent(InputLeapEvent.ClipboardData(0, 1, 2, byteArrayOf(1)))
        assertThat(sync.onServerEvent(InputLeapEvent.ClipboardData(0, 1, 3, byteArrayOf()))).isNull()
        var content: ClipboardContent? = null
        ClipboardFixtures.events(ClipboardCodec.marshallText("next")).forEach { content = sync.onServerEvent(it) ?: content }
        assertThat(content!!.text).isEqualTo("next")
    }

    @Test fun `sequence changes and new connections discard incomplete images`() {
        val sync = ClipboardSync()
        val events = ClipboardFixtures.events(ClipboardFixtures.blob(2 to ClipboardFixtures.dib()), chunk = 1000)
        sync.onServerEvent(events[0])
        assertThat(sync.onServerEvent(InputLeapEvent.ClipboardData(0, 99, 2, events[1].data))).isNull()
        assertThat(sync.onServerEvent(events[2])).isNull()
        sync.onServerEvent(events[0])
        sync.onServerEvent(events[1])
        sync.reset()
        assertThat(sync.onServerEvent(events[2])).isNull()
        sync.onServerEvent(events[0])
        sync.onServerEvent(events[1])
        sync.onServerEvent(InputLeapEvent.ClipboardGrab(0, 88))
        assertThat(sync.onServerEvent(events[2])).isNull()
        var content: ClipboardContent? = null
        events.forEach { content = sync.onServerEvent(it) ?: content }
        assertThat(content!!.bitmap).isNotNull()
    }

    @Test fun `secondary selection image chunks cannot overwrite the primary clipboard`() {
        val sync = ClipboardSync()
        ClipboardFixtures.events(ClipboardFixtures.blob(2 to ClipboardFixtures.dib())).forEach {
            assertThat(sync.onServerEvent(InputLeapEvent.ClipboardData(1, it.seqNum, it.mark, it.data))).isNull()
        }
    }
}

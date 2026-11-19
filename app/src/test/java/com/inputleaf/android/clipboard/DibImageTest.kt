package com.inputleaf.android.clipboard

import com.google.common.truth.Truth.assertThat
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.random.Random
import org.junit.Test

class DibImageTest {
    @Test fun `24 bit rows handle BGR padding and both vertical orientations`() {
        for (topDown in listOf(false, true)) {
            val image = DibImage.decode(ByteBuffer.wrap(ClipboardFixtures.dib(topDown = topDown)))!!
            assertThat(image.width).isEqualTo(2)
            assertThat(image.height).isEqualTo(2)
            assertThat(image.pixels).isEqualTo(ClipboardFixtures.colors)
        }
    }

    @Test fun `32 bit BI_RGB reserved bytes are opaque and colors stay unchanged`() {
        for (topDown in listOf(false, true)) {
            val data = ClipboardFixtures.dib(depth = 32, topDown = topDown)
            data[43] = 127
            val image = DibImage.decode(ByteBuffer.wrap(data))!!
            assertThat(image.pixels).isEqualTo(ClipboardFixtures.colors)
        }
    }

    @Test fun `extended Windows headers and optional color tables locate the correct pixels`() {
        for (header in listOf(40, 52, 56, 108, 124)) {
            val data = ClipboardFixtures.dib(headerSize = header, paletteEntries = 2)
            assertThat(DibImage.decode(ByteBuffer.wrap(data))!!.pixels).isEqualTo(ClipboardFixtures.colors)
        }
    }

    @Test fun `read-only payload views honor their offset without changing the caller buffer`() {
        val dib = ClipboardFixtures.dib()
        val source = ByteBuffer.allocate(dib.size + 17).put(ByteArray(17)).put(dib)
        source.position(17)
        val view = source.asReadOnlyBuffer()
        assertThat(DibImage.decode(view)!!.pixels).isEqualTo(ClipboardFixtures.colors)
        assertThat(view.position()).isEqualTo(17)
    }

    @Test fun `an independently encoded BMP yields the same pixels after its file header is removed`() {
        val bmp = BufferedImage(2, 2, BufferedImage.TYPE_3BYTE_BGR)
        bmp.setRGB(0, 0, 2, 2, ClipboardFixtures.colors, 0, 2)
        val out = ByteArrayOutputStream()
        assertThat(ImageIO.write(bmp, "bmp", out)).isTrue()
        val encoded = out.toByteArray()
        assertThat(String(encoded.copyOfRange(0, 2))).isEqualTo("BM")
        assertThat(DibImage.decode(ByteBuffer.wrap(encoded, 14, encoded.size - 14))!!.pixels)
            .isEqualTo(ClipboardFixtures.colors)
    }

    @Test fun `invalid headers compression dimensions and truncated rows are rejected before allocation`() {
        val edits = listOf(0 to 12, 0 to 125, 4 to 0, 4 to -1, 4 to Int.MAX_VALUE,
            8 to 0, 8 to Int.MIN_VALUE, 8 to 8193, 16 to 3, 32 to -1)
        for ((offset, value) in edits) {
            val data = ClipboardFixtures.dib()
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value)
            assertThat(DibImage.decode(ByteBuffer.wrap(data))).isNull()
        }
        for ((offset, value) in listOf(12 to 2, 14 to 16, 14 to 8)) {
            val data = ClipboardFixtures.dib()
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, value.toShort())
            assertThat(DibImage.decode(ByteBuffer.wrap(data))).isNull()
        }
        val data = ClipboardFixtures.dib()
        for (size in 0 until data.size) assertThat(DibImage.decode(ByteBuffer.wrap(data.copyOf(size)))).isNull()
    }

    @Test fun `oversized pixel counts are rejected even with in-range individual dimensions`() {
        val header = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(40).putInt(8192).putInt(8192).putShort(1).putShort(32).array()
        assertThat(DibImage.decode(ByteBuffer.wrap(header))).isNull()
    }

    @Test fun `random malformed inputs fail without throwing or allocating from their declared dimensions`() {
        val random = Random(84)
        repeat(1000) {
            val bytes = random.nextBytes(random.nextInt(0, 256))
            assertThat(DibImage.decode(ByteBuffer.wrap(bytes))).isNull()
        }
    }
}

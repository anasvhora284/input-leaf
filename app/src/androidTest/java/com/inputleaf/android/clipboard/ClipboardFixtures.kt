package com.inputleaf.android.clipboard

import com.inputleaf.android.model.InputLeapEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object ClipboardFixtures {
    val colors = intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(), 0xffffffff.toInt())

    fun dib(width: Int = 2, height: Int = 2, depth: Int = 24, topDown: Boolean = false,
        headerSize: Int = 40, paletteEntries: Int = 0): ByteArray {
        val stride = (width * depth + 31) / 32 * 4
        val offset = headerSize + paletteEntries * 4
        val data = ByteBuffer.allocate(offset + stride * height).order(ByteOrder.LITTLE_ENDIAN)
        data.putInt(0, headerSize).putInt(4, width).putInt(8, if (topDown) -height else height)
        data.putShort(12, 1).putShort(14, depth.toShort()).putInt(32, paletteEntries)
        for (y in 0 until height) for (x in 0 until width) {
            val color = colors[(y * width + x) % colors.size]
            val pos = offset + (if (topDown) y else height - 1 - y) * stride + x * depth / 8
            data.put(pos, color.toByte()).put(pos + 1, (color ushr 8).toByte())
                .put(pos + 2, (color ushr 16).toByte())
            // BI_RGB's reserved byte is deliberately zero; treating it as alpha makes a black image.
        }
        return data.array()
    }

    fun blob(vararg formats: Pair<Int, ByteArray>): ByteArray =
        ByteBuffer.allocate(4 + formats.sumOf { 8 + it.second.size }).apply {
            putInt(formats.size)
            formats.forEach { (id, bytes) -> putInt(id); putInt(bytes.size); put(bytes) }
        }.array()

    fun events(blob: ByteArray, seq: Int = 7, chunk: Int = 11): List<InputLeapEvent.ClipboardData> = buildList {
        add(InputLeapEvent.ClipboardData(0, seq, 1, blob.size.toString().toByteArray()))
        var offset = 0
        while (offset < blob.size) {
            val end = minOf(offset + chunk, blob.size)
            add(InputLeapEvent.ClipboardData(0, seq, 2, blob.copyOfRange(offset, end)))
            offset = end
        }
        add(InputLeapEvent.ClipboardData(0, seq, 3, byteArrayOf()))
    }
}

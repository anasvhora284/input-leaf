package com.inputleaf.android.clipboard

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Deskflow Bitmap: a packed Windows DIB, with no 14-byte BMP file header. */
internal class DibImage(val width: Int, val height: Int, val pixels: IntArray) {
    companion object {
        const val MAX_PIXELS = 8 * 1024 * 1024
        const val MAX_DIMENSION = 8192

        /** Deskflow normalizes Windows images to 24/32-bit BI_RGB. Alpha in BI_RGB is undefined. */
        fun decode(source: ByteBuffer): DibImage? {
            val data = source.slice().order(ByteOrder.LITTLE_ENDIAN)
            if (data.remaining() < 40) return null
            val headerSize = data.getInt(0)
            if (headerSize !in setOf(40, 52, 56, 108, 124) || headerSize > data.limit()) return null
            val width = data.getInt(4)
            val signedHeight = data.getInt(8)
            val height = kotlin.math.abs(signedHeight.toLong())
            val planes = data.getShort(12).toInt() and 0xffff
            val depth = data.getShort(14).toInt() and 0xffff
            val compression = data.getInt(16)
            if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION.toLong() ||
                width * height > MAX_PIXELS || planes != 1 || depth !in setOf(24, 32) || compression != 0
            ) return null
            val colorsUsed = data.getInt(32).toLong() and 0xffffffffL
            val offset = headerSize.toLong() + colorsUsed * 4
            val stride = ((width.toLong() * depth + 31) / 32) * 4
            if (offset + stride * height > data.limit()) return null
            val pixels = IntArray((width * height).toInt())
            val bytesPerPixel = depth / 8
            for (y in 0 until height.toInt()) {
                val row = if (signedHeight > 0) height.toInt() - 1 - y else y
                var pos = (offset + row * stride).toInt()
                for (x in 0 until width) {
                    val blue = data.get(pos).toInt() and 0xff
                    val green = data.get(pos + 1).toInt() and 0xff
                    val red = data.get(pos + 2).toInt() and 0xff
                    pixels[y * width + x] = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
                    pos += bytesPerPixel
                }
            }
            return DibImage(width, height.toInt(), pixels)
        }
    }
}

package com.inputleaf.android.clipboard

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Deskflow Bitmap: a packed Windows DIB, with no 14-byte BMP file header. */
internal class DibImage(val width: Int, val height: Int, val pixels: IntArray) {
    companion object {
        const val MAX_PIXELS = 8 * 1024 * 1024
        const val MAX_DIMENSION = 8192

        /** BI_RGB has undefined alpha; BI_BITFIELDS may explicitly declare standard BGRA. */
        fun decode(source: ByteBuffer, onRejected: (String) -> Unit = {}): DibImage? {
            val data = source.slice().order(ByteOrder.LITTLE_ENDIAN)
            fun reject(reason: String): DibImage? {
                onRejected("Clipboard image rejected: $reason; bytes=${data.remaining()}")
                return null
            }
            if (data.remaining() < 40) return reject("truncated DIB header")
            val headerSize = data.getInt(0)
            if (headerSize !in setOf(40, 52, 56, 108, 124) || headerSize > data.limit()) {
                return reject("unsupported or truncated header=$headerSize")
            }
            val width = data.getInt(4)
            val signedHeight = data.getInt(8)
            val height = kotlin.math.abs(signedHeight.toLong())
            val planes = data.getShort(12).toInt() and 0xffff
            val depth = data.getShort(14).toInt() and 0xffff
            val compression = data.getInt(16)
            if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION.toLong() ||
                width * height > MAX_PIXELS
            ) return reject("dimensions=$width,$signedHeight exceed image limits")
            if (planes != 1 || depth !in setOf(24, 32)) {
                return reject("unsupported planes=$planes depth=$depth")
            }
            var maskBytes = 0
            var hasAlpha = false
            when (compression) {
                0 -> Unit
                3 -> {
                    if (depth != 32) return reject("BI_BITFIELDS requires depth=32")
                    if (headerSize == 40) maskBytes = 12
                    if (data.limit() < headerSize + maskBytes) return reject("truncated bitfield masks")
                    if (data.getInt(40) != 0x00ff0000 || data.getInt(44) != 0x0000ff00 ||
                        data.getInt(48) != 0x000000ff
                    ) return reject("unsupported RGB bitfield masks")
                    val alphaMask = if (headerSize >= 56) data.getInt(52) else 0
                    if (alphaMask != 0 && alphaMask != 0xff000000.toInt()) {
                        return reject("unsupported alpha bitfield mask")
                    }
                    hasAlpha = alphaMask != 0
                }
                else -> return reject("unsupported compression=$compression")
            }
            val colorsUsed = data.getInt(32).toLong() and 0xffffffffL
            val offset = headerSize.toLong() + maskBytes + colorsUsed * 4
            val stride = ((width.toLong() * depth + 31) / 32) * 4
            if (offset + stride * height > data.limit()) return reject("truncated color table or pixel rows")
            val pixels = IntArray((width * height).toInt())
            val bytesPerPixel = depth / 8
            for (y in 0 until height.toInt()) {
                val row = if (signedHeight > 0) height.toInt() - 1 - y else y
                var pos = (offset + row * stride).toInt()
                for (x in 0 until width) {
                    val blue = data.get(pos).toInt() and 0xff
                    val green = data.get(pos + 1).toInt() and 0xff
                    val red = data.get(pos + 2).toInt() and 0xff
                    val alpha = if (hasAlpha) data.get(pos + 3).toInt() and 0xff else 0xff
                    pixels[y * width + x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                    pos += bytesPerPixel
                }
            }
            return DibImage(width, height.toInt(), pixels)
        }
    }
}

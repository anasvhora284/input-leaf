package com.inputleaf.android.shizuku.uhid

/**
 * Absolute HID pen. Android 15+ treats this descriptor as a drawing tablet and draws a
 * mouse cursor at the reported position.
 *
 * Report is five bytes: tip / barrel / in-range bits, then 16-bit absolute X and Y
 * scaled to 0..[LOGICAL_MAX].
 */
internal class HidPen(private val sink: HidReportSink) {

    fun report(x: Int, y: Int, maxX: Int, maxY: Int, inRange: Boolean, tip: Boolean, barrel: Boolean): Boolean {
        // Android maps both raw axes onto the longer display side with one scale.
        // Scaling X by the width alone lands the pointer about 2x too far right.
        val extent = maxOf(maxX, maxY)
        val sx = scale(x, extent)
        val sy = scale(y, extent)
        val bits = (if (tip) 0x01 else 0) or (if (barrel) 0x02 else 0) or (if (inRange) 0x04 else 0)
        val report = byteArrayOf(
            bits.toByte(),
            (sx and 0xFF).toByte(), ((sx shr 8) and 0xFF).toByte(),
            (sy and 0xFF).toByte(), ((sy shr 8) and 0xFF).toByte(),
        )
        return sink.sendReport(report)
    }

    private fun scale(value: Int, max: Int): Int =
        if (max <= 0) 0 else ((value.coerceIn(0, max).toLong() * LOGICAL_MAX) / max).toInt()

    companion object {
        const val LOGICAL_MAX = 32767

        /**
         * Generic Desktop / Pointer. hid-input's low usage byte 0x01 sets INPUT_PROP_POINTER.
         * A Digitizer application is rejected and never becomes an input device.
         */
        fun descriptor(): ByteArray = byteArrayOf(
            0x05, 0x01,
            0x09, 0x01,
            0xA1.toByte(), 0x01,
            0x05, 0x0D,
            0x09, 0x20,
            0xA1.toByte(), 0x00,
            0x09, 0x42,
            0x09, 0x44,
            0x09, 0x32,
            0x15, 0x00,
            0x25, 0x01,
            0x75, 0x01,
            0x95.toByte(), 0x03,
            0x81.toByte(), 0x02,
            0x95.toByte(), 0x05,
            0x81.toByte(), 0x03,
            0x05, 0x01,
            0x09, 0x30,
            0x09, 0x31,
            0x15, 0x00,
            0x26, 0xFF.toByte(), 0x7F,
            0x75, 0x10,
            0x95.toByte(), 0x02,
            0x81.toByte(), 0x02,
            0xC0.toByte(),
            0xC0.toByte(),
        )
    }
}

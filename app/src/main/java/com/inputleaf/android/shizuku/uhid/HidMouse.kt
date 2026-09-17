package com.inputleaf.android.shizuku.uhid

/**
 * A standard HID relative mouse.
 *
 * Report is six bytes: button bitmask, 16-bit relative X and Y, then an 8-bit
 * relative wheel. Buttons are absolute in each report — there is no separate
 * held-button state beyond what the caller sends.
 */
internal class HidMouse(private val sink: HidReportSink) {

    fun move(dx: Int, dy: Int, buttons: Int, wheel: Int): Boolean {
        return sendReport(
            buttons = buttons and BUTTON_MASK,
            dx = dx.coerceIn(MIN_DELTA, MAX_DELTA),
            dy = dy.coerceIn(MIN_DELTA, MAX_DELTA),
            wheel = wheel.coerceIn(MIN_WHEEL, MAX_WHEEL),
        )
    }

    /** Releases every button so a disconnect cannot leave one held down. */
    fun releaseAll() {
        sendReport(buttons = 0, dx = 0, dy = 0, wheel = 0)
    }

    private fun sendReport(buttons: Int, dx: Int, dy: Int, wheel: Int): Boolean {
        val report = ByteArray(REPORT_SIZE)
        report[0] = buttons.toByte()
        report[1] = (dx and 0xFF).toByte()
        report[2] = ((dx shr 8) and 0xFF).toByte()
        report[3] = (dy and 0xFF).toByte()
        report[4] = ((dy shr 8) and 0xFF).toByte()
        report[5] = wheel.toByte()
        return sink.sendReport(report)
    }

    companion object {
        const val REPORT_SIZE = 6
        const val BUTTON_LEFT = 0x01
        const val BUTTON_RIGHT = 0x02
        const val BUTTON_MIDDLE = 0x04
        const val BUTTON_MASK = BUTTON_LEFT or BUTTON_RIGHT or BUTTON_MIDDLE

        const val MIN_DELTA = -32767
        const val MAX_DELTA = 32767
        private const val MIN_WHEEL = -127
        private const val MAX_WHEEL = 127

        val DESCRIPTOR: ByteArray = byteArrayOf(
            0x05, 0x01,
            0x09, 0x02,
            0xA1.toByte(), 0x01,
            0x09, 0x01,
            0xA1.toByte(), 0x00,
            0x05, 0x09,
            0x19, 0x01,
            0x29, 0x03,
            0x15, 0x00,
            0x25, 0x01,
            0x95.toByte(), 0x03,
            0x75, 0x01,
            0x81.toByte(), 0x02,
            0x95.toByte(), 0x01,
            0x75, 0x05,
            0x81.toByte(), 0x03,
            0x05, 0x01,
            0x09, 0x30,
            0x09, 0x31,
            0x16, 0x01, 0x80.toByte(),
            0x26, 0xFF.toByte(), 0x7F,
            0x75, 0x10,
            0x95.toByte(), 0x02,
            0x81.toByte(), 0x06,
            0x09, 0x38,
            0x15, 0x81.toByte(),
            0x25, 0x7F,
            0x75, 0x08,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x06,
            0xC0.toByte(),
            0xC0.toByte(),
        )
    }
}

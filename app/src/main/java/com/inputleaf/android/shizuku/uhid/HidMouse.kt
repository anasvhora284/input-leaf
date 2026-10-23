package com.inputleaf.android.shizuku.uhid

/**
 * A standard HID relative mouse with five buttons and both scroll wheels.
 *
 * Report is seven bytes: button bitmask, 16-bit relative X and Y, an 8-bit vertical
 * wheel, then an 8-bit horizontal wheel (Consumer AC Pan, which Linux maps to
 * REL_HWHEEL). Buttons 4 and 5 become BTN_SIDE/BTN_EXTRA, which Android turns into
 * Back and Forward. Buttons are absolute in each report.
 */
internal class HidMouse(private val sink: HidReportSink) {

    fun move(dx: Int, dy: Int, buttons: Int, wheel: Int, hWheel: Int = 0): Boolean {
        return sendReport(
            buttons = buttons and BUTTON_MASK,
            dx = dx.coerceIn(MIN_DELTA, MAX_DELTA),
            dy = dy.coerceIn(MIN_DELTA, MAX_DELTA),
            wheel = wheel.coerceIn(MIN_WHEEL, MAX_WHEEL),
            hWheel = hWheel.coerceIn(MIN_WHEEL, MAX_WHEEL),
        )
    }

    /** Releases every button so a disconnect cannot leave one held down. */
    fun releaseAll() {
        sendReport(buttons = 0, dx = 0, dy = 0, wheel = 0, hWheel = 0)
    }

    private fun sendReport(buttons: Int, dx: Int, dy: Int, wheel: Int, hWheel: Int): Boolean {
        val report = ByteArray(REPORT_SIZE)
        report[0] = buttons.toByte()
        report[1] = (dx and 0xFF).toByte()
        report[2] = ((dx shr 8) and 0xFF).toByte()
        report[3] = (dy and 0xFF).toByte()
        report[4] = ((dy shr 8) and 0xFF).toByte()
        report[5] = wheel.coerceIn(MIN_WHEEL, MAX_WHEEL).toByte()
        report[6] = hWheel.coerceIn(MIN_WHEEL, MAX_WHEEL).toByte()
        return sink.sendReport(report)
    }

    companion object {
        const val REPORT_SIZE = 7
        const val BUTTON_LEFT = 0x01
        const val BUTTON_RIGHT = 0x02
        const val BUTTON_MIDDLE = 0x04
        const val BUTTON_BACK = 0x08
        const val BUTTON_FORWARD = 0x10
        const val BUTTON_MASK = BUTTON_LEFT or BUTTON_RIGHT or BUTTON_MIDDLE or BUTTON_BACK or BUTTON_FORWARD

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
            0x29, 0x05,
            0x15, 0x00,
            0x25, 0x01,
            0x95.toByte(), 0x05,
            0x75, 0x01,
            0x81.toByte(), 0x02,
            0x95.toByte(), 0x01,
            0x75, 0x03,
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
            // Horizontal wheel: Consumer AC Pan.
            0x05, 0x0C,
            0x0A, 0x38, 0x02,
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

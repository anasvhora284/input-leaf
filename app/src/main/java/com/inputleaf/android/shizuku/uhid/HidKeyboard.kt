package com.inputleaf.android.shizuku.uhid

/**
 * A standard HID boot keyboard.
 *
 * Registering a real keyboard is what makes Android treat incoming keys as hardware
 * input. Two consequences follow, both wanted: the platform stops showing the soft
 * keyboard (giving the screen back), and the user's own IME stays selected, so its emoji
 * and GIF pickers remain available once summoned.
 *
 * Report is the 8-byte boot format: modifier bitmask, a reserved byte, then six key
 * slots. Six is the hardware limit for this format (6KRO); further simultaneous keys are
 * dropped rather than corrupting the report.
 */
internal class HidKeyboard(private val sink: HidReportSink) {

    private var modifiers = 0
    private val pressed = mutableListOf<Int>()

    fun key(evdevCode: Int, isDown: Boolean): Boolean {
        if (EvdevToHid.isModifier(evdevCode)) {
            val bit = EvdevToHid.modifierBit(evdevCode)
            modifiers = if (isDown) modifiers or bit else modifiers and bit.inv()
            sendReport()
            return true
        }

        val usage = EvdevToHid.usage(evdevCode) ?: return false
        if (isDown) {
            if (usage in pressed) return true
            if (pressed.size >= MAX_KEYS) return false
            pressed += usage
        } else {
            pressed.remove(usage)
        }
        sendReport()
        return true
    }

    /** Releases everything, so a disconnect mid-keypress cannot leave a key stuck down. */
    fun releaseAll() {
        if (modifiers == 0 && pressed.isEmpty()) return
        modifiers = 0
        pressed.clear()
        sendReport()
    }

    private fun sendReport() {
        val report = ByteArray(REPORT_SIZE)
        report[0] = modifiers.toByte()
        pressed.forEachIndexed { index, usage -> report[2 + index] = usage.toByte() }
        sink.sendReport(report)
    }

    companion object {
        const val REPORT_SIZE = 8
        const val MAX_KEYS = 6

        /** Report: modifiers(8) | reserved(8) | six key slots(8 each). */
        val DESCRIPTOR: ByteArray = byteArrayOf(
            0x05, 0x01,                  // Usage Page (Generic Desktop)
            0x09, 0x06,                  // Usage (Keyboard)
            0xA1.toByte(), 0x01,         // Collection (Application)
            0x05, 0x07,                  //   Usage Page (Keyboard/Keypad)
            0x19, 0xE0.toByte(),         //   Usage Minimum (Left Control)
            0x29, 0xE7.toByte(),         //   Usage Maximum (Right GUI)
            0x15, 0x00,                  //   Logical Minimum (0)
            0x25, 0x01,                  //   Logical Maximum (1)
            0x75, 0x01,                  //   Report Size (1)
            0x95.toByte(), 0x08,         //   Report Count (8)
            0x81.toByte(), 0x02,         //   Input (Data,Var,Abs) - modifiers
            0x95.toByte(), 0x01,         //   Report Count (1)
            0x75, 0x08,                  //   Report Size (8)
            0x81.toByte(), 0x03,         //   Input (Cnst,Var,Abs) - reserved
            0x95.toByte(), 0x06,         //   Report Count (6)
            0x75, 0x08,                  //   Report Size (8)
            0x15, 0x00,                  //   Logical Minimum (0)
            0x26, 0xFF.toByte(), 0x00,   //   Logical Maximum (255)
            0x05, 0x07,                  //   Usage Page (Keyboard/Keypad)
            0x19, 0x00,                  //   Usage Minimum (0)
            0x2A, 0xFF.toByte(), 0x00,   //   Usage Maximum (255)
            0x81.toByte(), 0x00,         //   Input (Data,Ary,Abs) - key slots
            0xC0.toByte(),               // End Collection
        )
    }
}

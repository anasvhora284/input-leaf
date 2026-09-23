package com.inputleaf.android.shizuku.uhid

/**
 * Maps Linux evdev key codes to USB HID keyboard usage codes.
 *
 * The InputLeap protocol already carries evdev scancodes and
 * [com.inputleaf.android.protocol.ProtocolScanCodeDecoder] decodes them, so this is the
 * only translation needed — no keysym table in between.
 *
 * Deliberately broader than the old `uhid-server` `KeysymToHid`, which held roughly 80
 * entries with no punctuation, symbols or numpad, and silently dropped everything else.
 */
internal object EvdevToHid {

    /** HID modifier bits, in report byte 0. */
    private val MODIFIER_BITS = mapOf(
        29 to 0x01,  // KEY_LEFTCTRL
        42 to 0x02,  // KEY_LEFTSHIFT
        56 to 0x04,  // KEY_LEFTALT
        125 to 0x08, // KEY_LEFTMETA
        97 to 0x10,  // KEY_RIGHTCTRL
        54 to 0x20,  // KEY_RIGHTSHIFT
        100 to 0x40, // KEY_RIGHTALT
        126 to 0x80, // KEY_RIGHTMETA
    )

    private val USAGES: Map<Int, Int> = buildMap {
        // Letters: evdev order is qwerty, HID order is alphabetical.
        val letterRows = listOf(
            16 to "qwertyuiop",
            30 to "asdfghjkl",
            44 to "zxcvbnm",
        )
        for ((base, row) in letterRows) {
            row.forEachIndexed { index, letter ->
                put(base + index, 0x04 + (letter - 'a'))
            }
        }

        // Digit row: evdev 2..11 is 1234567890; HID 0x1E..0x26 is 1..9 with 0 at 0x27.
        for (digit in 1..9) put(1 + digit, 0x1D + digit)
        put(11, 0x27) // KEY_0

        putAll(
            mapOf(
                1 to 0x29,   // ESC
                12 to 0x2D,  // MINUS
                13 to 0x2E,  // EQUAL
                14 to 0x2A,  // BACKSPACE
                15 to 0x2B,  // TAB
                26 to 0x2F,  // LEFTBRACE
                27 to 0x30,  // RIGHTBRACE
                28 to 0x28,  // ENTER
                39 to 0x33,  // SEMICOLON
                40 to 0x34,  // APOSTROPHE
                41 to 0x35,  // GRAVE
                43 to 0x31,  // BACKSLASH
                51 to 0x36,  // COMMA
                52 to 0x37,  // DOT
                53 to 0x38,  // SLASH
                57 to 0x2C,  // SPACE
                58 to 0x39,  // CAPSLOCK
                // Navigation
                99 to 0x46,  // SYSRQ / PrintScreen
                70 to 0x47,  // SCROLLLOCK
                119 to 0x48, // PAUSE
                110 to 0x49, // INSERT
                102 to 0x4A, // HOME
                104 to 0x4B, // PAGEUP
                111 to 0x4C, // DELETE
                107 to 0x4D, // END
                109 to 0x4E, // PAGEDOWN
                106 to 0x4F, // RIGHT
                105 to 0x50, // LEFT
                108 to 0x51, // DOWN
                103 to 0x52, // UP
                127 to 0x65, // COMPOSE / Application
                // Keypad
                69 to 0x53,  // NUMLOCK
                98 to 0x54,  // KPSLASH
                55 to 0x55,  // KPASTERISK
                74 to 0x56,  // KPMINUS
                78 to 0x57,  // KPPLUS
                96 to 0x58,  // KPENTER
                83 to 0x63,  // KPDOT
                82 to 0x62,  // KP0
            )
        )

        // Keypad 1-9: evdev order is 7 8 9 / 4 5 6 / 1 2 3; HID 0x59..0x61 is 1..9.
        val keypadRows = listOf(71 to 7, 75 to 4, 79 to 1)
        for ((base, firstDigit) in keypadRows) {
            for (offset in 0..2) put(base + offset, 0x59 + (firstDigit + offset - 1))
        }

        // F1-F10 are contiguous in both; F11/F12 sit elsewhere in evdev.
        for (index in 0..9) put(59 + index, 0x3A + index)
        put(87, 0x44) // F11
        put(88, 0x45) // F12
    }

    fun isModifier(evdevCode: Int): Boolean = MODIFIER_BITS.containsKey(evdevCode)

    fun modifierBit(evdevCode: Int): Int = MODIFIER_BITS[evdevCode] ?: 0

    /** HID usage for [evdevCode], or null when unmapped. */
    fun usage(evdevCode: Int): Int? = USAGES[evdevCode]
}

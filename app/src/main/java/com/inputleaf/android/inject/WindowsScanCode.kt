package com.inputleaf.android.inject

/** Deskflow Windows Set-1 scan codes, with bit 8 marking an extended key. */
internal object WindowsScanCode {
    private val extendedEvdev = mapOf(
        0x11c to 96,  // Keypad Enter
        0x11d to 97,  // Right Ctrl
        0x135 to 98,  // Keypad slash
        0x137 to 99,  // Print Screen
        0x138 to 100, // Right Alt
        0x147 to 102, // Home
        0x148 to 103, // Up
        0x149 to 104, // Page Up
        0x14b to 105, // Left
        0x14d to 106, // Right
        0x14f to 107, // End
        0x150 to 108, // Down
        0x151 to 109, // Page Down
        0x152 to 110, // Insert
        0x153 to 111, // Delete
        0x15b to 125, // Left Win
        0x15c to 126, // Right Win
        0x15d to 127, // Application menu
    )

    /** Common Set-1 keys overlap evdev; only a matching extended key identifies Windows. */
    fun matchesExtended(button: Int, expectedEvdev: Int): Boolean =
        extendedEvdev[button] == expectedEvdev

    fun toEvdev(button: Int): Int = extendedEvdev[button]
        ?: if (button in 1..0x7f) button else 0
}

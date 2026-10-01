package com.inputleaf.android.inject

/** Physical Carbon virtual keycodes mapped to Linux evdev, for the Android HID path. */
internal object MacKeyCode {
    // Indexed by Apple's kVK_* values (HIToolbox Events.h), before Synergy's +1 offset.
    // Zero means unmapped: let keysym/text injection handle it instead of guessing.
    private val evdev = intArrayOf(
        /* 00 */ 30, 31, 32, 33, 35, 34, 44, 45, 46, 47, 86, 48, 16, 17, 18, 19,
        /* 10 */ 21, 20, 2, 3, 4, 5, 7, 6, 13, 10, 8, 12, 9, 11, 27, 24,
        /* 20 */ 22, 26, 23, 25, 28, 38, 36, 40, 37, 39, 43, 51, 53, 49, 50, 52,
        /* 30 */ 15, 57, 41, 14, 0, 1, 126, 125, 42, 58, 56, 29, 54, 100, 97, 0,
        /* 40 */ 187, 83, 0, 55, 0, 78, 0, 69, 115, 114, 113, 98, 96, 0, 74, 188,
        /* 50 */ 189, 117, 82, 79, 80, 81, 75, 76, 77, 71, 190, 72, 73, 124, 89, 121,
        /* 60 */ 63, 64, 65, 61, 66, 67, 0, 87, 0, 183, 186, 184, 0, 68, 127, 88,
        /* 70 */ 0, 185, 110, 102, 104, 111, 62, 107, 60, 109, 59, 105, 106, 108, 103, 0,
    )

    fun toEvdev(virtualKeyCode: Int): Int = evdev.getOrElse(virtualKeyCode) { 0 }
}

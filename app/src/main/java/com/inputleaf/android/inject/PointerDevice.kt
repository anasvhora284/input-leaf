package com.inputleaf.android.inject

/**
 * How a privileged HID session presents the pointer.
 *
 * [MOUSE] is the relative HID mouse. [STYLUS] is the absolute pen Android treats as a
 * drawing tablet. Accessibility does not use this choice.
 */
enum class PointerDevice {
    MOUSE,
    STYLUS,
    ;

    val storageValue: String
        get() = when (this) {
            MOUSE -> "mouse"
            STYLUS -> "stylus"
        }

    companion object {
        fun fromStorage(value: String?): PointerDevice = when (value) {
            "stylus" -> STYLUS
            else -> MOUSE
        }
    }
}

package com.inputleaf.android.inject

import android.view.KeyEvent

/**
 * Synergy / Input Leap / Deskflow KeyButton is platform-dependent:
 * - Windows: Set-1 scan code with an extended bit (A = 30, Left Win = 0x15b)
 * - Linux evdev: native input code (A = 30, Left Meta = 125)
 * - Linux X11 and libei: X11 keycode = evdev + 8 (A = 38)
 * - macOS: Carbon virtual keycode + 1 (A = 1; zero means no physical key)
 *
 * Official Input Leap/Deskflow code uses that +8 offset on Linux. Learn which
 * encoding this connection uses by matching a key whose keysym already maps to
 * a known evdev code (Ctrl, Space, Latin letters, …), then apply it to every
 * later key — including scripts with no Latin keysym.
 */
class ProtocolScanCodeDecoder {
    private enum class Encoding { EVDEV, WINDOWS, X11, MAC }
    private var encoding: Encoding? = null
    private val pressedKeys = mutableMapOf<Int, Int>()

    /** Keep key-up paired with key-down if another held key identifies the source. */
    @Synchronized
    fun decodeKeyEvent(button: Int, keysym: Int, isDown: Boolean): Int {
        if (button <= 0) return 0
        if (!isDown) return pressedKeys.remove(button) ?: toEvdev(button, keysym)
        return pressedKeys.getOrPut(button) { toEvdev(button, keysym) }
    }

    @Synchronized
    fun clearPressedKeys() = pressedKeys.clear()

    /** A new server connection may use a different scan-code format; Leave is not a reset. */
    @Synchronized
    fun resetForConnection() {
        pressedKeys.clear()
        encoding = null
    }

    fun toEvdev(button: Int, keysym: Int): Int {
        if (button <= 0) return 0
        // With Num Lock off the server sends KP_Home etc. As a keypad key Android only
        // reaches Home through a fallback many text boxes ignore, so press the real key (#82).
        KEYPAD_NAVIGATION[keysym or 0x1000]?.let { return it }
        learn(button, keysym)
        return when (encoding) {
            Encoding.EVDEV -> button
            Encoding.WINDOWS -> WindowsScanCode.toEvdev(button)
            Encoding.X11 -> (button - X11_KEYCODE_OFFSET).coerceAtLeast(0)
            Encoding.MAC -> MacKeyCode.toEvdev(button - 1)
            // Until a known key identifies the source, avoid injecting a Mac button
            // as an unrelated Linux key (Mac A = 1 would otherwise inject Escape).
            null -> KeyMapUtils.keycodeToScanCode(KeyMapUtils.keysymToAndroidKeyCode(keysym))
        }
    }

    private fun learn(button: Int, keysym: Int) {
        if (encoding == Encoding.X11 || encoding == Encoding.MAC || encoding == Encoding.WINDOWS) return
        val keyCode = KeyMapUtils.keysymToAndroidKeyCode(keysym)
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return
        val expected = KeyMapUtils.keycodeToScanCode(keyCode)
        if (expected <= 0) return
        // Ordinary Windows keys look like evdev. Upgrade that provisional choice
        // when an extended scan code and its keysym agree; never reinterpret X11/Mac.
        if (WindowsScanCode.matchesExtended(button, expected)) {
            encoding = Encoding.WINDOWS
            return
        }
        if (encoding != null) return
        val candidates = buildList {
            if (button == expected) add(Encoding.EVDEV)
            if (button == expected + X11_KEYCODE_OFFSET) add(Encoding.X11)
            if (MacKeyCode.toEvdev(button - 1) == expected) add(Encoding.MAC)
        }
        // Some keys have the same code in two encodings. Wait for an unambiguous key.
        encoding = candidates.singleOrNull()
    }

    companion object {
        const val X11_KEYCODE_OFFSET = 8

        /** X11 keypad navigation keysym to the evdev code of the dedicated key. */
        private val KEYPAD_NAVIGATION = mapOf(
            0xFF95 to 102, // KP_Home -> Home
            0xFF96 to 105, // KP_Left
            0xFF97 to 103, // KP_Up
            0xFF98 to 106, // KP_Right
            0xFF99 to 108, // KP_Down
            0xFF9A to 104, // KP_Page_Up
            0xFF9B to 109, // KP_Page_Down
            0xFF9C to 107, // KP_End
            0xFF9E to 110, // KP_Insert
            0xFF9F to 111, // KP_Delete
        )
    }
}

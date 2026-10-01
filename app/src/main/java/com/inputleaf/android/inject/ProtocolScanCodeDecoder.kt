package com.inputleaf.android.inject

import android.view.KeyEvent

/**
 * Synergy / Input Leap / Deskflow KeyButton is platform-dependent:
 * - Windows / protocol examples: Linux evdev (A = 30)
 * - Linux X11 and libei: X11 keycode = evdev + 8 (A = 38)
 * - macOS: Carbon virtual keycode + 1 (A = 1; zero means no physical key)
 *
 * Official Input Leap/Deskflow code uses that +8 offset on Linux. Learn which
 * encoding this connection uses by matching a key whose keysym already maps to
 * a known evdev code (Ctrl, Space, Latin letters, …), then apply it to every
 * later key — including scripts with no Latin keysym.
 */
class ProtocolScanCodeDecoder {
    private enum class Encoding { EVDEV, X11, MAC }
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

    fun toEvdev(button: Int, keysym: Int): Int {
        if (button <= 0) return 0
        learn(button, keysym)
        return when (encoding) {
            Encoding.EVDEV -> button
            Encoding.X11 -> (button - X11_KEYCODE_OFFSET).coerceAtLeast(0)
            Encoding.MAC -> MacKeyCode.toEvdev(button - 1)
            // Until a known key identifies the source, avoid injecting a Mac button
            // as an unrelated Linux key (Mac A = 1 would otherwise inject Escape).
            null -> KeyMapUtils.keycodeToScanCode(KeyMapUtils.keysymToAndroidKeyCode(keysym))
        }
    }

    private fun learn(button: Int, keysym: Int) {
        if (encoding != null) return
        val keyCode = KeyMapUtils.keysymToAndroidKeyCode(keysym)
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return
        val expected = KeyMapUtils.keycodeToScanCode(keyCode)
        if (expected <= 0) return
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
    }
}

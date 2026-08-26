package com.inputleaf.android.inject

import android.view.KeyEvent

/**
 * Deskflow KeyButton is platform-dependent:
 * - Windows / protocol examples: Linux evdev (A = 30)
 * - Linux X11 and libei: X11 keycode = evdev + 8 (A = 38)
 *
 * Official Input Leap/Deskflow code uses that +8 offset on Linux. Learn which
 * encoding this connection uses by matching a key whose keysym already maps to
 * a known evdev code (Ctrl, Space, Latin letters, …), then apply it to every
 * later key — including scripts with no Latin keysym.
 */
class ProtocolScanCodeDecoder {
    private var x11Offset: Int? = null

    fun toEvdev(button: Int, keysym: Int): Int {
        if (button <= 0) return 0
        learn(button, keysym)
        val offset = x11Offset ?: 0
        return (button - offset).coerceAtLeast(0)
    }

    private fun learn(button: Int, keysym: Int) {
        if (x11Offset != null) return
        val keyCode = KeyMapUtils.keysymToAndroidKeyCode(keysym)
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return
        val expected = KeyMapUtils.keycodeToScanCode(keyCode)
        if (expected <= 0) return
        x11Offset = when (button) {
            expected -> 0
            expected + X11_KEYCODE_OFFSET -> X11_KEYCODE_OFFSET
            else -> return
        }
    }

    companion object {
        const val X11_KEYCODE_OFFSET = 8
    }
}

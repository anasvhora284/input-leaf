package com.inputleaf.android.inject

import android.view.KeyEvent

sealed class KeysymAction {
    data class KeyEventAction(
        val keyCode: Int,
        val scanCode: Int,
        val metaState: Int,
    ) : KeysymAction()

    data class Text(val char: String) : KeysymAction()

    object Ignore : KeysymAction()
}

object KeysymResolver {
    /**
     * The character a keypad digit or decimal key types, or null for any other keysym.
     * The server sends these only while its Num Lock is on (otherwise KP_Home, KP_End…).
     * Android's own keyboard starts with Num Lock off, where these keys turn into
     * navigation, so press the matching top-row key instead of the keypad key (#82).
     */
    fun keypadText(keysym: Int): String? {
        val key = if (keysym in 0xEF00..0xEFFF) keysym + 0x1000 else keysym
        return when (key) {
            in 0xFFB0..0xFFB9 -> ('0' + (key - 0xFFB0)).toString()
            0xFFAE -> "."
            else -> null
        }
    }

    fun resolve(
        keysym: Int,
        scancode: Int,
        isDown: Boolean,
        shortcutModifiers: Boolean = false,
    ): KeysymAction {
        if (!shortcutModifiers) {
            // A key event, not text: Android 16 rejects the ACTION_MULTIPLE text event.
            keypadText(keysym)?.let {
                val keyCode = if (it == ".") KeyEvent.KEYCODE_PERIOD else KeyEvent.KEYCODE_0 + (it[0] - '0')
                return KeysymAction.KeyEventAction(keyCode, KeyMapUtils.keycodeToScanCode(keyCode), metaState = 0)
            }
        }
        val keyCode = KeyMapUtils.keysymToAndroidKeyCode(keysym)
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
            return KeysymAction.KeyEventAction(
                keyCode = keyCode,
                scanCode = resolveScanCode(keyCode, scancode),
                metaState = 0,
            )
        }

        val scancodeKeyCode = if (scancode > 0) {
            KeyMapUtils.scancodeToAndroidKeyCode(scancode)
        } else {
            KeyEvent.KEYCODE_UNKNOWN
        }
        if (shortcutModifiers && scancodeKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
            return KeysymAction.KeyEventAction(
                keyCode = scancodeKeyCode,
                scanCode = scancode,
                metaState = 0,
            )
        }

        val text = KeysymUnicodeTable.lookup(keysym)
        if (text != null) {
            return if (isDown) {
                KeysymAction.Text(text)
            } else {
                KeysymAction.Ignore
            }
        }

        if (scancodeKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
            return KeysymAction.KeyEventAction(
                keyCode = scancodeKeyCode,
                scanCode = scancode,
                metaState = 0,
            )
        }

        return KeysymAction.Ignore
    }

    private fun resolveScanCode(keyCode: Int, scancode: Int): Int {
        return if (scancode > 0) scancode else KeyMapUtils.keycodeToScanCode(keyCode)
    }
}

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
    fun resolve(
        keysym: Int,
        scancode: Int,
        isDown: Boolean,
        shortcutModifiers: Boolean = false,
    ): KeysymAction {
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

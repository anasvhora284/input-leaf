package com.inputleaf.android.inject

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeyMapUtilsTest {
    @Test fun `scancodeToAndroidKeyCode maps linux evdev codes`() {
        assertThat(KeyMapUtils.scancodeToAndroidKeyCode(30)).isEqualTo(KeyEvent.KEYCODE_A)
        assertThat(KeyMapUtils.scancodeToAndroidKeyCode(57)).isEqualTo(KeyEvent.KEYCODE_SPACE)
    }

    @Test fun `scancode and keycode map desktop arrow keys to dpad codes for TV navigation`() {
        assertThat(KeyMapUtils.scancodeToAndroidKeyCode(103)).isEqualTo(KeyEvent.KEYCODE_DPAD_UP)
        assertThat(KeyMapUtils.scancodeToAndroidKeyCode(108)).isEqualTo(KeyEvent.KEYCODE_DPAD_DOWN)
        assertThat(KeyMapUtils.scancodeToAndroidKeyCode(105)).isEqualTo(KeyEvent.KEYCODE_DPAD_LEFT)
        assertThat(KeyMapUtils.scancodeToAndroidKeyCode(106)).isEqualTo(KeyEvent.KEYCODE_DPAD_RIGHT)

        assertThat(KeyMapUtils.keycodeToScanCode(KeyEvent.KEYCODE_DPAD_UP)).isEqualTo(103)
        assertThat(KeyMapUtils.keycodeToScanCode(KeyEvent.KEYCODE_DPAD_DOWN)).isEqualTo(108)
        assertThat(KeyMapUtils.keycodeToScanCode(KeyEvent.KEYCODE_DPAD_LEFT)).isEqualTo(105)
        assertThat(KeyMapUtils.keycodeToScanCode(KeyEvent.KEYCODE_DPAD_RIGHT)).isEqualTo(106)
    }

    @Test fun `scancodeToAndroidKeyCode returns unknown for unmapped codes`() {
        assertThat(KeyMapUtils.scancodeToAndroidKeyCode(9999)).isEqualTo(KeyEvent.KEYCODE_UNKNOWN)
    }

    @Test fun `hasShortcutModifiers is true for ctrl alt and win but not shift`() {
        assertThat(KeyMapUtils.hasShortcutModifiers(KeyEvent.META_CTRL_ON)).isTrue()
        assertThat(KeyMapUtils.hasShortcutModifiers(KeyEvent.META_ALT_ON)).isTrue()
        assertThat(KeyMapUtils.hasShortcutModifiers(KeyEvent.META_META_ON)).isTrue()
        assertThat(KeyMapUtils.hasShortcutModifiers(KeyEvent.META_SHIFT_ON)).isFalse()
        assertThat(KeyMapUtils.hasShortcutModifiers(0)).isFalse()
    }

    @Test fun `protocol mask treats ctrl alt win as shortcuts but not shift or altgr`() {
        assertThat(KeyMapUtils.protocolMaskHasShortcuts(KeyMapUtils.PROTOCOL_MASK_CONTROL)).isTrue()
        assertThat(KeyMapUtils.protocolMaskHasShortcuts(KeyMapUtils.PROTOCOL_MASK_ALT)).isTrue()
        assertThat(KeyMapUtils.protocolMaskHasShortcuts(KeyMapUtils.PROTOCOL_MASK_SUPER)).isTrue()
        assertThat(KeyMapUtils.protocolMaskHasShortcuts(KeyMapUtils.PROTOCOL_MASK_META)).isTrue()
        assertThat(KeyMapUtils.protocolMaskHasShortcuts(KeyMapUtils.PROTOCOL_MASK_SHIFT)).isFalse()
        assertThat(KeyMapUtils.protocolMaskHasShortcuts(0x0020)).isFalse()
        assertThat(KeyMapUtils.protocolMaskHasShortcuts(0)).isFalse()
    }

    @Test fun `protocol mask maps to Android meta flags`() {
        assertThat(KeyMapUtils.androidMetaFromProtocolMask(KeyMapUtils.PROTOCOL_MASK_CONTROL))
            .isEqualTo(KeyEvent.META_CTRL_ON)
        assertThat(
            KeyMapUtils.androidMetaFromProtocolMask(
                KeyMapUtils.PROTOCOL_MASK_CONTROL or KeyMapUtils.PROTOCOL_MASK_SHIFT
            )
        ).isEqualTo(KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
        assertThat(KeyMapUtils.androidMetaFromProtocolMask(KeyMapUtils.PROTOCOL_MASK_ALT))
            .isEqualTo(KeyEvent.META_ALT_ON)
        assertThat(KeyMapUtils.androidMetaFromProtocolMask(KeyMapUtils.PROTOCOL_MASK_SUPER))
            .isEqualTo(KeyEvent.META_META_ON)
        assertThat(KeyMapUtils.androidMetaFromProtocolMask(KeyMapUtils.PROTOCOL_MASK_META))
            .isEqualTo(KeyEvent.META_META_ON)
        assertThat(KeyMapUtils.androidMetaFromProtocolMask(KeyMapUtils.PROTOCOL_MASK_CAPS_LOCK))
            .isEqualTo(KeyEvent.META_CAPS_LOCK_ON)
        assertThat(KeyMapUtils.androidMetaFromProtocolMask(0)).isEqualTo(0)
    }

    @Test fun `updateMetaState sets and clears left and right modifiers`() {
        var meta = 0
        meta = KeyMapUtils.updateMetaState(KeyEvent.KEYCODE_SHIFT_LEFT, true, meta)
        assertThat(meta and KeyEvent.META_SHIFT_ON).isNotEqualTo(0)
        meta = KeyMapUtils.updateMetaState(KeyEvent.KEYCODE_CTRL_RIGHT, true, meta)
        assertThat(meta and KeyEvent.META_CTRL_ON).isNotEqualTo(0)
        meta = KeyMapUtils.updateMetaState(KeyEvent.KEYCODE_ALT_LEFT, true, meta)
        assertThat(meta and KeyEvent.META_ALT_ON).isNotEqualTo(0)
        meta = KeyMapUtils.updateMetaState(KeyEvent.KEYCODE_META_RIGHT, true, meta)
        assertThat(meta and KeyEvent.META_META_ON).isNotEqualTo(0)

        meta = KeyMapUtils.updateMetaState(KeyEvent.KEYCODE_SHIFT_RIGHT, false, meta)
        assertThat(meta and KeyEvent.META_SHIFT_ON).isEqualTo(0)
        meta = KeyMapUtils.updateMetaState(KeyEvent.KEYCODE_CTRL_LEFT, false, meta)
        assertThat(meta and KeyEvent.META_CTRL_ON).isEqualTo(0)
        meta = KeyMapUtils.updateMetaState(KeyEvent.KEYCODE_ALT_RIGHT, false, meta)
        assertThat(meta and KeyEvent.META_ALT_ON).isEqualTo(0)
        meta = KeyMapUtils.updateMetaState(KeyEvent.KEYCODE_META_LEFT, false, meta)
        assertThat(meta and KeyEvent.META_META_ON).isEqualTo(0)
        assertThat(KeyMapUtils.updateMetaState(KeyEvent.KEYCODE_A, true, 0)).isEqualTo(0)
    }

    @Test fun `keysymToAndroidKeyCode covers function lock and punctuation keysyms`() {
        assertThat(KeyMapUtils.keysymToAndroidKeyCode(0xFFBE)).isEqualTo(KeyEvent.KEYCODE_F1)
        assertThat(KeyMapUtils.keysymToAndroidKeyCode(0xffc9)).isEqualTo(KeyEvent.KEYCODE_F12)
        assertThat(KeyMapUtils.keysymToAndroidKeyCode(0xFF7F)).isEqualTo(KeyEvent.KEYCODE_NUM_LOCK)
        assertThat(KeyMapUtils.keysymToAndroidKeyCode(0xFF14)).isEqualTo(KeyEvent.KEYCODE_SCROLL_LOCK)
        assertThat(KeyMapUtils.keysymToAndroidKeyCode(0xFF61)).isEqualTo(KeyEvent.KEYCODE_SYSRQ)
        assertThat(KeyMapUtils.keysymToAndroidKeyCode(0xFF13)).isEqualTo(KeyEvent.KEYCODE_BREAK)
        assertThat(KeyMapUtils.keysymToAndroidKeyCode(0x20)).isEqualTo(KeyEvent.KEYCODE_SPACE)
        assertThat(KeyMapUtils.keysymToAndroidKeyCode(0xEF08)).isEqualTo(KeyEvent.KEYCODE_DEL)
        assertThat(KeyMapUtils.keysymToAndroidKeyCode(0x123456)).isEqualTo(KeyEvent.KEYCODE_UNKNOWN)
    }

    @Test fun `keycodeToScanCode maps lock keys and unknown codes`() {
        assertThat(KeyMapUtils.keycodeToScanCode(KeyEvent.KEYCODE_NUM_LOCK)).isEqualTo(69)
        assertThat(KeyMapUtils.keycodeToScanCode(KeyEvent.KEYCODE_SCROLL_LOCK)).isEqualTo(70)
        assertThat(KeyMapUtils.keycodeToScanCode(KeyEvent.KEYCODE_SYSRQ)).isEqualTo(99)
        assertThat(KeyMapUtils.keycodeToScanCode(KeyEvent.KEYCODE_BREAK)).isEqualTo(119)
        assertThat(KeyMapUtils.keycodeToScanCode(KeyEvent.KEYCODE_UNKNOWN)).isEqualTo(0)
        assertThat(KeyMapUtils.scancodeToAndroidKeyCode(69)).isEqualTo(KeyEvent.KEYCODE_UNKNOWN)
    }
}

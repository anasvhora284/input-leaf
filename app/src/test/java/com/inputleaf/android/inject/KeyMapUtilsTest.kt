package com.inputleaf.android.inject

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeyMapUtilsTest {
    @Test fun `scancodeToAndroidKeyCode maps linux evdev codes`() {
        assertThat(KeyMapUtils.scancodeToAndroidKeyCode(30)).isEqualTo(KeyEvent.KEYCODE_A)
        assertThat(KeyMapUtils.scancodeToAndroidKeyCode(57)).isEqualTo(KeyEvent.KEYCODE_SPACE)
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
    }
}

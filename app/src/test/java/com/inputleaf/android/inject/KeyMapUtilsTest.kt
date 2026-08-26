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
}

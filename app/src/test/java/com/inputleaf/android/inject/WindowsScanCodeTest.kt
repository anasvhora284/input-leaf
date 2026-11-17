package com.inputleaf.android.inject

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WindowsScanCodeTest {
    @Test fun `ordinary Set-1 letter modifier and keypad keys retain their evdev positions`() {
        for (button in listOf(1, 15, 23, 29, 30, 42, 53, 56, 57, 79)) {
            assertThat(WindowsScanCode.toEvdev(button)).isEqualTo(button)
        }
    }

    @Test fun `Win extended modifiers and navigation translate to Linux input codes`() {
        assertThat(WindowsScanCode.toEvdev(0x15b)).isEqualTo(125)
        assertThat(WindowsScanCode.toEvdev(0x15c)).isEqualTo(126)
        assertThat(WindowsScanCode.toEvdev(0x11d)).isEqualTo(97)
        assertThat(WindowsScanCode.toEvdev(0x138)).isEqualTo(100)
        assertThat(WindowsScanCode.toEvdev(0x14b)).isEqualTo(105)
        assertThat(WindowsScanCode.toEvdev(0x153)).isEqualTo(111)
        assertThat(WindowsScanCode.toEvdev(0x11c)).isEqualTo(96)
    }

    @Test fun `Windows detection requires an extended code matching the keysym position`() {
        assertThat(WindowsScanCode.matchesExtended(0x15b, 125)).isTrue()
        assertThat(WindowsScanCode.matchesExtended(0x15b, 126)).isFalse()
        assertThat(WindowsScanCode.matchesExtended(30, 30)).isFalse()
        assertThat(WindowsScanCode.matchesExtended(133, 125)).isFalse()
        assertThat(WindowsScanCode.matchesExtended(56, 125)).isFalse()
        assertThat(WindowsScanCode.matchesExtended(0x1ff, 0)).isFalse()
    }

    @Test fun `unknown extended codes use the existing keysym fallback instead of guessing`() {
        for (button in listOf(-1, 0, 0x80, 0x100, 0x1ff, 0x200)) {
            assertThat(WindowsScanCode.toEvdev(button)).isEqualTo(0)
        }
    }
}

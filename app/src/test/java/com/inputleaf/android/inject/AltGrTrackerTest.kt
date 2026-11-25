package com.inputleaf.android.inject

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AltGrTrackerTest {
    private val tracker = AltGrTracker()

    @Test
    fun `a character pressed while AltGr is held is typed and its release follows`() {
        tracker.typesChar(ALT_GR, CONTROL, 0x138, isDown = true)

        assertThat(tracker.typesChar('@'.code, NUM_LOCK, 0x27, isDown = true)).isTrue()
        assertThat(tracker.isTyping(0x27)).isTrue()
        tracker.typesChar(ALT_GR, NUM_LOCK, 0x138, isDown = false)
        assertThat(tracker.typesChar('@'.code, NUM_LOCK, 0x27, isDown = false)).isTrue()
        assertThat(tracker.isTyping(0x27)).isFalse()
    }

    @Test
    fun `the AltGr bit in the mask or ISO Level3 Shift also counts`() {
        assertThat(tracker.typesChar('e'.code, ALT_GR_MASK, 0x12, isDown = true)).isTrue()
        tracker.typesChar(0xFE03, 0, 0x138, isDown = true)
        assertThat(tracker.typesChar('q'.code, 0, 0x10, isDown = true)).isTrue()
    }

    @Test
    fun `keys without AltGr and real Ctrl or Alt shortcuts are not typed`() {
        assertThat(tracker.typesChar('q'.code, NUM_LOCK, 0x10, isDown = true)).isFalse()
        tracker.typesChar(ALT_GR, 0, 0x138, isDown = true)
        assertThat(tracker.typesChar('c'.code, CONTROL, 0x2E, isDown = true)).isFalse()
        assertThat(tracker.isTyping(0x2E)).isFalse()
    }

    @Test
    fun `a key with no character is not typed even with AltGr held`() {
        tracker.typesChar(ALT_GR, 0, 0x138, isDown = true)

        assertThat(tracker.typesChar(0xEFE3, 0, 0x1D, isDown = true)).isFalse()
    }

    @Test
    fun `releasing a key that was never typed reports false`() {
        assertThat(tracker.typesChar('q'.code, 0, 0x10, isDown = false)).isFalse()
    }

    @Test
    fun `clear forgets AltGr and typed keys`() {
        tracker.typesChar(ALT_GR, 0, 0x138, isDown = true)
        tracker.typesChar('@'.code, 0, 0x27, isDown = true)

        tracker.clear()

        assertThat(tracker.isTyping(0x27)).isFalse()
        assertThat(tracker.typesChar('q'.code, 0, 0x10, isDown = true)).isFalse()
    }

    private companion object {
        const val ALT_GR = 0xEF7E
        const val ALT_GR_MASK = 0x0020
        const val CONTROL = 0x0002
        const val NUM_LOCK = 0x2000
    }
}

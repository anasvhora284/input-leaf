package com.inputleaf.android.inject

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccessibilityMousePolicyTest {
    @Test
    fun `touch devices keep gestures for the three main buttons`() {
        for (button in 1..3) {
            assertThat(AccessibilityMousePolicy.onButtonDown(button, supportsTouchGestures = true))
                .isEqualTo(AccessibilityMouseAction.TOUCH_DOWN)
            assertThat(AccessibilityMousePolicy.onButtonUp(button, supportsTouchGestures = true))
                .isEqualTo(AccessibilityMouseAction.TOUCH_UP)
        }
    }

    @Test
    fun `side buttons never start a touch that would stay held`() {
        for (touch in listOf(true, false)) {
            for (button in 4..5) {
                assertThat(AccessibilityMousePolicy.onButtonDown(button, supportsTouchGestures = touch))
                    .isEqualTo(AccessibilityMouseAction.NONE)
            }
        }
    }

    @Test
    fun `the back side button goes back everywhere and forward does nothing`() {
        for (touch in listOf(true, false)) {
            assertThat(AccessibilityMousePolicy.onButtonUp(4, supportsTouchGestures = touch))
                .isEqualTo(AccessibilityMouseAction.GLOBAL_BACK)
            assertThat(AccessibilityMousePolicy.onButtonUp(5, supportsTouchGestures = touch))
                .isEqualTo(AccessibilityMouseAction.NONE)
        }
    }

    @Test
    fun `non-touch devices click nodes on left button release`() {
        assertThat(AccessibilityMousePolicy.onButtonDown(1, supportsTouchGestures = false))
            .isEqualTo(AccessibilityMouseAction.NONE)
        assertThat(AccessibilityMousePolicy.onButtonUp(1, supportsTouchGestures = false))
            .isEqualTo(AccessibilityMouseAction.CLICK_NODE)
    }

    @Test
    fun `non-touch devices go back on right button and home on middle button`() {
        assertThat(AccessibilityMousePolicy.onButtonUp(3, supportsTouchGestures = false))
            .isEqualTo(AccessibilityMouseAction.GLOBAL_BACK)
        assertThat(AccessibilityMousePolicy.onButtonUp(2, supportsTouchGestures = false))
            .isEqualTo(AccessibilityMouseAction.GLOBAL_HOME)
    }

    @Test
    fun `unknown buttons do nothing`() {
        for (touch in listOf(true, false)) {
            assertThat(AccessibilityMousePolicy.onButtonUp(9, supportsTouchGestures = touch))
                .isEqualTo(AccessibilityMouseAction.NONE)
            assertThat(AccessibilityMousePolicy.onButtonDown(9, supportsTouchGestures = touch))
                .isEqualTo(AccessibilityMouseAction.NONE)
        }
    }

    @Test
    fun `escape and alt left are back keys`() {
        assertThat(AccessibilityKeyPolicy.isBackKey(0xFF1B, mask = 0)).isTrue()
        assertThat(AccessibilityKeyPolicy.isBackKey(0xFF51, mask = KeyMapUtils.PROTOCOL_MASK_ALT)).isTrue()
        // Plain Left and other keys still reach the text field.
        assertThat(AccessibilityKeyPolicy.isBackKey(0xFF51, mask = 0)).isFalse()
        assertThat(AccessibilityKeyPolicy.isBackKey('a'.code, mask = KeyMapUtils.PROTOCOL_MASK_ALT)).isFalse()
    }
}

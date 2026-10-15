package com.inputleaf.android.inject

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccessibilityMousePolicyTest {
    @Test
    fun `touch devices preserve gesture down and up for every button`() {
        assertThat(AccessibilityMousePolicy.onButtonDown(supportsTouchGestures = true))
            .isEqualTo(AccessibilityMouseAction.TOUCH_DOWN)
        for (button in 1..5) {
            assertThat(AccessibilityMousePolicy.onButtonUp(button, supportsTouchGestures = true))
                .isEqualTo(AccessibilityMouseAction.TOUCH_UP)
        }
    }

    @Test
    fun `non-touch devices click nodes on left button release`() {
        assertThat(AccessibilityMousePolicy.onButtonDown(supportsTouchGestures = false))
            .isEqualTo(AccessibilityMouseAction.NONE)
        assertThat(AccessibilityMousePolicy.onButtonUp(1, supportsTouchGestures = false))
            .isEqualTo(AccessibilityMouseAction.CLICK_NODE)
    }

    @Test
    fun `non-touch devices go back on right button release`() {
        assertThat(AccessibilityMousePolicy.onButtonUp(3, supportsTouchGestures = false))
            .isEqualTo(AccessibilityMouseAction.GLOBAL_BACK)
    }

    @Test
    fun `non-touch devices ignore unsupported buttons`() {
        assertThat(AccessibilityMousePolicy.onButtonUp(2, supportsTouchGestures = false))
            .isEqualTo(AccessibilityMouseAction.NONE)
        assertThat(AccessibilityMousePolicy.onButtonUp(9, supportsTouchGestures = false))
            .isEqualTo(AccessibilityMouseAction.NONE)
    }
}

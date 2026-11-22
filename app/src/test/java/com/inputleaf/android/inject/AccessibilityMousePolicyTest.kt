package com.inputleaf.android.inject

import android.accessibilityservice.AccessibilityService
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
        fun back(keysym: Int, mask: Int) = AccessibilityKeyPolicy.globalAction(keysym, mask) == BACK
        assertThat(back(0xFF1B, mask = 0)).isTrue()
        assertThat(back(0xFF51, mask = ALT)).isTrue()
        // Input Leap / Deskflow send the 0xEFxx form (#73).
        assertThat(back(0xEF1B, mask = 0)).isTrue()
        assertThat(back(0xEF51, mask = ALT)).isTrue()
        assertThat(back(0xEF51, mask = 0)).isFalse()
    }

    @Test
    fun `system shortcuts map to global actions`() {
        fun action(keysym: Int, mask: Int) = AccessibilityKeyPolicy.globalAction(keysym, mask)
        assertThat(action(0xEF09, ALT)).isEqualTo(AccessibilityService.GLOBAL_ACTION_RECENTS)
        assertThat(action(0xEF61, 0)).isEqualTo(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        for (win in listOf(KeyMapUtils.PROTOCOL_MASK_SUPER, KeyMapUtils.PROTOCOL_MASK_META)) {
            assertThat(action('n'.code, win)).isEqualTo(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            assertThat(action('A'.code, win)).isEqualTo(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            assertThat(action('l'.code, win)).isEqualTo(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    @Test
    fun `ordinary keys still reach the text field`() {
        for ((keysym, mask) in listOf(
            0xFF51 to 0, 'a'.code to ALT, 'n'.code to 0, 'a'.code to 0, 'l'.code to 0,
            0xFF09 to 0, 'n'.code to KeyMapUtils.PROTOCOL_MASK_CONTROL,
        )) {
            assertThat(AccessibilityKeyPolicy.globalAction(keysym, mask)).isNull()
        }
    }

    private companion object {
        const val ALT = KeyMapUtils.PROTOCOL_MASK_ALT
        const val BACK = AccessibilityService.GLOBAL_ACTION_BACK
    }
}

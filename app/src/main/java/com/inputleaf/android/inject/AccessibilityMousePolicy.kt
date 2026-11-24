package com.inputleaf.android.inject

import android.accessibilityservice.AccessibilityService

internal enum class AccessibilityMouseAction {
    NONE,
    TOUCH_DOWN,
    TOUCH_UP,
    CLICK_NODE,
    GLOBAL_BACK,
    GLOBAL_HOME,
}

/** Chooses between touch gestures and TV-safe accessibility actions. */
internal object AccessibilityMousePolicy {
    private const val LEFT_BUTTON = 1
    private const val MIDDLE_BUTTON = 2
    private const val RIGHT_BUTTON = 3
    // Deskflow/Input Leap kButtonExtra0 and kButtonExtra1: the mouse's side buttons (#16).
    private const val BACK_BUTTON = 4

    private fun touches(buttonId: Int) = buttonId in LEFT_BUTTON..RIGHT_BUTTON

    fun onButtonDown(buttonId: Int, supportsTouchGestures: Boolean): AccessibilityMouseAction =
        if (supportsTouchGestures && touches(buttonId)) AccessibilityMouseAction.TOUCH_DOWN
        else AccessibilityMouseAction.NONE

    fun onButtonUp(buttonId: Int, supportsTouchGestures: Boolean): AccessibilityMouseAction {
        // Android has no global Forward, so the forward side button does nothing here.
        if (buttonId == BACK_BUTTON) return AccessibilityMouseAction.GLOBAL_BACK
        if (supportsTouchGestures) {
            return if (touches(buttonId)) AccessibilityMouseAction.TOUCH_UP else AccessibilityMouseAction.NONE
        }
        return when (buttonId) {
            LEFT_BUTTON -> AccessibilityMouseAction.CLICK_NODE
            RIGHT_BUTTON -> AccessibilityMouseAction.GLOBAL_BACK
            MIDDLE_BUTTON -> AccessibilityMouseAction.GLOBAL_HOME
            else -> AccessibilityMouseAction.NONE
        }
    }
}

/**
 * Keys the Accessibility engine turns into Android's global actions. Its keyboard goes
 * through Input Leaf's IME, which only reaches text fields, so without this Esc does
 * nothing on a launcher or in most apps (#64), and system shortcuts can't work (#86).
 */
internal object AccessibilityKeyPolicy {
    private const val KEYSYM_TAB = 0xFF09
    private const val KEYSYM_ESCAPE = 0xFF1B
    private const val KEYSYM_LEFT = 0xFF51
    private const val KEYSYM_PRINT = 0xFF61
    private const val WIN = KeyMapUtils.PROTOCOL_MASK_META or KeyMapUtils.PROTOCOL_MASK_SUPER

    /**
     * The AccessibilityService.GLOBAL_ACTION_* a key triggers, or null for a normal key;
     * press, repeat and release are all consumed. Input Leap and Deskflow send function
     * keys as 0xEFxx, X11 as 0xFFxx (#73).
     */
    fun globalAction(keysym: Int, mask: Int): Int? {
        val key = if (keysym in 0xEF00..0xEFFF) keysym + 0x1000 else keysym
        val alt = mask and KeyMapUtils.PROTOCOL_MASK_ALT != 0
        val win = mask and WIN != 0
        val shift = mask and KeyMapUtils.PROTOCOL_MASK_SHIFT != 0
        return when {
            key == KEYSYM_ESCAPE -> AccessibilityService.GLOBAL_ACTION_BACK
            key == KEYSYM_LEFT && alt -> AccessibilityService.GLOBAL_ACTION_BACK
            key == KEYSYM_TAB && alt -> AccessibilityService.GLOBAL_ACTION_RECENTS
            key == KEYSYM_PRINT -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            !win -> null
            key == 'n'.code || key == 'N'.code -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            key == 'a'.code || key == 'A'.code -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            key == 'l'.code || key == 'L'.code -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            // Windows' snipping shortcut; X11 sends the shifted 'S'.
            (key == 's'.code || key == 'S'.code) && shift -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            else -> null
        }
    }
}

package com.inputleaf.android.inject

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
 * Keys the Accessibility engine turns into Android's global Back. Its keyboard goes
 * through Input Leaf's IME, which only reaches text fields, so without this Esc does
 * nothing on a launcher or in most apps (#64).
 */
internal object AccessibilityKeyPolicy {
    private const val KEYSYM_ESCAPE = 0xFF1B
    private const val KEYSYM_LEFT = 0xFF51

    /** True for a key Input Leaf handles as Back; press and release are both consumed. */
    fun isBackKey(keysym: Int, mask: Int): Boolean =
        keysym == KEYSYM_ESCAPE ||
            (keysym == KEYSYM_LEFT && mask and KeyMapUtils.PROTOCOL_MASK_ALT != 0)
}

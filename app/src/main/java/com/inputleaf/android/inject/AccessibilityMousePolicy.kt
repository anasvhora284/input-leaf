package com.inputleaf.android.inject

internal enum class AccessibilityMouseAction {
    NONE,
    TOUCH_DOWN,
    TOUCH_UP,
    CLICK_NODE,
    GLOBAL_BACK,
}

/** Chooses between touch gestures and TV-safe accessibility actions. */
internal object AccessibilityMousePolicy {
    private const val LEFT_BUTTON = 1
    private const val RIGHT_BUTTON = 3

    fun onButtonDown(supportsTouchGestures: Boolean): AccessibilityMouseAction =
        if (supportsTouchGestures) AccessibilityMouseAction.TOUCH_DOWN else AccessibilityMouseAction.NONE

    fun onButtonUp(buttonId: Int, supportsTouchGestures: Boolean): AccessibilityMouseAction {
        if (supportsTouchGestures) return AccessibilityMouseAction.TOUCH_UP
        return when (buttonId) {
            LEFT_BUTTON -> AccessibilityMouseAction.CLICK_NODE
            RIGHT_BUTTON -> AccessibilityMouseAction.GLOBAL_BACK
            else -> AccessibilityMouseAction.NONE
        }
    }
}

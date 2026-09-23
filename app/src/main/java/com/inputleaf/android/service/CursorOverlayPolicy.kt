package com.inputleaf.android.service

import com.inputleaf.android.inject.NativePointerState

/**
 * Deterministic overlay vs native-pointer arbitration. Pure function so Enter/Leave
 * and async attach callbacks cannot disagree.
 */
internal object CursorOverlayPolicy {

    fun shouldShowOverlay(
        cursorSettingEnabled: Boolean,
        onScreen: Boolean,
        mouseEnabled: Boolean,
        native: NativePointerState,
        expectsNativePointer: Boolean,
    ): Boolean {
        if (!onScreen || !cursorSettingEnabled || !mouseEnabled) return false
        return when (native) {
            NativePointerState.PENDING, NativePointerState.ACTIVE -> false
            NativePointerState.FALLBACK -> true
            NativePointerState.NONE -> !expectsNativePointer
        }
    }
}

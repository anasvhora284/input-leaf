package com.inputleaf.android.service

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.inject.NativePointerState
import org.junit.Test

class CursorOverlayPolicyTest {

    @Test
    fun `native attaching hides overlay even if cursor setting is on`() {
        assertThat(
            CursorOverlayPolicy.shouldShowOverlay(
                cursorSettingEnabled = true,
                onScreen = true,
                mouseEnabled = true,
                native = NativePointerState.PENDING,
                expectsNativePointer = true,
            ),
        ).isFalse()
    }

    @Test
    fun `native attached hides overlay`() {
        assertThat(
            CursorOverlayPolicy.shouldShowOverlay(
                cursorSettingEnabled = true,
                onScreen = true,
                mouseEnabled = true,
                native = NativePointerState.ACTIVE,
                expectsNativePointer = true,
            ),
        ).isFalse()
    }

    @Test
    fun `expects native before attach completes hides overlay to avoid flash`() {
        assertThat(
            CursorOverlayPolicy.shouldShowOverlay(
                cursorSettingEnabled = true,
                onScreen = true,
                mouseEnabled = true,
                native = NativePointerState.NONE,
                expectsNativePointer = true,
            ),
        ).isFalse()
    }

    @Test
    fun `uhid failure shows fallback overlay when cursor setting is on`() {
        assertThat(
            CursorOverlayPolicy.shouldShowOverlay(
                cursorSettingEnabled = true,
                onScreen = true,
                mouseEnabled = true,
                native = NativePointerState.FALLBACK,
                expectsNativePointer = true,
            ),
        ).isTrue()
    }

    @Test
    fun `leave hides overlay regardless of native state`() {
        for (native in NativePointerState.entries) {
            assertThat(
                CursorOverlayPolicy.shouldShowOverlay(
                    cursorSettingEnabled = true,
                    onScreen = false,
                    mouseEnabled = true,
                    native = native,
                    expectsNativePointer = true,
                ),
            ).isFalse()
        }
    }

    @Test
    fun `accessibility without native pointer shows overlay`() {
        assertThat(
            CursorOverlayPolicy.shouldShowOverlay(
                cursorSettingEnabled = true,
                onScreen = true,
                mouseEnabled = true,
                native = NativePointerState.NONE,
                expectsNativePointer = false,
            ),
        ).isTrue()
    }

    @Test
    fun `cursor setting off never shows overlay`() {
        assertThat(
            CursorOverlayPolicy.shouldShowOverlay(
                cursorSettingEnabled = false,
                onScreen = true,
                mouseEnabled = true,
                native = NativePointerState.FALLBACK,
                expectsNativePointer = false,
            ),
        ).isFalse()
    }
}

package com.inputleaf.android.inject

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.InputLeapEvent
import org.junit.Test

class InputInjectorTest {
    @Test
    fun `default interface methods have expected fallback behavior`() {
        val injector = object : InputInjector {
            override suspend fun connect(): Boolean = true
            override fun send(event: InputLeapEvent) {}
            override fun disconnect() {}
            override fun isAvailable(): Boolean = true
            override val name: String = "TestInjector"
        }

        injector.setHidKeyboardAttached(true)
        injector.setHidMouseAttached(true)
        assertThat(injector.usesNativePointer()).isFalse()
        assertThat(injector.nativePointerState()).isEqualTo(NativePointerState.NONE)
        assertThat(injector.expectsNativePointer()).isFalse()
        injector.setOnNativePointerStateChanged(null)
        injector.updateScreenSize(1080, 2400)
        injector.updatePointerSpeed(2)
        injector.onHidMouseEnter(100, 200)
        injector.onHidMouseLeave()
    }
}

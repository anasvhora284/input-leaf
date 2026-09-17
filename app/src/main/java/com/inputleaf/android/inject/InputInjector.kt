package com.inputleaf.android.inject

import com.inputleaf.android.model.InputLeapEvent

interface InputInjector {
    suspend fun connect(): Boolean
    fun send(event: InputLeapEvent)
    fun disconnect()
    fun isAvailable(): Boolean
    val name: String
    fun setHidKeyboardAttached(attached: Boolean) {}
    fun setHidMouseAttached(attached: Boolean) {}
    fun usesNativePointer(): Boolean = false
    fun nativePointerState(): NativePointerState = NativePointerState.NONE
    fun expectsNativePointer(): Boolean = false
    fun setOnNativePointerStateChanged(listener: ((NativePointerState) -> Unit)?) {}
    fun updateScreenSize(width: Int, height: Int) {}
    fun updatePointerSpeed(speed: Int) {}

    /** Cursor entered at (x, y) in screen coords — snap the native pointer there. */
    fun onHidMouseEnter(x: Int, y: Int) {}

    /** Cursor left this screen. Marks the current HID target stale for a later detach. */
    fun onHidMouseLeave() {}
}

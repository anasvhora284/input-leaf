package com.inputleaf.android.testutil

import com.inputleaf.android.inject.InputInjector
import com.inputleaf.android.model.InputLeapEvent
import java.util.Collections

/**
 * A no-op [InputInjector] that records the HID lifecycle calls the service dispatches to it.
 *
 * The emulator CI has no Shizuku, so the real injectors are never installed there and the
 * service's HID effect arms run against a null injector. Installing this fake lets the
 * connected lifecycle tests (a) drive the `setInjector` swap/re-attach path for real and
 * (b) assert that Enter/Leave/move frames actually reach the injector, rather than merely
 * observing a null-safe no-op.
 */
class RecordingInputInjector : InputInjector {
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())
    val routedEvents: MutableList<InputLeapEvent> = Collections.synchronizedList(mutableListOf<InputLeapEvent>())

    override val name: String = "recording"

    override suspend fun connect(): Boolean {
        calls += "connect"
        return true
    }

    override fun send(event: InputLeapEvent) {
        calls += "send"
        routedEvents += event
    }

    override fun disconnect() {
        calls += "disconnect"
    }

    override fun isAvailable(): Boolean = true

    override fun setHidKeyboardAttached(attached: Boolean) {
        calls += "setHidKeyboardAttached:$attached"
    }

    override fun setHidMouseAttached(attached: Boolean) {
        calls += "setHidMouseAttached:$attached"
    }

    override fun updateScreenSize(width: Int, height: Int) {
        calls += "updateScreenSize"
    }

    override fun updatePointerSpeed(speed: Int) {
        calls += "updatePointerSpeed"
    }

    override fun onHidMouseEnter(x: Int, y: Int) {
        calls += "onHidMouseEnter"
    }

    override fun onHidMouseLeave() {
        calls += "onHidMouseLeave"
    }
}

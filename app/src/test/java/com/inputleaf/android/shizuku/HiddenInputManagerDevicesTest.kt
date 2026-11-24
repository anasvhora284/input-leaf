package com.inputleaf.android.shizuku

import android.view.InputDevice
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowInputDevice

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HiddenInputManagerDevicesTest {

    @Test
    fun `hasDevice looks the name up among the listed devices`() {
        val target = targetFor(FakeInputManager(mapOf(3 to "Logitech Mouse", 9 to "Input Leaf Mouse HID")))
        assertThat(HiddenInputManager.hasDevice(target, "Input Leaf Mouse HID")).isTrue()
        assertThat(HiddenInputManager.hasDevice(target, "Input Leaf Pen")).isFalse()
    }

    @Test
    fun `hasDevice is null without a target or a device list`() {
        assertThat(HiddenInputManager.hasDevice(null, "Input Leaf Mouse HID")).isNull()
        assertThat(HiddenInputManager.hasDevice(targetFor(Any()), "Input Leaf Mouse HID")).isNull()
    }

    private fun targetFor(instance: Any) =
        HiddenInputManager.Target(instance, Any::class.java.getMethod("toString"))

    class FakeInputManager(private val devices: Map<Int, String>) {
        fun getInputDeviceIds(): IntArray = devices.keys.toIntArray()
        fun getInputDevice(id: Int): InputDevice? = devices[id]?.let { ShadowInputDevice.makeInputDeviceNamed(it) }
    }
}

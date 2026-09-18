package com.inputleaf.android.util

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.inject.AccessibilityInputInjector
import com.inputleaf.android.shizuku.ShizukuInputInjector
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccessibilityInputInjectorPointerSpeedTest {

    @Test
    fun `updatePointerSpeed forwards to nested Shizuku injector`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val injector = AccessibilityInputInjector(context, 1080, 2400)
        val nested = nestedShizuku(injector)

        injector.updatePointerSpeed(4)
        assertThat(readPointerSpeed(nested)).isEqualTo(4)

        injector.updatePointerSpeed(-3)
        assertThat(readPointerSpeed(nested)).isEqualTo(-3)
    }

    private fun nestedShizuku(injector: AccessibilityInputInjector): ShizukuInputInjector {
        val field = AccessibilityInputInjector::class.java.getDeclaredField("hidKeyboard")
        field.isAccessible = true
        return field.get(injector) as ShizukuInputInjector
    }

    private fun readPointerSpeed(injector: ShizukuInputInjector): Int {
        val field = ShizukuInputInjector::class.java.getDeclaredField("pointerSpeed")
        field.isAccessible = true
        return field.getInt(injector)
    }
}

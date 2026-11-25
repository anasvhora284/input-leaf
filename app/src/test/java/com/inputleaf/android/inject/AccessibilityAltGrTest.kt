package com.inputleaf.android.inject

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.inputleaf.android.model.InputLeapEvent
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * #82 in Accessibility mode: a Windows server sends AltGr as a held Left Ctrl plus
 * AltGr, then the composed character with Ctrl and Alt cleared from the mask. It must
 * be typed as text, not injected as Ctrl+key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityAltGrTest {
    private var previousIme: InputLeafIME? = null
    private var previousService: AccessibilityInputService? = null
    private lateinit var ime: InputLeafIME
    private lateinit var injector: AccessibilityInputInjector

    @Before fun installInputEndpoints() {
        previousIme = InputLeafIME.getInstance()
        previousService = AccessibilityInputService.getInstance()
        ime = mock(InputLeafIME::class.java)
        ReflectionHelpers.setStaticField(InputLeafIME::class.java, "instance", ime)
        ReflectionHelpers.setStaticField(
            AccessibilityInputService::class.java, "instance", mock(AccessibilityInputService::class.java),
        )
        injector = AccessibilityInputInjector(ApplicationProvider.getApplicationContext<Context>(), 100, 100)
    }

    @After fun restoreInputEndpoints() {
        ReflectionHelpers.setStaticField(InputLeafIME::class.java, "instance", previousIme)
        ReflectionHelpers.setStaticField(AccessibilityInputService::class.java, "instance", previousService)
    }

    private fun press(keyId: Int, mask: Int, button: Int) = injector.send(InputLeapEvent.KeyDown(keyId, mask, button))
    private fun release(keyId: Int, mask: Int, button: Int) = injector.send(InputLeapEvent.KeyUp(keyId, mask, button))

    private fun pressAltGr() {
        press(CONTROL_L, NUM_LOCK, 0x1D)
        press(ALT_GR, NUM_LOCK or CONTROL, 0x138)
    }

    @Test fun `an AltGr character is typed as text, not sent as Ctrl plus the key`() {
        pressAltGr()
        press('@'.code, NUM_LOCK, 0x27)
        release('@'.code, NUM_LOCK, 0x27)
        release(ALT_GR, NUM_LOCK or CONTROL or ALT, 0x138)
        release(CONTROL_L, NUM_LOCK or CONTROL, 0x1D)

        verify(ime).commitText("@")
        verify(ime, never()).injectKeyEvent(anyInt(), eq(KeyEvent.KEYCODE_AT), anyInt())
    }

    @Test fun `a key released after AltGr is released is still swallowed`() {
        pressAltGr()
        press('@'.code, NUM_LOCK, 0x27)
        release(ALT_GR, NUM_LOCK or CONTROL or ALT, 0x138)
        release('@'.code, NUM_LOCK, 0x27)

        verify(ime, never()).injectKeyEvent(eq(KeyEvent.ACTION_UP), eq(KeyEvent.KEYCODE_AT), anyInt())
    }

    @Test fun `a held AltGr character repeats as text`() {
        pressAltGr()
        press('@'.code, NUM_LOCK, 0x27)
        injector.send(InputLeapEvent.KeyRepeat('@'.code, NUM_LOCK, 3, 0x27))

        verify(ime, times(4)).commitText("@")
    }

    @Test fun `a real Ctrl plus key is still a shortcut`() {
        press(CONTROL_L, NUM_LOCK, 0x1D)
        press('c'.code, NUM_LOCK or CONTROL, 0x2E)

        verify(ime, never()).commitText("c")
        verify(ime).injectKeyEvent(
            eq(KeyEvent.ACTION_DOWN), eq(KeyEvent.KEYCODE_C),
            eq(KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON),
        )
    }

    @Test fun `a new connection forgets a stuck AltGr`() {
        press(ALT_GR, NUM_LOCK or CONTROL, 0x138)
        injector.onNewConnection()
        press('q'.code, NUM_LOCK, 0x10)

        verify(ime, never()).commitText("q")
    }

    @Test fun `AltGr is still tracked when the keyboard is missing`() {
        ReflectionHelpers.setStaticField(InputLeafIME::class.java, "instance", null)
        press(ALT_GR, NUM_LOCK or CONTROL, 0x138)
        release(ALT_GR, NUM_LOCK, 0x138)
        ReflectionHelpers.setStaticField(InputLeafIME::class.java, "instance", ime)
        press('q'.code, NUM_LOCK, 0x10)

        verify(ime, never()).commitText("q")
    }

    private companion object {
        const val CONTROL_L = 0xEFE3
        const val ALT_GR = 0xEF7E
        const val CONTROL = 0x0002
        const val ALT = 0x0004
        const val NUM_LOCK = 0x2000
    }
}

package com.inputleaf.android.inject

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.inputleaf.android.model.InputLeapEvent
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityKeyboardSessionTest {
    private var previousIme: InputLeafIME? = null
    private var previousService: AccessibilityInputService? = null
    private lateinit var ime: InputLeafIME

    @Before fun installInputEndpoints() {
        previousIme = InputLeafIME.getInstance()
        previousService = AccessibilityInputService.getInstance()
        ime = mock(InputLeafIME::class.java)
        ReflectionHelpers.setStaticField(InputLeafIME::class.java, "instance", ime)
        ReflectionHelpers.setStaticField(
            AccessibilityInputService::class.java, "instance", mock(AccessibilityInputService::class.java),
        )
    }

    @After fun restoreInputEndpoints() {
        ReflectionHelpers.setStaticField(InputLeafIME::class.java, "instance", previousIme)
        ReflectionHelpers.setStaticField(AccessibilityInputService::class.java, "instance", previousService)
    }

    @Test fun `a new server session forgets Windows encoding and held Meta before X11 shortcuts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val injector = AccessibilityInputInjector(context, 100, 100)
        injector.send(InputLeapEvent.KeyDown(0xefeb, 0, 0x15b))
        injector.onNewConnection()
        clearInvocations(ime)

        injector.send(InputLeapEvent.KeyDown(0xefe3, 0, 37)) // X11 left Ctrl
        injector.send(InputLeapEvent.KeyDown(0, 0, 38)) // Physical A, NoSymbol

        verify(ime).injectKeyEvent(
            KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_A,
            KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON,
        )
    }
}

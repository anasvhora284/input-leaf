package com.inputleaf.android.shizuku

import android.content.ServiceConnection
import android.os.IBinder
import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.privilege.PrivilegeKind
import com.inputleaf.android.privilege.PrivilegedUserServiceHost
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

/**
 * #82: a Windows server sends AltGr as a fake Left Ctrl plus Right Alt (an extended
 * scan code the HID keyboard doesn't know), then the composed character with Ctrl and
 * Alt cleared from the mask. Sending that key to the HID keyboard typed Ctrl+ò, not `@`.
 */
@RunWith(RobolectricTestRunner::class)
class ShizukuAltGrTest {

    private val service: IInputInjector = mock(IInputInjector::class.java).also {
        // The real HID keyboard has no usage for the Windows Right Alt scan code (0x138).
        `when`(it.injectHidKey(anyInt(), anyBoolean())).thenAnswer { call -> call.arguments[0] != 0x138 }
        `when`(it.injectText(anyString())).thenReturn(true)
    }

    private fun connectedInjector(): ShizukuInputInjector = runBlocking {
        ShizukuInputInjector(100, 100, Host(service)).also { assertThat(it.connect()).isTrue() }
    }

    private fun ShizukuInputInjector.press(keyId: Int, mask: Int, button: Int) {
        send(InputLeapEvent.KeyDown(keyId, mask, button))
    }

    private fun ShizukuInputInjector.release(keyId: Int, mask: Int, button: Int) {
        send(InputLeapEvent.KeyUp(keyId, mask, button))
    }

    private fun ShizukuInputInjector.learnWindowsEncoding() {
        press('a'.code, 0, 0x1E)
        release('a'.code, 0, 0x1E)
    }

    @Test
    fun `an AltGr character from a Windows server is typed, not sent as Ctrl plus the key`() {
        val injector = connectedInjector()
        injector.learnWindowsEncoding()

        injector.press(CONTROL_L, NUM_LOCK, 0x1D)
        injector.press(ALT_GR, NUM_LOCK or CONTROL, 0x138)
        injector.press('@'.code, NUM_LOCK, 0x27)
        injector.release('@'.code, NUM_LOCK, 0x27)
        injector.release(ALT_GR, NUM_LOCK or CONTROL or ALT, 0x138)
        injector.release(CONTROL_L, NUM_LOCK or CONTROL, 0x1D)

        verify(service, never()).injectHidKey(eq(0x27), anyBoolean())
        verify(service).injectKeyEvent(eq(KeyEvent.ACTION_DOWN), eq(KeyEvent.KEYCODE_AT), anyInt(), eq(0))
        verify(service).injectKeyEvent(eq(KeyEvent.ACTION_UP), eq(KeyEvent.KEYCODE_AT), anyInt(), eq(0))
    }

    @Test
    fun `a key released after AltGr is released the way it was pressed`() {
        val injector = connectedInjector()
        injector.learnWindowsEncoding()

        injector.press(CONTROL_L, NUM_LOCK, 0x1D)
        injector.press(ALT_GR, NUM_LOCK or CONTROL, 0x138)
        injector.press('@'.code, NUM_LOCK, 0x27)
        injector.release(ALT_GR, NUM_LOCK or CONTROL or ALT, 0x138)
        injector.release(CONTROL_L, NUM_LOCK or CONTROL, 0x1D)
        injector.release('@'.code, NUM_LOCK, 0x27)

        verify(service, never()).injectHidKey(eq(0x27), anyBoolean())
        verify(service).injectKeyEvent(eq(KeyEvent.ACTION_UP), eq(KeyEvent.KEYCODE_AT), anyInt(), eq(0))
    }

    @Test
    fun `keys without AltGr still go to the HID keyboard`() {
        val injector = connectedInjector()
        injector.learnWindowsEncoding()

        injector.press(CONTROL_L, NUM_LOCK, 0x1D)
        injector.press('c'.code, NUM_LOCK or CONTROL, 0x2E)
        injector.release('c'.code, NUM_LOCK or CONTROL, 0x2E)
        injector.release(CONTROL_L, NUM_LOCK or CONTROL, 0x1D)
        injector.press('q'.code, NUM_LOCK, 0x10)

        verify(service).injectHidKey(0x2E, true)
        verify(service).injectHidKey(0x10, true)
        verify(service, never()).injectKeyEvent(anyInt(), eq(KeyEvent.KEYCODE_C), anyInt(), anyInt())
    }

    @Test
    fun `keypad digits press the top-row keys and Num Lock is not forwarded`() {
        val injector = connectedInjector()

        injector.press(NUM_LOCK_KEY, NUM_LOCK, 77)
        injector.release(NUM_LOCK_KEY, NUM_LOCK, 77)
        injector.press(KP_7, NUM_LOCK, 79)
        injector.release(KP_7, NUM_LOCK, 79)

        verify(service, never()).injectHidKey(anyInt(), anyBoolean())
        verify(service, never()).injectText(anyString())
        verify(service).injectKeyEvent(eq(KeyEvent.ACTION_DOWN), eq(KeyEvent.KEYCODE_7), anyInt(), anyInt())
        verify(service).injectKeyEvent(eq(KeyEvent.ACTION_UP), eq(KeyEvent.KEYCODE_7), anyInt(), anyInt())
    }

    private class Host(val service: IInputInjector) : PrivilegedUserServiceHost {
        private val binder: IBinder = mock(IBinder::class.java).also {
            `when`(it.queryLocalInterface(anyString())).thenReturn(service)
        }

        override val kind: PrivilegeKind = PrivilegeKind.ROOT
        override val displayName: String = "test"
        override val bindTimeoutMs: Long = 1_000L
        override fun isAvailable(): Boolean = true
        override fun bind(connection: ServiceConnection): Boolean {
            connection.onServiceConnected(null, binder)
            return true
        }
        override fun unbind(connection: ServiceConnection, destroy: Boolean) = Unit
        override fun onBindTimeout(connection: ServiceConnection) = Unit
    }

    private companion object {
        const val CONTROL_L = 0xEFE3
        const val ALT_GR = 0xEF7E
        const val CONTROL = 0x0002
        const val ALT = 0x0004
        const val NUM_LOCK = 0x2000
        const val NUM_LOCK_KEY = 0xEF7F
        const val KP_7 = 0xEFB7
    }
}

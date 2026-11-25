package com.inputleaf.android.shizuku

import android.content.ServiceConnection
import android.os.IBinder
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.privilege.PrivilegeKind
import com.inputleaf.android.privilege.PrivilegedUserServiceHost
import com.inputleaf.android.shizuku.uhid.MousePointerCompensation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

/** Deskflow's relative moves (#95) reach the virtual mouse as raw counts, not clamped targets. */
@RunWith(RobolectricTestRunner::class)
class ShizukuRelativeMouseTest {

    private val service: IInputInjector = mock(IInputInjector::class.java).also {
        `when`(it.openVirtualMouse()).thenReturn(true)
        `when`(it.moveHidMouse(anyInt(), anyInt(), anyInt())).thenReturn(1L)
    }

    @Before
    fun scale() {
        MousePointerCompensation.cursorScale = 1.0
    }

    @After
    fun restoreScale() {
        MousePointerCompensation.cursorScale = 1.0
    }

    private fun attachedInjector(): ShizukuInputInjector {
        val injector = runBlocking {
            ShizukuInputInjector(1080, 2414, Host(service)).also { assertThat(it.connect()).isTrue() }
        }
        injector.setHidMouseAttached(true)
        assertThat(injector.usesNativePointer()).isTrue()
        return injector
    }

    @Test
    fun `a relative move at the edge goes out unclamped`() {
        val injector = attachedInjector()
        injector.onHidMouseEnter(1079, 1206)
        org.mockito.Mockito.clearInvocations(service)

        assertThat(injector.tryHidMouse(InputLeapEvent.MouseMoveRel(400, 0))).isTrue()

        verify(service).moveHidMouse(400, 0, 0)
    }

    @Test
    fun `a move under half a count sends nothing and is handled`() {
        val injector = attachedInjector()
        MousePointerCompensation.cursorScale = 4.0
        org.mockito.Mockito.clearInvocations(service)

        assertThat(injector.tryHidMouse(InputLeapEvent.MouseMoveRel(1, 1))).isTrue()

        verify(service, never()).moveHidMouse(anyInt(), anyInt(), anyInt())
    }

    @Test
    fun `a failed write is not handled`() {
        val injector = attachedInjector()
        `when`(service.moveHidMouse(anyInt(), anyInt(), anyInt())).thenReturn(0L)

        assertThat(injector.tryHidMouse(InputLeapEvent.MouseMoveRel(5, 0))).isFalse()
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
}

package com.inputleaf.android.shizuku

import android.content.ServiceConnection
import android.os.IBinder
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

/** With "keep attached" (#90) a Leave no longer destroys the devices, so held input is let go instead. */
@RunWith(RobolectricTestRunner::class)
class ShizukuReleaseHeldInputTest {

    private val service: IInputInjector = mock(IInputInjector::class.java).also {
        `when`(it.injectHidKey(anyInt(), anyBoolean())).thenReturn(true)
    }

    @Test
    fun `releasing held input lets go of the keyboard and forgets pressed keys`() {
        val injector = runBlocking {
            ShizukuInputInjector(100, 100, Host(service)).also { assertThat(it.connect()).isTrue() }
        }
        injector.send(InputLeapEvent.KeyDown('a'.code, 0, 38))

        injector.releaseHeldInput()

        verify(service).releaseHidKeys()
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

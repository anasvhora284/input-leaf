package com.inputleaf.android.shizuku

import android.content.ServiceConnection
import android.os.IBinder
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.privilege.PrivilegeKind
import com.inputleaf.android.privilege.PrivilegedUserServiceHost
import com.inputleaf.android.service.sharesPrivilegedService
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

/**
 * A reconnect builds and binds a new injector before the old one is released. They
 * share one injector service per process, so the old one must not stop it.
 */
@RunWith(RobolectricTestRunner::class)
class ShizukuInjectorHandoverTest {

    @Test
    fun `handing over to the same host only drops this injector's own connection`() = runBlocking {
        val host = RecordingHost(PrivilegeKind.ROOT)
        val previous = ShizukuInputInjector(100, 100, host)
        assertThat(previous.connect()).isTrue()

        previous.disconnect(stopService = false)

        assertThat(host.unbinds).containsExactly(false)
        verify(host.service, never()).destroy()
        verify(host.service, never()).closeVirtualMouse()
        verify(host.service, never()).closeVirtualKeyboard()
        verify(host.service).releaseHidKeys()
    }

    @Test
    fun `a plain disconnect still tears the service down`() = runBlocking {
        val host = RecordingHost(PrivilegeKind.SHIZUKU)
        val injector = ShizukuInputInjector(100, 100, host)
        assertThat(injector.connect()).isTrue()

        injector.disconnect()

        assertThat(host.unbinds).containsExactly(true)
        verify(host.service).destroy()
        verify(host.service).closeVirtualMouse()
    }

    @Test
    fun `only injectors on the same host share a service`() {
        val root = ShizukuInputInjector(10, 10, RecordingHost(PrivilegeKind.ROOT))
        val otherRoot = ShizukuInputInjector(10, 10, RecordingHost(PrivilegeKind.ROOT))
        val shizuku = ShizukuInputInjector(10, 10, RecordingHost(PrivilegeKind.SHIZUKU))

        assertThat(sharesPrivilegedService(root, otherRoot)).isTrue()
        assertThat(sharesPrivilegedService(root, shizuku)).isFalse()
    }

    @Test
    fun `a bind that never connects reports the timeout before each retry`() = runBlocking {
        val host = SilentHost()
        val injector = ShizukuInputInjector(100, 100, host)

        assertThat(injector.connect()).isFalse()

        assertThat(host.events).containsExactly(
            "bind", "timeout", "unbind",
            "bind", "timeout", "unbind",
            "unbind",
        ).inOrder()
    }

    /** Accepts every bind and never connects, like a UserService that dies at startup. */
    private class SilentHost : PrivilegedUserServiceHost {
        val events = mutableListOf<String>()

        override val kind: PrivilegeKind = PrivilegeKind.SHIZUKU
        override val displayName: String = "silent"
        override val bindTimeoutMs: Long = 50L
        override val bindAttempts: Int = 2
        override fun isAvailable(): Boolean = true
        override fun bind(connection: ServiceConnection): Boolean {
            events += "bind"
            return true
        }
        override fun unbind(connection: ServiceConnection, destroy: Boolean) {
            events += "unbind"
        }
        override fun onBindTimeout(connection: ServiceConnection) {
            events += "timeout"
        }
    }

    /** Connects synchronously and records the destroy flag of every unbind. */
    private class RecordingHost(override val kind: PrivilegeKind) : PrivilegedUserServiceHost {
        val service: IInputInjector = mock(IInputInjector::class.java)
        val unbinds = mutableListOf<Boolean>()
        private val binder: IBinder = mock(IBinder::class.java).also {
            `when`(it.queryLocalInterface(anyString())).thenReturn(service)
        }

        override val displayName: String = "recording"
        override val bindTimeoutMs: Long = 1_000L
        override fun isAvailable(): Boolean = true
        override fun bind(connection: ServiceConnection): Boolean {
            connection.onServiceConnected(null, binder)
            return true
        }
        override fun unbind(connection: ServiceConnection, destroy: Boolean) {
            unbinds += destroy
        }
    }
}

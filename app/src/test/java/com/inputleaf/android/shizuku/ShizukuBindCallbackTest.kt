package com.inputleaf.android.shizuku

import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.privilege.PrivilegeKind
import com.inputleaf.android.privilege.PrivilegedUserServiceHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Shizuku calls onServiceConnected while it loops over the service's connections. A
 * Main.immediate caller of connect() must not resume inside that loop: it went on to
 * unbind the previous injector, and Shizuku crashed with ConcurrentModificationException.
 */
@RunWith(RobolectricTestRunner::class)
class ShizukuBindCallbackTest {

    @Test
    fun `connect does not resume its caller inside Shizuku's callback loop`() {
        val service = mock(IInputInjector::class.java)
        val binder = mock(IBinder::class.java).also {
            `when`(it.queryLocalInterface(anyString())).thenReturn(service)
        }
        val host = Host()
        val injector = ShizukuInputInjector(100, 100, host)
        val events = mutableListOf<String>()

        val job = CoroutineScope(Dispatchers.Main.immediate).launch {
            assertThat(injector.connect()).isTrue()
            events += "caller resumed"
        }
        val connections = linkedSetOf(host.bound!!, mock(ServiceConnection::class.java))
        for (connection in connections) {
            connection.onServiceConnected(null, binder)
            events += "callback returned"
        }

        val deadline = System.currentTimeMillis() + 5_000
        while (!job.isCompleted && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertThat(events).containsExactly("callback returned", "callback returned", "caller resumed").inOrder()
    }

    private class Host : PrivilegedUserServiceHost {
        var bound: ServiceConnection? = null
        override val kind: PrivilegeKind = PrivilegeKind.SHIZUKU
        override val displayName: String = "test"
        override val bindTimeoutMs: Long = 5_000L
        override fun isAvailable(): Boolean = true
        override fun bind(connection: ServiceConnection): Boolean {
            bound = connection
            return true
        }
        override fun unbind(connection: ServiceConnection, destroy: Boolean) = Unit
        override fun onBindTimeout(connection: ServiceConnection) = Unit
    }
}

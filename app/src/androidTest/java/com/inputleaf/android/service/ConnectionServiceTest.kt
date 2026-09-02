package com.inputleaf.android.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.testutil.ServiceBinding
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests that bind the real [ConnectionService] on an emulator: the binder must come up,
 * expose the service, and keep the connection state consistent across a user-initiated disconnect.
 */
@RunWith(AndroidJUnit4::class)
class ConnectionServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun boundServiceExposesLocalBinderAndStartsDisconnected() {
        ServiceBinding(context, ConnectionService::class.java).use { binding ->
            val binder = binding.awaitBinder()
            assertThat(binder).isInstanceOf(ConnectionService.LocalBinder::class.java)

            val service = (binder as ConnectionService.LocalBinder).getService()
            assertThat(service.state.value).isEqualTo(ConnectionState.Disconnected)
        }
    }

    @Test
    fun disconnectFromBoundServiceKeepsStateDisconnected() {
        ServiceBinding(context, ConnectionService::class.java).use { binding ->
            val service =
                (binding.awaitBinder() as ConnectionService.LocalBinder).getService()

            service.disconnect()

            assertThat(service.state.value).isEqualTo(ConnectionState.Disconnected)
        }
    }
}

package com.inputleaf.android.service

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ConnectionServiceLifecycleTest {

    @Test
    fun `connect starts the service so closing the app does not take it down`() {
        val service = Robolectric.buildService(ConnectionService::class.java).create().get()

        service.connect("192.168.1.10", "phone")

        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedService
        assertThat(started.component?.className).isEqualTo(ConnectionService::class.java.name)
        service.disconnect()
    }

    @Test
    fun `a restart after the process was killed is not asked for`() {
        val service = Robolectric.buildService(ConnectionService::class.java).create().get()

        val result = service.onStartCommand(null, 0, 1)

        assertThat(result).isEqualTo(android.app.Service.START_NOT_STICKY)
    }

    @Test
    fun `the disconnect action still stops the service`() {
        val service = Robolectric.buildService(ConnectionService::class.java).create().get()

        service.onStartCommand(Intent(ACTION_DISCONNECT), 0, 1)

        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    }
}

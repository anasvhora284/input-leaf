package com.inputleaf.android.ui

import android.app.Application
import android.content.ComponentName
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.service.ConnectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelServiceCallbacksTest {

    @Test
    fun `clearing the view model leaves no UI callbacks on the service that outlives it`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val service = Robolectric.buildService(ConnectionService::class.java).create().get()
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, ConnectionService::class.java),
            service.onBind(android.content.Intent(app, ConnectionService::class.java)),
        )
        val store = ViewModelStore()
        ViewModelProvider(store, MainViewModelFactory(app))[MainViewModel::class.java]
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertThat(service.onConnectionFailed).isNotNull()

        store.clear()

        assertThat(service.onFingerprintConfirmationRequired).isNull()
        assertThat(service.onConnectionRejected).isNull()
        assertThat(service.onConnectionFailed).isNull()
    }

    @Test
    fun `clearing the view model answers a pending trust prompt so the handshake does not hang`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val service = Robolectric.buildService(ConnectionService::class.java).create().get()
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, ConnectionService::class.java),
            service.onBind(android.content.Intent(app, ConnectionService::class.java)),
        )
        val store = ViewModelStore()
        ViewModelProvider(store, MainViewModelFactory(app))[MainViewModel::class.java]
        val looper = shadowOf(android.os.Looper.getMainLooper())
        looper.idle()
        val callback = service.onFingerprintConfirmationRequired!!
        val answer = CoroutineScope(Dispatchers.Main).async { callback("1.2.3.4", "aa", "") }
        looper.idle()
        assertThat(answer.isCompleted).isFalse()

        store.clear()
        looper.idle()

        assertThat(answer.isCompleted).isTrue()
        assertThat(answer.getCompleted()).isFalse()
    }
}

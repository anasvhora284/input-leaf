package com.inputleaf.android.shizuku

import android.content.ComponentName
import android.content.ContentValues
import android.content.ServiceConnection
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBinder

/**
 * The handoff provider is exported so the shell process can reach it; only root and
 * shell may hand the app an injector.
 */
@RunWith(RobolectricTestRunner::class)
class InjectorHandoffProviderTest {

    private val connected = mutableListOf<IBinder?>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            connected += service
        }
        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    @After
    fun tearDown() {
        ShadowBinder.reset()
        StandaloneInjectorLauncher.unbind(connection, destroy = true)
    }

    @Test
    fun `an app uid cannot hand over an injector`() {
        val provider = Robolectric.setupContentProvider(InjectorHandoffProvider::class.java)
        ShadowBinder.setCallingUid(10_123)

        val reply = provider.call(InjectorHandoff.METHOD_ATTACH, null, extrasWith(Binder()))

        assertThat(reply).isNull()
    }

    @Test
    fun `shell hands over the injector and gets the client token back`() {
        val provider = Robolectric.setupContentProvider(InjectorHandoffProvider::class.java)
        // Registers the connection; no Shizuku in tests, so the launch itself fails.
        StandaloneInjectorLauncher.bind(connection)
        ShadowBinder.setCallingUid(2000)
        val injector = Binder()

        val reply = provider.call(InjectorHandoff.METHOD_ATTACH, null, extrasWith(injector))
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(reply?.getBinder(InjectorHandoff.KEY_CLIENT))
            .isSameInstanceAs(StandaloneInjectorLauncher.clientToken)
        assertThat(connected).containsExactly(injector)
    }

    @Test
    fun `unknown methods are ignored`() {
        val provider = Robolectric.setupContentProvider(InjectorHandoffProvider::class.java)
        ShadowBinder.setCallingUid(2000)

        assertThat(provider.call("somethingElse", null, extrasWith(Binder()))).isNull()
    }

    @Test
    fun `the provider exposes no data`() {
        val provider = Robolectric.setupContentProvider(InjectorHandoffProvider::class.java)
        val uri = Uri.parse("content://${InjectorHandoffProvider.authority(provider.context!!)}/x")

        assertThat(provider.query(uri, null, null, null, null)).isNull()
        assertThat(provider.getType(uri)).isNull()
        assertThat(provider.insert(uri, ContentValues())).isNull()
        assertThat(provider.delete(uri, null, null)).isEqualTo(0)
        assertThat(provider.update(uri, ContentValues(), null, null)).isEqualTo(0)
    }

    private fun extrasWith(injector: IBinder) =
        Bundle().apply { putBinder(InjectorHandoff.KEY_INJECTOR, injector) }
}

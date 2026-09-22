package com.inputleaf.android.privilege

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.topjohnwu.superuser.ipc.RootService
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class PrivilegedUserServiceHostTest {

    @Test
    fun `root bind intent uses daemon mode so uid-0 injector stays warm`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = RootInputInjectorService.bindIntent(context)
        assertThat(intent.hasCategory(RootService.CATEGORY_DAEMON_MODE)).isTrue()
        assertThat(intent.hasCategory(RootInputInjectorService.CATEGORY_VERSION)).isTrue()
        assertThat(intent.component?.className)
            .isEqualTo(RootInputInjectorService::class.java.name)
    }

    @Test
    fun `hostFor root does not require Shizuku`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val host = PrivilegedInjectorFactory.hostFor(context, PrivilegeKind.ROOT)
        assertThat(host.kind).isEqualTo(PrivilegeKind.ROOT)
        assertThat(host.displayName).contains("HID")
    }

    @Test
    fun `create with explicit root keeps privilege attach-only`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshot = PrivilegeSnapshot(
            shizukuReady = true,
            root = RootAvailability.GRANTED,
        )
        val injector = PrivilegedInjectorFactory.create(
            context,
            screenWidth = 1080,
            screenHeight = 1920,
            snapshot = snapshot,
            method = "root",
        )
        assertThat(injector.privilegeKind()).isEqualTo(PrivilegeKind.ROOT)
        assertThat(injector.name).contains("HID")
    }
}

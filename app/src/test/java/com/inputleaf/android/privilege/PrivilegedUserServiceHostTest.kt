package com.inputleaf.android.privilege

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.inject.AccessibilityInputInjector
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mockStatic
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import rikka.shizuku.Shizuku

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

    @Test
    fun `root service bind unbind and destroy stay on the shared stub`() {
        val service = RootInputInjectorService()
        assertThat(service.onBind(Intent())).isNotNull()
        assertThat(service.onUnbind(Intent())).isTrue()
        service.onDestroy()
    }

    @Test
    fun `shizuku host reports availability and survives a failed bind`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockStatic(Shizuku::class.java).use { shizuku ->
            shizuku.`when`<Boolean> { Shizuku.pingBinder() }.thenReturn(true)
            shizuku.`when`<Int> { Shizuku.checkSelfPermission() }
                .thenReturn(PackageManager.PERMISSION_GRANTED)
            val host = PrivilegedInjectorFactory.hostFor(context, PrivilegeKind.SHIZUKU)
            assertThat(host.kind).isEqualTo(PrivilegeKind.SHIZUKU)
            assertThat(host.bindTimeoutMs).isEqualTo(10_000L)
            assertThat(host.bindAttempts).isEqualTo(3)
            assertThat(host.isAvailable()).isTrue()
            assertThat(host.bind(connection)).isTrue()
            host.unbind(connection, destroy = true)

            shizuku.`when`<Int> { Shizuku.checkSelfPermission() }
                .thenReturn(PackageManager.PERMISSION_DENIED)
            assertThat(host.isAvailable()).isFalse()

            shizuku.`when`<Boolean> { Shizuku.pingBinder() }
                .thenThrow(IllegalStateException("binder dead"))
            assertThat(host.isAvailable()).isFalse()

            shizuku.`when`<Boolean> { Shizuku.pingBinder() }.thenReturn(true)
            shizuku.`when`<Unit> {
                Shizuku.bindUserService(
                    any(Shizuku.UserServiceArgs::class.java),
                    any(ServiceConnection::class.java),
                )
            }.thenThrow(IllegalStateException("bind"))
            assertThat(host.bind(connection)).isFalse()
        }
    }

    @Test
    fun `root host binds on the main thread and posts when called off it`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val granted = object : RootAccess {
            override fun availability() = RootAvailability.GRANTED
            override fun requestAccess() = true
        }
        val denied = object : RootAccess {
            override fun availability() = RootAvailability.DENIED
            override fun requestAccess() = false
        }
        val host = PrivilegedInjectorFactory.hostFor(context, PrivilegeKind.ROOT, granted)
        assertThat(host.isAvailable()).isTrue()
        assertThat(host.bindTimeoutMs).isEqualTo(30_000L)
        assertThat(host.bindAttempts).isEqualTo(2)
        assertThat(host.bind(connection)).isTrue()
        mockStatic(RootService::class.java).use { root ->
            root.`when`<Unit> {
                RootService.bind(any(Intent::class.java), any(ServiceConnection::class.java))
            }.thenThrow(IllegalStateException("bind"))
            assertThat(host.bind(connection)).isTrue()
        }
        host.unbind(connection, destroy = false)
        host.unbind(connection, destroy = true)
        assertThat(
            PrivilegedInjectorFactory.hostFor(context, PrivilegeKind.ROOT, denied).isAvailable(),
        ).isFalse()
        val bare = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context? = null
        }
        assertThat(PrivilegedInjectorFactory.hostFor(bare, PrivilegeKind.ROOT, granted).kind)
            .isEqualTo(PrivilegeKind.ROOT)

        val worker = Thread { host.bind(connection) }
        worker.start()
        worker.join()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `factory falls back to a Shizuku host when nothing is granted`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThat(PrivilegedInjectorFactory.hostFor(context, PrivilegeKind.NONE).kind)
            .isEqualTo(PrivilegeKind.SHIZUKU)
        val created = PrivilegedInjectorFactory.create(context, 10, 20)
        assertThat(created.name).contains("Shizuku")
        val snapshot = PrivilegedInjectorFactory.currentSnapshot(object : RootAccess {
            override fun availability() = RootAvailability.GRANTED
            override fun requestAccess() = false
        })
        assertThat(snapshot.root).isEqualTo(RootAvailability.GRANTED)
        assertThat(snapshot.shizukuReady).isFalse()
    }

    @Test
    fun `accessibility latent hid does not bind root that was never asked`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previous = LibSuRootAccess.pathExists
        try {
            LibSuRootAccess.pathExists = { it == "/proc/ksu" }
            mockStatic(Shell::class.java).use { shells ->
                shells.`when`<Boolean?> { Shell.isAppGrantedRoot() }.thenReturn(null)
                val neverAsked = AccessibilityInputInjector(context, 1080, 1920)
                assertThat(neverAsked.privilegeKind()).isNotEqualTo(PrivilegeKind.ROOT)

                shells.`when`<Boolean?> { Shell.isAppGrantedRoot() }.thenReturn(true)
                val granted = AccessibilityInputInjector(context, 1080, 1920)
                assertThat(granted.privilegeKind()).isEqualTo(PrivilegeKind.ROOT)
            }
        } finally {
            LibSuRootAccess.pathExists = previous
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) = Unit
        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }
}

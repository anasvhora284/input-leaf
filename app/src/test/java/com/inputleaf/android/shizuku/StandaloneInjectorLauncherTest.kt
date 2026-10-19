package com.inputleaf.android.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The launcher is the app side of the app_process fallback. Shizuku cannot fork anything
 * in a unit test, so the process comes from the [StandaloneInjectorLauncher.launchProcess]
 * hook and the rest (one injector per app process, warm reuse, death handling) runs for real.
 */
@RunWith(RobolectricTestRunner::class)
class StandaloneInjectorLauncherTest {

    private val launched = mutableListOf<FakeProcess>()
    private val commands = mutableListOf<List<String>>()

    private class Events : ServiceConnection {
        val connected = mutableListOf<IBinder?>()
        var disconnects = 0
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            connected += service
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            disconnects++
        }
    }

    @Before
    fun setUp() {
        // Providers run before Application.onCreate, which is where the launcher gets its Context.
        Robolectric.setupContentProvider(InjectorHandoffProvider::class.java)
        StandaloneInjectorLauncher.resetForTest()
        StandaloneInjectorLauncher.launchProcess = { cmd ->
            commands += cmd.toList()
            FakeProcess().also { launched += it }
        }
    }

    @After
    fun tearDown() = StandaloneInjectorLauncher.resetForTest()

    @Test
    fun `bind forks one injector and later binds share it while it is alive`() {
        assertThat(StandaloneInjectorLauncher.bind(Events())).isTrue()
        assertThat(StandaloneInjectorLauncher.bind(Events())).isTrue()

        assertThat(launched).hasSize(1)
        val command = commands.single()
        assertThat(command.take(2)).containsExactly("sh", "-c").inOrder()
        assertThat(command[2]).contains("CLASSPATH='")
        assertThat(command[2]).contains("exec app_process /system/bin")
        assertThat(command[2]).contains("--nice-name=")
        assertThat(command[2]).contains(StandaloneInjectorMain::class.java.name)
        assertThat(command[2]).contains(".injector 0")
    }

    @Test
    fun `bind refuses to start without the provider's app context`() {
        InjectorHandoffProvider.clearAppContextForTest()

        assertThat(StandaloneInjectorLauncher.bind(Events())).isFalse()
        assertThat(launched).isEmpty()
    }

    @Test
    fun `the forked process output is drained so its pipes cannot fill`() {
        val process = FakeProcess(stdout = "hello\n", stderr = "oops\n")
        StandaloneInjectorLauncher.launchProcess = { process }

        assertThat(StandaloneInjectorLauncher.bind(Events())).isTrue()

        // Daemon threads started by bind; wait for them rather than sleeping.
        Thread.getAllStackTraces().keys
            .filter { it.name.startsWith("standalone-injector-std") }
            .forEach { it.join(2_000) }
        assertThat(process.stdoutRead).isTrue()
        assertThat(process.stderrRead).isTrue()
    }

    @Test
    fun `a launch that throws reports failure and the next bind tries again`() {
        StandaloneInjectorLauncher.launchProcess = { throw IllegalStateException("no newProcess") }
        assertThat(StandaloneInjectorLauncher.bind(Events())).isFalse()

        StandaloneInjectorLauncher.launchProcess = { FakeProcess().also { launched += it } }
        assertThat(StandaloneInjectorLauncher.bind(Events())).isTrue()
        assertThat(launched).hasSize(1)
    }

    @Test
    fun `a warm injector is handed straight to the next connection without forking`() {
        val first = Events()
        StandaloneInjectorLauncher.bind(first)
        val injector = Binder()
        StandaloneInjectorLauncher.onInjectorAttached(injector)
        // A reconnect releases its old connection without destroying the injector.
        StandaloneInjectorLauncher.unbind(first, destroy = false)
        val second = Events()

        assertThat(StandaloneInjectorLauncher.bind(second)).isTrue()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(launched).hasSize(1)
        assertThat(second.connected).containsExactly(injector)
    }

    @Test
    fun `unbind with destroy stops the process and the next bind forks a new one`() {
        val events = Events()
        StandaloneInjectorLauncher.bind(events)

        StandaloneInjectorLauncher.unbind(events, destroy = true)
        assertThat(launched.single().destroyed).isTrue()

        StandaloneInjectorLauncher.bind(Events())
        assertThat(launched).hasSize(2)
    }

    @Test
    fun `unbind without destroy leaves the process running`() {
        val events = Events()
        StandaloneInjectorLauncher.bind(events)

        StandaloneInjectorLauncher.unbind(events, destroy = false)

        assertThat(launched.single().destroyed).isFalse()
    }

    @Test
    fun `a stale connection cannot unregister the current one`() {
        val stale = Events()
        val current = Events()
        StandaloneInjectorLauncher.bind(stale)
        StandaloneInjectorLauncher.bind(current)

        StandaloneInjectorLauncher.unbind(stale, destroy = false)
        val injector = Binder()
        StandaloneInjectorLauncher.onInjectorAttached(injector)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(current.connected).containsExactly(injector)
        assertThat(stale.connected).isEmpty()
    }

    @Test
    fun `the injector dying disconnects the app and allows a fresh fork`() {
        val events = Events()
        StandaloneInjectorLauncher.bind(events)
        val injector = Binder()
        StandaloneInjectorLauncher.onInjectorAttached(injector)

        // A different binder is a stale notification and must be ignored.
        StandaloneInjectorLauncher.onInjectorDied(Binder())
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(events.disconnects).isEqualTo(0)

        StandaloneInjectorLauncher.onInjectorDied(injector)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(events.disconnects).isEqualTo(1)

        StandaloneInjectorLauncher.bind(Events())
        assertThat(launched).hasSize(2)
    }

    @Test
    fun `an injector that is already dead on arrival is reported as a disconnect`() {
        val events = Events()
        StandaloneInjectorLauncher.bind(events)
        val dead = object : Binder() {
            override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) {
                throw RemoteException("already dead")
            }
        }

        StandaloneInjectorLauncher.onInjectorAttached(dead)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(events.connected).isEmpty()
        assertThat(events.disconnects).isEqualTo(1)
    }

    /** A process that stays alive until [destroy] and serves canned output. */
    private class FakeProcess(stdout: String = "", stderr: String = "") : Process() {
        var destroyed = false
        var stdoutRead = false
        var stderrRead = false
        private val out = tracking(stdout) { stdoutRead = true }
        private val err = tracking(stderr) { stderrRead = true }

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = out
        override fun getErrorStream(): InputStream = err
        override fun waitFor(): Int = 0
        override fun exitValue(): Int =
            if (destroyed) 143 else throw IllegalThreadStateException("still running")
        override fun destroy() {
            destroyed = true
        }

        private fun tracking(text: String, onEof: () -> Unit) =
            object : ByteArrayInputStream(text.toByteArray()) {
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    super.read(b, off, len).also { if (it < 0) onEof() }
            }
    }
}

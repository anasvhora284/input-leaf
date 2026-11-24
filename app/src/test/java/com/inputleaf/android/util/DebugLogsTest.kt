package com.inputleaf.android.util

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DebugLogsTest {

    @Test
    fun `the file has the header, the app section and the injector section`() {
        val text = DebugLogs.compose("Input Leaf 1.4.7", "app line", "injector line")
        assertThat(text).startsWith("Input Leaf 1.4.7\n")
        assertThat(text.indexOf("=== App ===\napp line")).isLessThan(text.indexOf("=== Injector"))
        assertThat(text).contains("=== Injector (Shizuku/root) ===\ninjector line")
    }

    @Test
    fun `a missing injector or empty log says so`() {
        val text = DebugLogs.compose("h", "", null)
        assertThat(text).contains("=== App ===\n(no lines)")
        assertThat(text).contains("=== Injector (Shizuku/root) ===\n(not running)")
    }

    @Test
    fun `logcat is asked for the last lines, of one process when given`() {
        assertThat(DebugLogs.readLogcat(5, pid = 7, program = "echo").trim())
            .isEqualTo("-d -v threadtime -t 5 --pid=7")
        assertThat(DebugLogs.readLogcat(5, program = "echo").trim()).isEqualTo("-d -v threadtime -t 5")
        assertThat(DebugLogs.readLogcat(5, program = "/nonexistent/logcat")).isEmpty()
    }

    @Test
    fun `sharing writes the file and offers it through the logs provider`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val chooser = DebugLogs.shareIntent(context, "1.4.7-beta.1 (17)", "injector line")

        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertThat(send.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertThat(uri.authority).isEqualTo("${context.packageName}.logs")

        val info = context.packageManager.resolveContentProvider(uri.authority!!, PackageManager.GET_META_DATA)!!
        Robolectric.buildContentProvider(DebugLogProvider::class.java).create(info)
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        assertThat(text).startsWith("Input Leaf 1.4.7-beta.1 (17) · ")
        assertThat(text).contains("=== Injector (Shizuku/root) ===\ninjector line")
    }
}

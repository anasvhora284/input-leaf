package com.inputleaf.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

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
}

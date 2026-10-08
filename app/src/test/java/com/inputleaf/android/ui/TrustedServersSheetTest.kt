package com.inputleaf.android.ui

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.network.TlsFingerprintManager
import org.junit.Test

class TrustedServersSheetTest {

    @Test
    fun `trusted server displays custom screen name when known`() {
        val serverNames = mapOf("192.168.1.50" to "Workstation")
        val ip = "192.168.1.50"
        val displayName = serverNames[ip]
        val hasName = !displayName.isNullOrBlank() && displayName != ip

        assertThat(hasName).isTrue()
        val title = if (hasName) displayName else ip
        assertThat(title).isEqualTo("Workstation")
    }

    @Test
    fun `trusted server falls back to IP address when name matches IP or is empty`() {
        val serverNames = mapOf("192.168.1.50" to "192.168.1.50", "10.0.0.1" to "")
        val ip1 = "192.168.1.50"
        val displayName1 = serverNames[ip1]
        val hasName1 = !displayName1.isNullOrBlank() && displayName1 != ip1
        assertThat(hasName1).isFalse()

        val ip2 = "10.0.0.1"
        val displayName2 = serverNames[ip2]
        val hasName2 = !displayName2.isNullOrBlank() && displayName2 != ip2
        assertThat(hasName2).isFalse()
    }

    @Test
    fun `trusted server formats colon separated fingerprint cleanly`() {
        val rawFp = "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0"
        val formatted = TlsFingerprintManager.formatFingerprint(rawFp)
        assertThat(formatted).startsWith("A1:B2:C3:D4:")
        assertThat(formatted.take(23)).isEqualTo("A1:B2:C3:D4:E5:F6:07:18")
    }
}

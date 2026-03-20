package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerScannerTest {
    @Test fun `derives correct subnet from IP`() {
        val hosts = ServerScanner.subnetHosts("192.168.1.47")
        assertThat(hosts).hasSize(254)
        assertThat(hosts).contains("192.168.1.1")
        assertThat(hosts).contains("192.168.1.254")
        assertThat(hosts).doesNotContain("192.168.1.0")
        assertThat(hosts).doesNotContain("192.168.1.255")
    }

    @Test fun `excludes the device own IP`() {
        val hosts = ServerScanner.subnetHosts("192.168.1.47")
        assertThat(hosts).doesNotContain("192.168.1.47")
    }
}

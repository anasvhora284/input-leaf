package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.model.WireProtocol
import org.junit.Test

class ServerScannerTest {
    private fun hello(protocol: WireProtocol, major: Int, minor: Int): ByteArray =
        protocol.magic.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(
                (major shr 8).toByte(),
                major.toByte(),
                (minor shr 8).toByte(),
                minor.toByte(),
            )

    @Test fun `derives correct subnet from IP`() {
        val hosts = ServerScanner.subnetHosts("192.168.1.47")
        assertThat(hosts).hasSize(253) // 254 minus the device's own IP (.47)
        assertThat(hosts).contains("192.168.1.1")
        assertThat(hosts).contains("192.168.1.254")
        assertThat(hosts).doesNotContain("192.168.1.0")
        assertThat(hosts).doesNotContain("192.168.1.255")
    }

    @Test fun `excludes the device own IP`() {
        val hosts = ServerScanner.subnetHosts("192.168.1.47")
        assertThat(hosts).doesNotContain("192.168.1.47")
    }

    @Test fun `recognizes Barrier and Synergy server hello messages`() {
        for (protocol in WireProtocol.entries) {
            assertThat(ServerScanner.parseHello("192.168.1.10", hello(protocol, 1, 8)))
                .isEqualTo(ServerInfo(ip = "192.168.1.10", name = "InputLeap 1.8"))
        }
    }

    @Test fun `rejects unknown and truncated server hello messages`() {
        assertThat(
            ServerScanner.parseHello(
                "192.168.1.10",
                "Unknown".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 1, 0, 8),
            )
        ).isNull()
        assertThat(
            ServerScanner.parseHello(
                "192.168.1.10",
                WireProtocol.BARRIER.magic.toByteArray(Charsets.US_ASCII),
            )
        ).isNull()
    }
}

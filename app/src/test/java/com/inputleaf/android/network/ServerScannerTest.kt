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

    @Test fun `parses server screen name when present in hello message`() {
        val nameBytes = "Anas-MacBook-Pro".toByteArray(Charsets.UTF_8)
        val body = hello(WireProtocol.BARRIER, 1, 8) +
            byteArrayOf(0, 0, 0, nameBytes.size.toByte()) +
            nameBytes
        assertThat(ServerScanner.parseHello("192.168.1.10", body))
            .isEqualTo(ServerInfo(ip = "192.168.1.10", name = "Anas-MacBook-Pro"))
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
        assertThat(
            ServerScanner.parseHello(
                "192.168.1.10",
                ByteArray(5),
            )
        ).isNull()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `subnetHosts throws on invalid IP format`() {
        ServerScanner.subnetHosts("invalid-ip")
    }

    @Test fun `probe returns server info when plain server responds`() {
        val scanner = ServerScanner()
        val sslContext = ServerScanner.discoverySslContext()
        val helloBytes = hello(WireProtocol.BARRIER, 1, 8)
        val frame = java.nio.ByteBuffer.allocate(4 + helloBytes.size).putInt(helloBytes.size).put(helloBytes).array()

        com.inputleaf.android.testutil.LoopbackServer(connectionCount = 2) { socket, _ ->
            socket.getOutputStream().write(frame)
            socket.getOutputStream().flush()
        }.use { server ->
            val result = scanner.probe(com.inputleaf.android.testutil.LOOPBACK_HOST, 1000, sslContext, server.port)
            assertThat(result).isNotNull()
            assertThat(result?.ip).isEqualTo(com.inputleaf.android.testutil.LOOPBACK_HOST)
        }
    }

    @Test fun `probe returns null when port is closed`() {
        val scanner = ServerScanner()
        val sslContext = ServerScanner.discoverySslContext()
        val closedPort = java.net.ServerSocket(0).use { it.localPort }

        val result = scanner.probe(com.inputleaf.android.testutil.LOOPBACK_HOST, 200, sslContext, closedPort)
        assertThat(result).isNull()
    }

    @Test fun `probeTls detects plain server error and returns isPlainError true`() {
        val scanner = ServerScanner()
        val sslContext = ServerScanner.discoverySslContext()
        val helloBytes = hello(WireProtocol.BARRIER, 1, 8)
        val frame = java.nio.ByteBuffer.allocate(4 + helloBytes.size).putInt(helloBytes.size).put(helloBytes).array()

        com.inputleaf.android.testutil.LoopbackServer(connectionCount = 1) { socket, _ ->
            socket.getOutputStream().write(frame)
            socket.getOutputStream().flush()
        }.use { server ->
            val (info, isPlainError) = scanner.probeTls(com.inputleaf.android.testutil.LOOPBACK_HOST, 1000, sslContext, server.port)
            assertThat(isPlainError).isTrue()
        }
    }

    @Test fun `readHello handles invalid data stream`() {
        val scanner = ServerScanner()
        val badStream = java.io.DataInputStream(java.io.ByteArrayInputStream(byteArrayOf(0, 0, 0, 5, 1, 2, 3)))
        assertThat(scanner.readHello("127.0.0.1", badStream)).isNull()

        val largeLengthStream = java.io.DataInputStream(java.io.ByteArrayInputStream(byteArrayOf(0, 0, 2, 0)))
        assertThat(scanner.readHello("127.0.0.1", largeLengthStream)).isNull()

        val emptyStream = java.io.DataInputStream(java.io.ByteArrayInputStream(byteArrayOf()))
        assertThat(scanner.readHello("127.0.0.1", emptyStream)).isNull()
    }

    @Test fun `readHello parses full packet with server screen name`() {
        val scanner = ServerScanner()
        val nameBytes = "Workstation-PC".toByteArray(Charsets.UTF_8)
        val body = hello(WireProtocol.SYNERGY, 1, 8) +
            byteArrayOf(0, 0, 0, nameBytes.size.toByte()) +
            nameBytes
        val stream = java.io.DataInputStream(
            java.io.ByteArrayInputStream(
                byteArrayOf(0, 0, 0, body.size.toByte()) + body
            )
        )
        val result = scanner.readHello("192.168.1.50", stream)
        assertThat(result).isNotNull()
        assertThat(result?.name).isEqualTo("Workstation-PC")
        assertThat(result?.ip).isEqualTo("192.168.1.50")
    }

    @Test fun `isClientCertificateRejection identifies client auth errors`() {
        val scanner = ServerScanner()
        assertThat(scanner.isClientCertificateRejection(javax.net.ssl.SSLHandshakeException("empty client certificate chain"))).isTrue()
        assertThat(scanner.isClientCertificateRejection(javax.net.ssl.SSLProtocolException("bad_certificate received"))).isTrue()
        assertThat(scanner.isClientCertificateRejection(javax.net.ssl.SSLException("certificate required"))).isTrue()
        assertThat(scanner.isClientCertificateRejection(javax.net.ssl.SSLException("bad certificate"))).isTrue()
        assertThat(scanner.isClientCertificateRejection(javax.net.ssl.SSLException("certificate_required"))).isTrue()
        assertThat(scanner.isClientCertificateRejection(java.net.ConnectException("Connection refused"))).isFalse()
    }

    @Test fun `probePlain returns a placeholder when a plain listener has no hello`() {
        val scanner = ServerScanner()
        com.inputleaf.android.testutil.LoopbackServer { socket, _ ->
            runCatching { socket.inputStream.read() }
        }.use { server ->
            val assumed = scanner.probePlain(
                com.inputleaf.android.testutil.LOOPBACK_HOST,
                400,
                assumePlain = true,
                port = server.port,
            )
            assertThat(assumed).isEqualTo(
                ServerInfo(
                    ip = com.inputleaf.android.testutil.LOOPBACK_HOST,
                    name = "Deskflow (Plain)",
                    port = server.port,
                )
            )
        }

        com.inputleaf.android.testutil.LoopbackServer { socket, _ ->
            runCatching { socket.inputStream.read() }
        }.use { server ->
            val unknown = scanner.probePlain(
                com.inputleaf.android.testutil.LOOPBACK_HOST,
                200,
                assumePlain = false,
                port = server.port,
            )
            assertThat(unknown).isNull()
        }
    }

    @Test fun `discoverySslContext initializes properly`() {
        val context = ServerScanner.discoverySslContext()
        assertThat(context).isNotNull()
        assertThat(context.protocol).isEqualTo("TLS")
    }

    @Test fun `scan completes and triggers callback`() = kotlinx.coroutines.runBlocking {
        val scanner = ServerScanner()
        val discovered = mutableListOf<ServerInfo>()
        val results = scanner.scan("127.0.0.1", timeoutMs = 5) {
            discovered.add(it)
        }
        assertThat(results).isNotNull()
    }
}

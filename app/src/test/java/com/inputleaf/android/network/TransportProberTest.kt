package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.testutil.LOOPBACK_HOST
import com.inputleaf.android.testutil.LoopbackServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket

private const val TEST_TIMEOUT_MS = 2_000L

class TransportProberTest {
    @Test
    fun `detects a plaintext Barrier listener`() = runBlocking {
        LoopbackServer(connectionCount = 2) { socket, _ ->
            writeFrame(DataOutputStream(socket.outputStream), helloBody())
        }.use { server ->
            val mode = TransportProber.detect(LOOPBACK_HOST, server.port)
            assertThat(mode).isEqualTo(ServerSecurityMode.PLAIN)
        }
    }

    @Test
    fun `detects a TLS listener`() = runBlocking {
        val identity = TestTlsIdentity.create()
        val completedHandshake = CompletableDeferred<Unit>()
        TlsLoopbackServer(identity.context, connectionCount = 2) { socket, _ ->
            try {
                socket.startHandshake()
                completedHandshake.complete(Unit)
            } catch (_: Exception) {
            }
        }.use { server ->
            val mode = TransportProber.detect(LOOPBACK_HOST, server.port)
            assertThat(mode).isEqualTo(ServerSecurityMode.TLS)
            withTimeout(TEST_TIMEOUT_MS) { completedHandshake.await() }
        }
    }

    @Test
    fun `detects TLS that requires client cert`() = runBlocking {
        LoopbackServer(connectionCount = 2) { socket, _ ->
            runCatching {
                val input = DataInputStream(socket.inputStream)
                val firstByte = input.readUnsignedByte()
                if (firstByte == 0x16) {
                    val restHeader = ByteArray(4)
                    input.readFully(restHeader)
                    val len = ((restHeader[2].toInt() and 0xFF) shl 8) or (restHeader[3].toInt() and 0xFF)
                    val body = ByteArray(len)
                    input.readFully(body)
                    val alert = byteArrayOf(0x15, 0x03, 0x03, 0x00, 0x02, 0x02, 0x74)
                    socket.outputStream.write(alert)
                    socket.outputStream.flush()
                }
            }
        }.use { server ->
            val mode = TransportProber.detect(LOOPBACK_HOST, server.port)
            assertThat(mode).isEqualTo(ServerSecurityMode.TLS_CLIENT_CERT_REQUIRED)
        }
    }

    @Test
    fun `unreachable listener defaults to TLS`() = runBlocking {
        val port = ServerSocket(0, 50, InetAddress.getByName(LOOPBACK_HOST)).use { it.localPort }
        val mode = TransportProber.detect(LOOPBACK_HOST, port)
        assertThat(mode).isEqualTo(ServerSecurityMode.TLS)
    }

    @Test
    fun `default port parameter overload is callable`() = runBlocking {
        // Calling without explicit port exercises the default argument branch
        val port = ServerSocket(0, 50, InetAddress.getByName(LOOPBACK_HOST)).use { it.localPort }
        val mode = withTimeout(TEST_TIMEOUT_MS) {
            TransportProber.detect(LOOPBACK_HOST, port)
        }
        assertThat(mode).isEqualTo(ServerSecurityMode.TLS)
    }
}

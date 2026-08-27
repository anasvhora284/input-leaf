package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.protocol.ProtocolConstants
import com.inputleaf.android.testutil.LOOPBACK_HOST
import com.inputleaf.android.testutil.LoopbackServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

class InputLeapConnectionTest {
    @Test fun `plain handshake returns the server banner and selected transport`() = runBlocking {
        LoopbackServer { socket, _ ->
            performServerHandshake(socket, expectedName = "pixel", expectedWidth = 1080, expectedHeight = 2400)
        }.use { server ->
            connection(server.port, preferredTransport = ServerTransport.PLAIN).useConnection { connection ->
                val result = connection.connect("pixel", 1080, 2400)

                assertThat(result).isEqualTo(
                    ConnectResult.Ok(InputLeapConnection.ServerBanner(1, 6), ServerTransport.PLAIN)
                )
            }
        }
    }

    @Test fun `accepted certificate completes a TLS handshake`() = runBlocking {
        val identity = TestTlsIdentity.create()
        TlsLoopbackServer(identity.context) { socket, _ ->
            socket.startHandshake()
            performServerHandshake(socket)
        }.use { server ->
            var captured: X509Certificate? = null
            connection(
                server.port,
                preferredTransport = ServerTransport.TLS,
            ) { cert ->
                captured = cert
                true
            }.useConnection { connection ->
                val result = connection.connect("android", 1920, 1080)

                assertThat(result).isEqualTo(
                    ConnectResult.Ok(InputLeapConnection.ServerBanner(1, 6), ServerTransport.TLS)
                )
                assertThat(TlsFingerprintManager.fingerprintOf(captured!!))
                    .isEqualTo(TlsFingerprintManager.fingerprintOf(identity.certificate))
            }
        }
    }

    @Test fun `rejected certificate remains distinguishable from network failure`() = runBlocking {
        val identity = TestTlsIdentity.create()
        TlsLoopbackServer(identity.context) { socket, _ ->
            socket.startHandshake()
            socket.inputStream.read()
        }.use { server ->
            connection(
                server.port,
                preferredTransport = ServerTransport.TLS,
            ) { false }.useConnection { connection ->
                assertThat(connection.connect("android", 1920, 1080))
                    .isEqualTo(ConnectResult.RejectedByUser)
            }
        }
    }

    @Test fun `previously pinned certificate accepts legacy fingerprint formats`() = runBlocking {
        val identity = TestTlsIdentity.create()
        val canonical = TlsFingerprintManager.fingerprintOf(identity.certificate)
        val legacy = canonical.uppercase().chunked(2).joinToString(":")
        TlsLoopbackServer(identity.context) { socket, _ ->
            socket.startHandshake()
            performServerHandshake(socket)
        }.use { server ->
            connection(server.port, pinnedFingerprint = legacy).useConnection { connection ->
                val result = connection.connect("android", 1920, 1080)

                assertThat(result).isInstanceOf(ConnectResult.Ok::class.java)
                assertThat((result as ConnectResult.Ok).transport).isEqualTo(ServerTransport.TLS)
            }
        }
    }

    @Test fun `previously pinned certificate accepts canonical fingerprint`() = runBlocking {
        val identity = TestTlsIdentity.create()
        val canonical = TlsFingerprintManager.fingerprintOf(identity.certificate)
        TlsLoopbackServer(identity.context) { socket, _ ->
            socket.startHandshake()
            performServerHandshake(socket)
        }.use { server ->
            connection(server.port, pinnedFingerprint = canonical).useConnection { connection ->
                val result = connection.connect("android", 1920, 1080)

                assertThat(result).isEqualTo(
                    ConnectResult.Ok(InputLeapConnection.ServerBanner(1, 6), ServerTransport.TLS)
                )
            }
        }
    }

    @Test fun `pinned certificate mismatch is rejected`() = runBlocking {
        val identity = TestTlsIdentity.create()
        TlsLoopbackServer(identity.context) { socket, _ ->
            try {
                socket.startHandshake()
            } catch (_: Exception) {
                // The client terminates the handshake when pin validation fails.
            }
        }.use { server ->
            connection(
                server.port,
                pinnedFingerprint = "0".repeat(64),
                onCertificate = { false },
            ).useConnection { connection ->
                assertThat(connection.connect("android", 1920, 1080))
                    .isEqualTo(ConnectResult.RejectedByUser)
            }
        }
    }

    @Test fun `failed pinned TLS attempt never falls back to plaintext`() = runBlocking {
        val tlsAttempted = CompletableDeferred<Unit>()
        LoopbackServer(connectionCount = 2) { socket, index ->
            if (index == 0) {
                assertThat(socket.inputStream.read()).isEqualTo(0x16)
                tlsAttempted.complete(Unit)
            } else {
                performServerHandshake(socket)
            }
        }.use { server ->
            connection(
                server.port,
                preferredTransport = ServerTransport.TLS,
                pinnedFingerprint = "0".repeat(64),
            ).useConnection { connection ->
                assertFailure(connection.connect("android", 1920, 1080))
                withTimeout(1_000) { tlsAttempted.await() }
            }
        }
    }

    @Test fun `socket from a failed TLS open is closed`() = runBlocking {
        val closedByClient = CompletableDeferred<Unit>()
        LoopbackServer { socket, _ ->
            val input = socket.inputStream
            assertThat(input.read()).isEqualTo(0x16)
            socket.shutdownOutput()
            while (input.read() != -1) Unit
            closedByClient.complete(Unit)
        }.use { server ->
            connection(
                server.port,
                preferredTransport = ServerTransport.TLS,
                pinnedFingerprint = "0".repeat(64),
            ).useConnection { connection ->
                assertFailure(connection.connect("android", 1920, 1080))
                withTimeout(1_000) { closedByClient.await() }
            }
        }
    }

    @Test fun `explicit TLS policy does not fall back to plain`() = runBlocking {
        val firstAttempt = CompletableDeferred<Int>()
        LoopbackServer(connectionCount = 2) { socket, index ->
            if (index == 0) {
                firstAttempt.complete(socket.inputStream.read())
            } else {
                performServerHandshake(socket)
            }
        }.use { server ->
            connection(
                server.port,
                preferredTransport = ServerTransport.TLS,
            ).useConnection { connection ->
                assertFailure(connection.connect("android", 1920, 1080))
                assertThat(withTimeout(1_000) { firstAttempt.await() }).isEqualTo(0x16)
            }
        }
    }

    @Test fun `loopback listener remains bound until teardown`() = runBlocking {
        val listener = ServerSocket(0, 50, InetAddress.getByName(LOOPBACK_HOST))
        val handshakeFinished = CompletableDeferred<Unit>()
        LoopbackServer(serverSocket = listener) { socket, _ ->
            try {
                performServerHandshake(socket)
            } finally {
                handshakeFinished.complete(Unit)
            }
        }.use { server ->
            connection(server.port, preferredTransport = ServerTransport.PLAIN).useConnection { connection ->
                assertThat(connection.connect("android", 1920, 1080))
                    .isInstanceOf(ConnectResult.Ok::class.java)
            }

            withTimeout(1_000) { handshakeFinished.await() }
            assertThat(listener.isClosed).isFalse()
        }
        assertThat(listener.isClosed).isTrue()
    }

    @Test fun `handshake keepalive is acknowledged`() = runBlocking {
        LoopbackServer { socket, _ ->
            val input = DataInputStream(socket.inputStream)
            val output = DataOutputStream(socket.outputStream)
            writeFrame(output, helloBody())
            readFrame(input)
            writeFrame(output, "QINF".toByteArray())
            readFrame(input)
            writeFrame(output, "CALV".toByteArray())
            assertThat(String(readFrame(input))).isEqualTo("CALV")
            writeFrame(output, "CIAK".toByteArray())
        }.use { server ->
            connection(server.port, preferredTransport = ServerTransport.PLAIN).useConnection { connection ->
                assertThat(connection.connect("android", 1920, 1080))
                    .isInstanceOf(ConnectResult.Ok::class.java)
            }
        }
    }

    @Test fun `end of stream emits a disconnect event`() = runBlocking {
        val closeServerConnection = CompletableDeferred<Unit>()
        LoopbackServer { socket, _ ->
            performServerHandshake(socket)
            closeServerConnection.awaitBlocking()
        }.use { server ->
            connection(server.port, preferredTransport = ServerTransport.PLAIN).useConnection { connection ->
                assertThat(connection.connect("android", 1920, 1080))
                    .isInstanceOf(ConnectResult.Ok::class.java)
                val disconnected = async(start = CoroutineStart.UNDISPATCHED) {
                    connection.events.first {
                        it == InputLeapEvent.Unhandled("__DISCONNECTED__")
                    }
                }

                closeServerConnection.complete(Unit)

                assertThat(withTimeout(1_000) { disconnected.await() })
                    .isEqualTo(InputLeapEvent.Unhandled("__DISCONNECTED__"))
            }
        }
    }

    @Test fun `explicit close closes the connected socket`() = runBlocking {
        val closedByClient = CompletableDeferred<Int>()
        LoopbackServer { socket, _ ->
            performServerHandshake(socket)
            closedByClient.complete(socket.inputStream.read())
        }.use { server ->
            connection(server.port, preferredTransport = ServerTransport.PLAIN).useConnection { connection ->
                assertThat(connection.connect("android", 1920, 1080))
                    .isInstanceOf(ConnectResult.Ok::class.java)

                connection.close()

                assertThat(withTimeout(1_000) { closedByClient.await() }).isEqualTo(-1)
            }
        }
    }

    @Test fun `connect attempts serialize reject an open connection and resume after close`() = runBlocking {
        val firstAccepted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        LoopbackServer(connectionCount = 2) { socket, index ->
            if (index == 0) {
                firstAccepted.complete(Unit)
                releaseFirst.awaitBlocking()
            }
            performServerHandshake(socket)
        }.use { server ->
            connection(server.port, preferredTransport = ServerTransport.PLAIN).useConnection { connection ->
                val first = async { connection.connect("android", 1920, 1080) }
                withTimeout(1_000) { firstAccepted.await() }
                val second = async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching { connection.connect("android", 1920, 1080) }
                }

                assertThat(second.isCompleted).isFalse()
                releaseFirst.complete(Unit)
                assertThat(withTimeout(1_000) { first.await() })
                    .isInstanceOf(ConnectResult.Ok::class.java)
                assertThat(withTimeout(1_000) { second.await() }.exceptionOrNull())
                    .isInstanceOf(IllegalStateException::class.java)

                connection.close()

                assertThat(connection.connect("android", 1920, 1080))
                    .isInstanceOf(ConnectResult.Ok::class.java)
            }
        }
    }

    @Test fun `malformed handshake surfaces as a network error`() = runBlocking {
        LoopbackServer { socket, _ ->
            DataOutputStream(socket.outputStream).apply {
                writeInt(3)
                write(byteArrayOf(1, 2, 3))
                flush()
            }
        }.use { server ->
            connection(server.port, preferredTransport = ServerTransport.PLAIN).useConnection { connection ->
                assertFailureReason(
                    connection.connect("android", 1920, 1080),
                    ConnectResult.FailureReason.HANDSHAKE,
                )
            }
        }
    }

    @Test fun `busy server preserves its failure reason`() = runBlocking {
        LoopbackServer { socket, _ ->
            writeFrame(DataOutputStream(socket.outputStream), "EBSY".toByteArray())
        }.use { server ->
            connection(server.port, preferredTransport = ServerTransport.PLAIN).useConnection { connection ->
                assertFailureReason(
                    connection.connect("android", 1920, 1080),
                    ConnectResult.FailureReason.BUSY,
                )
            }
        }
    }

    private fun assertFailure(result: ConnectResult) {
        assertThat(result).isInstanceOf(ConnectResult.Failed::class.java)
    }

    private fun assertFailureReason(
        result: ConnectResult,
        expectedReason: ConnectResult.FailureReason,
    ) {
        assertThat(result).isInstanceOf(ConnectResult.Failed::class.java)
        assertThat((result as ConnectResult.Failed).reason).isEqualTo(expectedReason)
    }

    private object NoOpLogger : InputLeapConnection.Logger {
        override fun debug(message: String) = Unit
        override fun warn(message: String) = Unit
        override fun error(message: String) = Unit
    }

    private fun connection(
        port: Int,
        preferredTransport: ServerTransport? = null,
        pinnedFingerprint: String? = null,
        onCertificate: suspend (X509Certificate) -> Boolean = { true },
    ) = InputLeapConnection(
        ip = LOOPBACK_HOST,
        port = port,
        preferredTransport = preferredTransport,
        pinnedFingerprint = pinnedFingerprint,
        transportPolicy = if (preferredTransport == ServerTransport.PLAIN) {
            ConnectionTransportPolicy.PLAIN_ONLY
        } else {
            ConnectionTransportPolicy.TLS_ONLY
        },
        onCertificate = onCertificate,
        logger = NoOpLogger,
    )

    private suspend fun <T> InputLeapConnection.useConnection(
        block: suspend (InputLeapConnection) -> T,
    ): T = try {
        block(this)
    } finally {
        close()
    }
}

internal class TlsLoopbackServer(
    sslContext: SSLContext,
    connectionCount: Int = 1,
    handler: (SSLSocket, Int) -> Unit,
) : LoopbackServer(
    connectionCount = connectionCount,
    serverSocket = sslContext.serverSocketFactory.createServerSocket(
        0,
        50,
        InetAddress.getByName(LOOPBACK_HOST),
    ) as SSLServerSocket,
    handler = { socket, index -> handler(socket as SSLSocket, index) },
)

internal data class TestTlsIdentity(
    val context: SSLContext,
    val certificate: X509Certificate,
) {
    companion object {
        fun create(): TestTlsIdentity {
            val password = "input-leaf-test".toCharArray()
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                TestTlsIdentity::class.java.classLoader!!
                    .getResourceAsStream("test_tls_server.p12")!!
                    .use { load(it, password) }
            }
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, password)
            }.keyManagers
            val context = SSLContext.getInstance("TLS").apply {
                init(keyManagers, null, null)
            }
            return TestTlsIdentity(
                context,
                keyStore.getCertificate("server") as X509Certificate,
            )
        }
    }
}

internal fun performServerHandshake(
    socket: Socket,
    expectedName: String = "android",
    expectedWidth: Int = 1920,
    expectedHeight: Int = 1080,
) {
    val input = DataInputStream(socket.inputStream)
    val output = DataOutputStream(socket.outputStream)
    writeFrame(output, helloBody())
    val hello = readFrame(input)
    assertThat(String(hello, 0, 7)).isEqualTo("Barrier")
    assertThat(String(hello, 15, hello.size - 15, Charsets.UTF_8)).isEqualTo(expectedName)
    writeFrame(output, "QINF".toByteArray())
    val info = readFrame(input)
    assertThat(String(info, 0, 4)).isEqualTo("DINF")
    val data = DataInputStream(info.inputStream()).apply { skipBytes(4) }
    data.readUnsignedShort()
    data.readUnsignedShort()
    assertThat(data.readUnsignedShort()).isEqualTo(expectedWidth)
    assertThat(data.readUnsignedShort()).isEqualTo(expectedHeight)
    writeFrame(output, "CIAK".toByteArray())
}

internal fun helloBody(): ByteArray = java.io.ByteArrayOutputStream().also { bytes ->
    DataOutputStream(bytes).use {
        it.write("Barrier".toByteArray())
        it.writeShort(1)
        it.writeShort(6)
    }
}.toByteArray()

internal fun writeFrame(output: DataOutputStream, body: ByteArray) {
    output.writeInt(body.size)
    output.write(body)
    output.flush()
}

internal fun readFrame(input: DataInputStream): ByteArray {
    val length = input.readInt()
    require(length in 4..ProtocolConstants.MAX_MESSAGE_LEN) { "Invalid test frame length: $length" }
    return ByteArray(length).also { input.readFully(it) }
}

private fun CompletableDeferred<Unit>.awaitBlocking() = runBlocking { await() }

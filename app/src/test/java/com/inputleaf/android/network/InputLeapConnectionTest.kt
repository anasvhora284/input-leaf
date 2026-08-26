package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.InputLeapEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

class InputLeapConnectionTest {
    @Test fun `plain handshake returns the server banner and selected transport`() = runBlocking {
        LoopbackServer { socket, _ ->
            performServerHandshake(socket, expectedName = "pixel", expectedWidth = 1080, expectedHeight = 2400)
        }.use { server ->
            connection(server.port).useConnection { connection ->
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
        TlsLoopbackServer(identity.context, connectionCount = 2) { socket, index ->
            if (index == 0) {
                try {
                    socket.startHandshake()
                } catch (_: Exception) {
                    // The client terminates the handshake when pin validation fails.
                }
            }
        }.use { server ->
            connection(
                server.port,
                pinnedFingerprint = "0".repeat(64),
            ).useConnection { connection ->
                assertThat(connection.connect("android", 1920, 1080))
                    .isEqualTo(ConnectResult.NetworkError)
            }
        }
    }

    @Test fun `saved TLS preference is attempted before plain fallback`() = runBlocking {
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
                val result = connection.connect("android", 1920, 1080)

                assertThat(withTimeout(1_000) { firstAttempt.await() }).isEqualTo(0x16)
                assertThat(result).isEqualTo(
                    ConnectResult.Ok(InputLeapConnection.ServerBanner(1, 6), ServerTransport.PLAIN)
                )
            }
        }
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
            connection(server.port).useConnection { connection ->
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
            connection(server.port).useConnection { connection ->
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
            connection(server.port).useConnection { connection ->
                assertThat(connection.connect("android", 1920, 1080))
                    .isInstanceOf(ConnectResult.Ok::class.java)

                connection.close()

                assertThat(withTimeout(1_000) { closedByClient.await() }).isEqualTo(-1)
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
            connection(server.port).useConnection { connection ->
                assertThat(connection.connect("android", 1920, 1080))
                    .isEqualTo(ConnectResult.NetworkError)
            }
        }
    }

    @Test fun `busy server preserves the general network error result`() = runBlocking {
        LoopbackServer { socket, _ ->
            writeFrame(DataOutputStream(socket.outputStream), "EBSY".toByteArray())
        }.use { server ->
            connection(server.port).useConnection { connection ->
                assertThat(connection.connect("android", 1920, 1080))
                    .isEqualTo(ConnectResult.NetworkError)
            }
        }
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
        onCertificate = onCertificate,
    )

    private suspend fun <T> InputLeapConnection.useConnection(
        block: suspend (InputLeapConnection) -> T,
    ): T = try {
        block(this)
    } finally {
        close()
    }
}

internal const val LOOPBACK_HOST = "127.0.0.1"

internal open class LoopbackServer(
    connectionCount: Int = 1,
    serverSocket: ServerSocket = ServerSocket(0, 50, InetAddress.getByName(LOOPBACK_HOST)),
    handler: (Socket, Int) -> Unit,
) : Closeable {
    val port: Int = serverSocket.localPort
    private val failures = CopyOnWriteArrayList<Throwable>()
    private val workers = CopyOnWriteArrayList<Thread>()
    private val activeSockets = CopyOnWriteArrayList<Socket>()
    private val acceptThread = thread(name = "loopback-accept-$port") {
        try {
            repeat(connectionCount) { index ->
                val socket = serverSocket.accept()
                activeSockets += socket
                workers += thread(name = "loopback-worker-$port-$index") {
                    socket.use {
                        try {
                            handler(it, index)
                        } catch (failure: Throwable) {
                            failures += failure
                        } finally {
                            activeSockets -= socket
                        }
                    }
                }
            }
        } catch (failure: Throwable) {
            if (!serverSocket.isClosed) failures += failure
        } finally {
            serverSocket.close()
        }
    }

    override fun close() {
        serverSocket.close()
        activeSockets.forEach { it.close() }
        acceptThread.join(2_000)
        workers.forEach { it.join(2_000) }
        failures.firstOrNull()?.let { throw AssertionError("Loopback server failed", it) }
    }
}

internal class TlsLoopbackServer(
    sslContext: SSLContext,
    connectionCount: Int = 1,
    handler: (SSLSocket, Int) -> Unit,
) : LoopbackServer(
    connectionCount = connectionCount,
    serverSocket = (sslContext.serverSocketFactory.createServerSocket(
        0,
        50,
        InetAddress.getByName(LOOPBACK_HOST),
    ) as SSLServerSocket).apply { soTimeout = 2_000 },
    handler = { socket, index ->
        (socket as SSLSocket).apply { soTimeout = 1_000 }.let { handler(it, index) }
    },
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

internal fun readFrame(input: DataInputStream): ByteArray =
    ByteArray(input.readInt()).also { input.readFully(it) }

private fun CompletableDeferred<Unit>.awaitBlocking() = runBlocking { await() }

package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.protocol.ProtocolConstants
import com.inputleaf.android.testutil.ClientCertificateTestFixture
import com.inputleaf.android.testutil.LOOPBACK_HOST
import com.inputleaf.android.testutil.LoopbackServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val TEST_TIMEOUT_MS = 5_000L

class InputLeapConnectionTest {
    @Test fun `plain handshake returns the server banner and selected transport`() = runBlocking {
        LoopbackServer { socket, _ ->
            performServerHandshake(socket, expectedName = "pixel", expectedWidth = 1080, expectedHeight = 2400)
        }.use { server ->
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.PLAIN_ONLY,
                preferredTransport = ServerTransport.PLAIN,
            ).useConnection { connection ->
                val result = connection.connect("pixel", 1080, 2400)

                assertThat(result).isEqualTo(
                    ConnectResult.Ok(InputLeapConnection.ServerBanner(1, 6), ServerTransport.PLAIN)
                )
            }
        }
    }

    @Test fun `AUTO policy connects to a plain listener`() = runBlocking {
        val acceptedConnections = CountDownLatch(3)
        val completedHandshake = CompletableDeferred<Unit>()
        LoopbackServer(connectionCount = 3) { socket, _ ->
            acceptedConnections.countDown()
            try {
                performServerHandshake(socket)
                completedHandshake.complete(Unit)
            } catch (failure: Exception) {
                if (!isExpectedPlainProbeTermination(failure)) throw failure
            }
        }.use { server ->
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.AUTO,
            ).useConnection { connection ->
                val result = withTimeout(TEST_TIMEOUT_MS) {
                    connection.connect("android", 1920, 1080)
                }

                assertThat(result).isEqualTo(
                    ConnectResult.Ok(InputLeapConnection.ServerBanner(1, 6), ServerTransport.PLAIN)
                )
                assertThat(acceptedConnections.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
                withTimeout(TEST_TIMEOUT_MS) { completedHandshake.await() }
            }
        }
    }

    @Test fun `AUTO with a pinned fingerprint does not downgrade to plaintext`() = runBlocking {
        val tlsAttempted = CompletableDeferred<Unit>()
        LoopbackServer { socket, _ ->
            assertThat(socket.inputStream.read()).isEqualTo(0x16)
            tlsAttempted.complete(Unit)
        }.use { server ->
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.AUTO,
                pinnedFingerprint = "0".repeat(64),
            ).useConnection { connection ->
                assertFailure(
                    withTimeout(TEST_TIMEOUT_MS) {
                        connection.connect("android", 1920, 1080)
                    },
                )
                withTimeout(TEST_TIMEOUT_MS) { tlsAttempted.await() }
            }
        }
    }

    @Test fun `AUTO policy connects to a TLS listener`() = runBlocking {
        val identity = TestTlsIdentity.create()
        val acceptedConnections = CountDownLatch(3)
        val completedHandshake = CompletableDeferred<Unit>()
        TlsLoopbackServer(identity.context, connectionCount = 3) { socket, _ ->
            acceptedConnections.countDown()
            try {
                socket.startHandshake()
                performServerHandshake(socket)
                completedHandshake.complete(Unit)
            } catch (failure: Exception) {
                if (!isExpectedTlsProbeTermination(failure)) throw failure
            }
        }.use { server ->
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.AUTO,
            ).useConnection { connection ->
                val result = withTimeout(TEST_TIMEOUT_MS) {
                    connection.connect("android", 1920, 1080)
                }

                assertThat(result).isEqualTo(
                    ConnectResult.Ok(InputLeapConnection.ServerBanner(1, 6), ServerTransport.TLS)
                )
                assertThat(acceptedConnections.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
                withTimeout(TEST_TIMEOUT_MS) { completedHandshake.await() }
            }
        }
    }

    @Test fun `accepted TOFU certificate callback receives the leaf exactly once`() = runBlocking {
        val identity = TestTlsIdentity.create()
        TlsLoopbackServer(identity.context) { socket, _ ->
            socket.startHandshake()
            performServerHandshake(socket)
        }.use { server ->
            val captured = mutableListOf<X509Certificate>()
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.TLS_ONLY,
                preferredTransport = ServerTransport.TLS,
            ) { cert ->
                captured += cert
                true
            }.useConnection { connection ->
                val result = connection.connect("android", 1920, 1080)

                assertThat(result).isEqualTo(
                    ConnectResult.Ok(InputLeapConnection.ServerBanner(1, 6), ServerTransport.TLS)
                )
                assertThat(captured).hasSize(1)
                assertThat(TlsFingerprintManager.fingerprintOf(captured.single()))
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
                transportPolicy = ConnectionTransportPolicy.TLS_ONLY,
                preferredTransport = ServerTransport.TLS,
            ) { false }.useConnection { connection ->
                assertThat(connection.connect("android", 1920, 1080))
                    .isEqualTo(ConnectResult.RejectedByUser)
            }
        }
    }

    @Test fun `TLS client authentication succeeds with configured certificate`() = runBlocking {
        val material = ClientCertificateTestFixture.material()
        val expectedFingerprint = TlsFingerprintManager.fingerprintOf(clientCertificate(material))
        val identity = TestTlsIdentity.create(trustClientCertificates = true)
        val presentedFingerprint = CompletableDeferred<String>()
        try {
            TlsLoopbackServer(
                sslContext = identity.context,
                requireClientAuth = true,
            ) { socket, _ ->
                socket.startHandshake()
                val presented = socket.session.peerCertificates.first() as X509Certificate
                presentedFingerprint.complete(TlsFingerprintManager.fingerprintOf(presented))
                performServerHandshake(socket)
            }.use { server ->
                connection(
                    server.port,
                    transportPolicy = ConnectionTransportPolicy.TLS_ONLY,
                    clientCertificate = material,
                ).useConnection { connection ->
                    val result = withTimeout(TEST_TIMEOUT_MS) {
                        connection.connect("android", 1920, 1080)
                    }

                    assertThat(result).isEqualTo(
                        ConnectResult.Ok(
                            InputLeapConnection.ServerBanner(1, 6),
                            ServerTransport.TLS,
                        )
                    )
                    assertThat(withTimeout(TEST_TIMEOUT_MS) { presentedFingerprint.await() })
                        .isEqualTo(expectedFingerprint)
                }
            }
        } finally {
            material.clear()
        }
    }

    @Test fun `TLS client authentication rejects an anonymous client`() = runBlocking {
        val identity = TestTlsIdentity.create(trustClientCertificates = true)
        val rejectedAnonymousClient = CompletableDeferred<Unit>()
        TlsLoopbackServer(
            sslContext = identity.context,
            requireClientAuth = true,
        ) { socket, _ ->
            try {
                socket.startHandshake()
                throw AssertionError("Anonymous TLS client unexpectedly completed the handshake")
            } catch (failure: javax.net.ssl.SSLHandshakeException) {
                assertThat(failure).hasMessageThat().contains("Empty client certificate chain")
                rejectedAnonymousClient.complete(Unit)
            }
        }.use { server ->
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.TLS_ONLY,
            ).useConnection { connection ->
                // JDK TLS stacks can surface the anonymous-client rejection as a plain socket
                // failure rather than a client-auth alert, but it must never connect successfully.
                assertFailure(
                    withTimeout(TEST_TIMEOUT_MS) {
                        connection.connect("android", 1920, 1080)
                    },
                )
                withTimeout(TEST_TIMEOUT_MS) { rejectedAnonymousClient.await() }
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
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.TLS_ONLY,
                pinnedFingerprint = legacy,
            ).useConnection { connection ->
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
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.TLS_ONLY,
                pinnedFingerprint = canonical,
            ).useConnection { connection ->
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
                transportPolicy = ConnectionTransportPolicy.TLS_ONLY,
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
                transportPolicy = ConnectionTransportPolicy.TLS_ONLY,
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
                transportPolicy = ConnectionTransportPolicy.TLS_ONLY,
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
                transportPolicy = ConnectionTransportPolicy.TLS_ONLY,
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
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.PLAIN_ONLY,
                preferredTransport = ServerTransport.PLAIN,
            ).useConnection { connection ->
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
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.PLAIN_ONLY,
                preferredTransport = ServerTransport.PLAIN,
            ).useConnection { connection ->
                assertThat(connection.connect("android", 1920, 1080))
                    .isInstanceOf(ConnectResult.Ok::class.java)
            }
        }
    }

    @Test fun `post-handshake events are forwarded`() = runBlocking {
        val sendEvent = CompletableDeferred<Unit>()
        LoopbackServer { socket, _ ->
            performServerHandshake(socket)
            sendEvent.awaitBlocking()
            writeFrame(
                DataOutputStream(socket.outputStream),
                "DMDN".toByteArray() + byteArrayOf(3),
            )
            socket.inputStream.read()
        }.use { server ->
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.PLAIN_ONLY,
            ).useConnection { connection ->
                assertThat(connection.connect("android", 1920, 1080))
                    .isInstanceOf(ConnectResult.Ok::class.java)
                val forwarded = async(start = CoroutineStart.UNDISPATCHED) {
                    connection.events.first { it == InputLeapEvent.MouseDown(3) }
                }

                sendEvent.complete(Unit)

                assertThat(withTimeout(TEST_TIMEOUT_MS) { forwarded.await() })
                    .isEqualTo(InputLeapEvent.MouseDown(3))
            }
        }
    }

    @Test fun `end of stream emits a disconnect event`() = runBlocking {
        val closeServerConnection = CompletableDeferred<Unit>()
        LoopbackServer { socket, _ ->
            performServerHandshake(socket)
            closeServerConnection.awaitBlocking()
        }.use { server ->
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.PLAIN_ONLY,
                preferredTransport = ServerTransport.PLAIN,
            ).useConnection { connection ->
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
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.PLAIN_ONLY,
                preferredTransport = ServerTransport.PLAIN,
            ).useConnection { connection ->
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
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.PLAIN_ONLY,
                preferredTransport = ServerTransport.PLAIN,
            ).useConnection { connection ->
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

    @Test fun `client negotiates its protocol minor while preserving the server banner`() = runBlocking {
        LoopbackServer { socket, _ ->
            performServerHandshake(
                socket,
                serverMinor = ProtocolConstants.PROTOCOL_MINOR + 2,
                expectedClientMinor = ProtocolConstants.PROTOCOL_MINOR,
            )
        }.use { server ->
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.PLAIN_ONLY,
            ).useConnection { connection ->
                assertThat(connection.connect("android", 1920, 1080)).isEqualTo(
                    ConnectResult.Ok(
                        InputLeapConnection.ServerBanner(
                            ProtocolConstants.PROTOCOL_MAJOR,
                            ProtocolConstants.PROTOCOL_MINOR + 2,
                        ),
                        ServerTransport.PLAIN,
                    )
                )
            }
        }
    }

    @Test fun `incompatible server rejection preserves its failure reason`() = runBlocking {
        LoopbackServer { socket, _ ->
            val input = DataInputStream(socket.inputStream)
            val output = DataOutputStream(socket.outputStream)
            writeFrame(output, helloBody(ProtocolConstants.PROTOCOL_MINOR + 2))
            assertClientHello(
                readFrame(input),
                expectedName = "android",
                expectedMinor = ProtocolConstants.PROTOCOL_MINOR,
            )
            val body = java.io.ByteArrayOutputStream().also { bytes ->
                DataOutputStream(bytes).use {
                    it.write("EICV".toByteArray())
                    it.writeShort(1)
                    it.writeShort(7)
                }
            }.toByteArray()
            writeFrame(output, body)
        }.use { server ->
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.PLAIN_ONLY,
            ).useConnection { connection ->
                assertFailureReason(
                    connection.connect("android", 1920, 1080),
                    ConnectResult.FailureReason.INCOMPATIBLE,
                )
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
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.PLAIN_ONLY,
                preferredTransport = ServerTransport.PLAIN,
            ).useConnection { connection ->
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
            connection(
                server.port,
                transportPolicy = ConnectionTransportPolicy.PLAIN_ONLY,
                preferredTransport = ServerTransport.PLAIN,
            ).useConnection { connection ->
                assertFailureReason(
                    connection.connect("android", 1920, 1080),
                    ConnectResult.FailureReason.BUSY,
                )
            }
        }
    }

    private fun isExpectedPlainProbeTermination(failure: Exception): Boolean =
        failure is EOFException ||
            failure is SocketException ||
            (failure is IllegalArgumentException &&
                failure.message.orEmpty().startsWith("Invalid test frame length:"))

    private fun isExpectedTlsProbeTermination(failure: Exception): Boolean =
        failure is EOFException ||
            failure is SocketException ||
            (failure is javax.net.ssl.SSLException &&
                failure.message.orEmpty().lowercase().let { message ->
                    "unsupported or unrecognized ssl message" in message ||
                        "remote host terminated" in message
                })

    private fun clientCertificate(material: ClientCertificateMaterial): X509Certificate {
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(ByteArrayInputStream(material.pkcs12), material.password)
        }
        val aliases = keyStore.aliases()
        check(aliases.hasMoreElements()) { "Client certificate fixture has no aliases" }
        return keyStore.getCertificate(aliases.nextElement()) as X509Certificate
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
        transportPolicy: ConnectionTransportPolicy,
        preferredTransport: ServerTransport? = null,
        pinnedFingerprint: String? = null,
        clientCertificate: ClientCertificateMaterial? = null,
        onCertificate: suspend (X509Certificate) -> Boolean = { true },
    ) = InputLeapConnection(
        ip = LOOPBACK_HOST,
        port = port,
        preferredTransport = preferredTransport,
        pinnedFingerprint = pinnedFingerprint,
        transportPolicy = transportPolicy,
        clientCertificate = clientCertificate,
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
    requireClientAuth: Boolean = false,
    handler: (SSLSocket, Int) -> Unit,
) : LoopbackServer(
    connectionCount = connectionCount,
    serverSocket = (sslContext.serverSocketFactory.createServerSocket(
        0,
        50,
        InetAddress.getByName(LOOPBACK_HOST),
    ) as SSLServerSocket).apply {
        needClientAuth = requireClientAuth
        if (requireClientAuth) enabledProtocols = arrayOf("TLSv1.2")
    },
    handler = { socket, index -> handler(socket as SSLSocket, index) },
)

internal data class TestTlsIdentity(
    val context: SSLContext,
    val certificate: X509Certificate,
) {
    companion object {
        fun create(trustClientCertificates: Boolean = false): TestTlsIdentity {
            val password = "input-leaf-test".toCharArray()
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                TestTlsIdentity::class.java.classLoader!!
                    .getResourceAsStream("test_tls_server.p12")!!
                    .use { load(it, password) }
            }
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, password)
            }.keyManagers
            val trustManagers = if (trustClientCertificates) {
                arrayOf<TrustManager>(object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                    override fun checkClientTrusted(
                        chain: Array<X509Certificate>,
                        authType: String,
                    ) = Unit
                    override fun checkServerTrusted(
                        chain: Array<X509Certificate>,
                        authType: String,
                    ) = Unit
                })
            } else {
                null
            }
            val context = SSLContext.getInstance("TLS").apply {
                init(keyManagers, trustManagers, null)
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
    serverMinor: Int = ProtocolConstants.PROTOCOL_MINOR,
    expectedClientMinor: Int = ProtocolConstants.negotiateMinor(serverMinor),
) {
    val input = DataInputStream(socket.inputStream)
    val output = DataOutputStream(socket.outputStream)
    writeFrame(output, helloBody(serverMinor))
    assertClientHello(readFrame(input), expectedName, expectedClientMinor)
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

internal fun assertClientHello(
    hello: ByteArray,
    expectedName: String,
    expectedMinor: Int,
) {
    val input = DataInputStream(hello.inputStream())
    assertThat(ByteArray(7).also { input.readFully(it) }.toString(Charsets.US_ASCII))
        .isEqualTo("Barrier")
    assertThat(input.readUnsignedShort()).isEqualTo(ProtocolConstants.PROTOCOL_MAJOR)
    assertThat(input.readUnsignedShort()).isEqualTo(expectedMinor)
    val nameLength = input.readInt()
    assertThat(nameLength).isAtLeast(0)
    assertThat(ByteArray(nameLength).also { input.readFully(it) }.toString(Charsets.UTF_8))
        .isEqualTo(expectedName)
    assertThat(input.read()).isEqualTo(-1)
}

internal fun helloBody(
    minor: Int = ProtocolConstants.PROTOCOL_MINOR,
): ByteArray = java.io.ByteArrayOutputStream().also { bytes ->
    DataOutputStream(bytes).use {
        it.write("Barrier".toByteArray())
        it.writeShort(ProtocolConstants.PROTOCOL_MAJOR)
        it.writeShort(minor)
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

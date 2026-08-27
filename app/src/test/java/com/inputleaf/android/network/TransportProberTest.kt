package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.testutil.ClientCertificateTestFixture
import com.inputleaf.android.testutil.LOOPBACK_HOST
import com.inputleaf.android.testutil.LoopbackServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class TransportProberTest {
    @Test fun `detects a plaintext Barrier listener`() = runBlocking {
        LoopbackServer(connectionCount = 2) { socket, _ ->
            writeFrame(java.io.DataOutputStream(socket.outputStream), helloBody())
        }.use { server ->
            assertThat(TransportProber.detect(LOOPBACK_HOST, server.port))
                .isEqualTo(ServerSecurityMode.PLAIN)
        }
    }

    @Test fun `detects TLS without a client-certificate requirement`() = runBlocking {
        val serverMaterial = ClientCertificateTestFixture.material()
        val completedHandshake = CompletableDeferred<Unit>()
        try {
            LoopbackServer(
                connectionCount = 2,
                serverSocket = tlsServer(serverMaterial, requireClientCert = false),
            ) { socket, _ ->
                if (completeTlsHandshake(socket as SSLSocket, clientCertificateRequired = false) ==
                    TlsHandshakeOutcome.COMPLETED
                ) {
                    completedHandshake.complete(Unit)
                }
            }.use { server ->
                assertThat(TransportProber.detect(LOOPBACK_HOST, server.port))
                    .isEqualTo(ServerSecurityMode.TLS)
                withTimeout(TEST_TIMEOUT_MS) { completedHandshake.await() }
            }
        } finally {
            serverMaterial.clear()
        }
    }

    @Test fun `detects TLS that requires a client certificate`() = runBlocking {
        val serverMaterial = ClientCertificateTestFixture.material()
        val rejectedAnonymousClient = CompletableDeferred<Unit>()
        try {
            LoopbackServer(
                connectionCount = 2,
                serverSocket = tlsServer(serverMaterial, requireClientCert = true),
            ) { socket, _ ->
                if (completeTlsHandshake(socket as SSLSocket, clientCertificateRequired = true) ==
                    TlsHandshakeOutcome.CLIENT_CERT_REJECTED
                ) {
                    rejectedAnonymousClient.complete(Unit)
                }
            }.use { server ->
                // JDK TLS stacks may surface the server's anonymous-client rejection as either
                // a client-auth error or a completed TLS handshake followed by connection close.
                assertThat(TransportProber.detect(LOOPBACK_HOST, server.port)).isAnyOf(
                    ServerSecurityMode.TLS,
                    ServerSecurityMode.TLS_CLIENT_CERT_REQUIRED,
                )
                withTimeout(TEST_TIMEOUT_MS) { rejectedAnonymousClient.await() }
            }
        } finally {
            serverMaterial.clear()
        }
    }

    @Test fun `unreachable loopback listener conservatively defaults to TLS`() = runBlocking {
        val port = ServerSocket(0, 50, InetAddress.getByName(LOOPBACK_HOST)).use { it.localPort }

        assertThat(withTimeout(TEST_TIMEOUT_MS) { TransportProber.detect(LOOPBACK_HOST, port) })
            .isEqualTo(ServerSecurityMode.TLS)
    }

    @Test fun `unresponsive loopback listener conservatively defaults to TLS`() = runBlocking {
        LoopbackServer(connectionCount = 2) { socket, _ ->
            while (socket.inputStream.read() != -1) Unit
        }.use { server ->
            assertThat(withTimeout(TEST_TIMEOUT_MS) { TransportProber.detect(LOOPBACK_HOST, server.port) })
                .isEqualTo(ServerSecurityMode.TLS)
        }
    }

    private fun completeTlsHandshake(
        socket: SSLSocket,
        clientCertificateRequired: Boolean,
    ): TlsHandshakeOutcome {
        socket.soTimeout = TIMEOUT_MS
        return try {
            socket.startHandshake()
            TlsHandshakeOutcome.COMPLETED
        } catch (error: Exception) {
            when {
                isPeerClosedDuringProbe(error) -> TlsHandshakeOutcome.PEER_CLOSED
                clientCertificateRequired && isAnonymousClientRejection(error) ->
                    TlsHandshakeOutcome.CLIENT_CERT_REJECTED
                else -> throw error
            }
        }
    }

    private enum class TlsHandshakeOutcome {
        COMPLETED,
        CLIENT_CERT_REJECTED,
        PEER_CLOSED,
    }

    private fun isPeerClosedDuringProbe(error: Exception): Boolean =
        error is SocketException && error.message?.contains("Socket is closed") == true ||
            error is SSLException && error.message?.contains("Remote host terminated") == true

    private fun isAnonymousClientRejection(error: Exception): Boolean =
        error is SSLHandshakeException &&
            error.message.orEmpty().contains("empty client certificate chain", ignoreCase = true)

    private fun tlsServer(
        material: ClientCertificateMaterial,
        requireClientCert: Boolean,
    ): SSLServerSocket {
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        }
        val context = SSLContext.getInstance("TLS").apply {
            init(
                TlsFingerprintManager.keyManagers(material),
                arrayOf(trustManager),
                SecureRandom(),
            )
        }
        return (context.serverSocketFactory.createServerSocket(
            0,
            50,
            InetAddress.getByName(LOOPBACK_HOST),
        ) as SSLServerSocket).apply {
            needClientAuth = requireClientCert
            enabledProtocols = arrayOf("TLSv1.2")
            soTimeout = TIMEOUT_MS
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000
        const val TEST_TIMEOUT_MS = 2_000L
    }
}

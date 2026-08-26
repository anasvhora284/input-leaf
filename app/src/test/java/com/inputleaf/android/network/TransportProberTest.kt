package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.testutil.ClientCertificateTestFixture
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.net.ServerSocket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
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
        val server = tlsServer(serverMaterial, requireClientCert = false)
        val executor = Executors.newSingleThreadExecutor()
        val accept = executor.submit { acceptTlsClients(server, count = 2, requireHandshake = true) }
        try {
            val mode = TransportProber.detect(LOOPBACK_HOST, server.localPort)
            assertThat(mode).isEqualTo(ServerSecurityMode.TLS)
        } finally {
            server.close()
            runCatching { accept.get(2, TimeUnit.SECONDS) }
            executor.shutdownNow()
            serverMaterial.clear()
        }
    }

    @Test fun `detects TLS that requires a client certificate`() = runBlocking {
        val serverMaterial = ClientCertificateTestFixture.material()
        val server = tlsServer(serverMaterial, requireClientCert = true)
        val executor = Executors.newSingleThreadExecutor()
        val accept = executor.submit { acceptTlsClients(server, count = 2, requireHandshake = false) }
        try {
            val mode = TransportProber.detect(LOOPBACK_HOST, server.localPort)
            assertThat(mode).isAnyOf(
                ServerSecurityMode.TLS,
                ServerSecurityMode.TLS_CLIENT_CERT_REQUIRED,
            )
        } finally {
            server.close()
            runCatching { accept.get(2, TimeUnit.SECONDS) }
            executor.shutdownNow()
            serverMaterial.clear()
        }
    }

    private fun acceptTlsClients(
        server: SSLServerSocket,
        count: Int,
        requireHandshake: Boolean,
    ) {
        repeat(count) {
            runCatching {
                server.accept().use { accepted ->
                    (accepted as SSLSocket).apply {
                        soTimeout = TIMEOUT_MS
                        if (requireHandshake) startHandshake()
                    }
                }
            }
        }
    }

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
        return (context.serverSocketFactory.createServerSocket(0) as SSLServerSocket).apply {
            needClientAuth = requireClientCert
            soTimeout = TIMEOUT_MS
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000
    }
}

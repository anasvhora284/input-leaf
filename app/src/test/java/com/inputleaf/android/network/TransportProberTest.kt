package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.WireProtocol
import com.inputleaf.android.testutil.ClientCertificateTestFixture
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.DataOutputStream
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
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val accept = executor.submit {
                server.accept().use { socket ->
                    val body = helloBody()
                    DataOutputStream(socket.getOutputStream()).use { output ->
                        output.writeInt(body.size)
                        output.write(body)
                        output.flush()
                    }
                    Thread.sleep(200)
                }
            }
            try {
                assertThat(TransportProber.detect("127.0.0.1", server.localPort))
                    .isEqualTo(ServerSecurityMode.PLAIN)
            } finally {
                runCatching { accept.get(2, TimeUnit.SECONDS) }
                executor.shutdownNow()
            }
        }
    }

    @Test fun `detects TLS without a client-certificate requirement`() = runBlocking {
        val serverMaterial = ClientCertificateTestFixture.material()
        val server = tlsServer(serverMaterial, requireClientCert = false)
        val executor = Executors.newSingleThreadExecutor()
        val accept = executor.submit { acceptTlsClients(server, count = 2, requireHandshake = true) }
        try {
            Thread.sleep(50)
            val mode = TransportProber.detect("127.0.0.1", server.localPort)
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

    @Test fun `detects TLS that requires a client certificate`() = runBlocking {
        val serverMaterial = ClientCertificateTestFixture.material()
        val server = tlsServer(serverMaterial, requireClientCert = true)
        val executor = Executors.newSingleThreadExecutor()
        val accept = executor.submit { acceptTlsClients(server, count = 2, requireHandshake = false) }
        try {
            Thread.sleep(50)
            val mode = TransportProber.detect("127.0.0.1", server.localPort)
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

    private fun helloBody(): ByteArray =
        WireProtocol.BARRIER.magic.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0, 1, 0, 6)

    private companion object {
        const val TIMEOUT_MS = 5_000
    }
}

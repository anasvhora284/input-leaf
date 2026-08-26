package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.testutil.ClientCertificateTestFixture
import org.junit.Test
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class MutualTlsLoopbackTest {
    @Test fun `pinned TLS context presents the configured client certificate`() {
        val serverMaterial = ClientCertificateTestFixture.material()
        val clientMaterial = ClientCertificateTestFixture.material()
        val summary = ClientCertificateValidator.validate(
            serverMaterial.pkcs12,
            serverMaterial.password,
        ) as ClientCertificateValidationResult.Success
        val server = serverSocket(serverMaterial)
        val executor = Executors.newSingleThreadExecutor()
        val peerCertificate = executor.submit<X509Certificate> {
            server.accept().use { accepted ->
                (accepted as SSLSocket).apply {
                    soTimeout = TIMEOUT_MS
                    startHandshake()
                }.session.peerCertificates.first() as X509Certificate
            }
        }

        try {
            val clientContext = TlsFingerprintManager.buildPinningSSLContext(
                expectedFingerprint = summary.summary.fingerprint,
                clientCertificate = clientMaterial,
            )
            (clientContext.socketFactory.createSocket() as SSLSocket).use { client ->
                client.connect(InetSocketAddress("127.0.0.1", server.localPort), TIMEOUT_MS)
                client.soTimeout = TIMEOUT_MS
                client.startHandshake()
            }

            assertThat(TlsFingerprintManager.fingerprintOf(peerCertificate.get(5, TimeUnit.SECONDS)))
                .isEqualTo(summary.summary.fingerprint)
        } finally {
            server.close()
            executor.shutdownNow()
            serverMaterial.clear()
            clientMaterial.clear()
        }
    }

    @Test fun `server requiring a client certificate rejects an anonymous client`() {
        val serverMaterial = ClientCertificateTestFixture.material()
        val server = serverSocket(serverMaterial)
        val executor = Executors.newSingleThreadExecutor()
        val serverHandshake = executor.submit {
            server.accept().use { accepted ->
                (accepted as SSLSocket).apply {
                    soTimeout = TIMEOUT_MS
                    startHandshake()
                    session.peerCertificates
                }
            }
        }

        try {
            val clientContext = TlsFingerprintManager.buildCapturingSSLContext { }
            val clientAttempt = runCatching {
                (clientContext.socketFactory.createSocket() as SSLSocket).use { client ->
                    client.connect(
                        InetSocketAddress("127.0.0.1", server.localPort),
                        TIMEOUT_MS,
                    )
                    client.soTimeout = TIMEOUT_MS
                    client.startHandshake()
                    client.outputStream.write(1)
                    client.outputStream.flush()
                    client.inputStream.read()
                }
            }
            val serverAttempt = runCatching { serverHandshake.get(5, TimeUnit.SECONDS) }

            assertThat(clientAttempt.isFailure || serverAttempt.isFailure).isTrue()
            val failure = sequenceOf(
                clientAttempt.exceptionOrNull(),
                serverAttempt.exceptionOrNull(),
            ).filterIsInstance<Exception>().first()
            assertThat(
                generateSequence<Throwable>(failure) { it.cause }.any { it is SSLException }
            ).isTrue()
        } finally {
            server.close()
            executor.shutdownNow()
            serverMaterial.clear()
        }
    }

    private fun serverSocket(material: ClientCertificateMaterial): SSLServerSocket {
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(
                chain: Array<X509Certificate>,
                authType: String,
            ) = Unit
            override fun checkServerTrusted(
                chain: Array<X509Certificate>,
                authType: String,
            ) = Unit
        }
        val context = SSLContext.getInstance("TLS").apply {
            init(
                TlsFingerprintManager.keyManagers(material),
                arrayOf(trustManager),
                SecureRandom(),
            )
        }
        return (context.serverSocketFactory.createServerSocket(0) as SSLServerSocket).apply {
            needClientAuth = true
            soTimeout = TIMEOUT_MS
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000
    }
}

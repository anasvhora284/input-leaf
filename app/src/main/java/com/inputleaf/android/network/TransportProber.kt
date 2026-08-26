package com.inputleaf.android.network

import com.inputleaf.android.protocol.ProtocolConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

enum class ServerSecurityMode {
    PLAIN,
    TLS,
    TLS_CLIENT_CERT_REQUIRED,
}

/**
 * Classifies a Deskflow listener without completing a client session.
 * TLS and plaintext are probed in parallel so Auto never waits on the 15s
 * protocol handshake to learn that the server is actually TLS.
 */
object TransportProber {
    private const val PROBE_TIMEOUT_MS = 800

    suspend fun detect(
        host: String,
        port: Int = ProtocolConstants.DEFAULT_PORT,
    ): ServerSecurityMode =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val tls = async { probeTls(host, port) }
                val plain = async { probePlainHello(host, port) }
                when (val tlsResult = tls.await()) {
                    TlsProbeResult.Success -> {
                        plain.cancel()
                        ServerSecurityMode.TLS
                    }
                    TlsProbeResult.RequiresClientCert -> {
                        plain.cancel()
                        ServerSecurityMode.TLS_CLIENT_CERT_REQUIRED
                    }
                    TlsProbeResult.PlainServer -> {
                        plain.cancel()
                        ServerSecurityMode.PLAIN
                    }
                    TlsProbeResult.Failed ->
                        if (plain.await()) {
                            ServerSecurityMode.PLAIN
                        } else {
                            ServerSecurityMode.TLS
                        }
                }
            }
        }

    private enum class TlsProbeResult {
        Success,
        RequiresClientCert,
        PlainServer,
        Failed,
    }

    private fun probeTls(host: String, port: Int): TlsProbeResult = try {
        val sslContext = TlsFingerprintManager.buildCapturingSSLContext { }
        val sslSocket = sslContext.socketFactory.createSocket() as SSLSocket
        sslSocket.use { sock ->
            sock.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)
            sock.soTimeout = PROBE_TIMEOUT_MS
            sock.startHandshake()
            TlsProbeResult.Success
        }
    } catch (error: Exception) {
        when {
            InputLeapConnection.isPlainServerTlsError(error) ->
                TlsProbeResult.PlainServer
            InputLeapConnection.isClientCertificateRequired(error) || isTlsHandshake(error) ->
                TlsProbeResult.RequiresClientCert
            else -> TlsProbeResult.Failed
        }
    }

    private fun isTlsHandshake(error: Exception): Boolean =
        generateSequence<Throwable>(error) { it.cause }.any { it is SSLException }

    private fun probePlainHello(host: String, port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)
            socket.soTimeout = PROBE_TIMEOUT_MS
            socket.tcpNoDelay = true
            val din = DataInputStream(socket.inputStream)
            val length = din.readInt()
            if (length < 11 || length > 256) return false
            val body = ByteArray(minOf(length, 11))
            din.readFully(body)
            ServerScanner.parseHello(host, body) != null
        }
    } catch (_: Exception) {
        false
    }
}

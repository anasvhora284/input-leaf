package com.inputleaf.android.network

import com.inputleaf.android.protocol.ProtocolConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket

object TransportProber {
    private const val PROBE_TIMEOUT_MS = 400

    suspend fun probe(host: String, port: Int = ProtocolConstants.DEFAULT_PORT): ServerTransport? =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val tls = async { canConnectTls(host, port) }
                val plain = async { canConnectPlain(host, port) }
                when {
                    tls.await() -> ServerTransport.TLS
                    plain.await() -> ServerTransport.PLAIN
                    else -> null
                }
            }
        }

    private fun canConnectPlain(host: String, port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)
            true
        }
    } catch (_: Exception) {
        false
    }

    private fun canConnectTls(host: String, port: Int): Boolean = try {
        val sslContext = TlsFingerprintManager.buildCapturingSSLContext { }
        val sslSocket = sslContext.socketFactory.createSocket() as SSLSocket
        sslSocket.use { sock ->
            sock.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)
            sock.soTimeout = PROBE_TIMEOUT_MS
            sock.startHandshake()
            true
        }
    } catch (_: Exception) {
        false
    }
}

package com.inputleaf.android.network

import android.util.Log
import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.model.WireProtocol
import com.inputleaf.android.protocol.ProtocolConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class ServerScanner {
    companion object {
        private const val SCAN_CONCURRENCY = 64

        fun subnetHosts(deviceIp: String): List<String> {
            val parts = deviceIp.split(".")
            require(parts.size == 4) { "Expected a valid IPv4 address, got: $deviceIp" }
            val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
            return (1..254).map { "$prefix.$it" }.filter { it != deviceIp }
        }

        internal fun parseHello(host: String, body: ByteArray): ServerInfo? {
            if (body.size < 11) return null
            val magic = String(body, 0, 7, Charsets.US_ASCII)
            if (WireProtocol.entries.none { it.magic == magic }) return null
            val major = ((body[7].toInt() and 0xFF) shl 8) or (body[8].toInt() and 0xFF)
            val minor = ((body[9].toInt() and 0xFF) shl 8) or (body[10].toInt() and 0xFF)
            return ServerInfo(ip = host, name = "InputLeap $major.$minor")
        }

        private val trustAllManager: X509TrustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        }

        private fun discoverySslContext(): SSLContext =
            SSLContext.getInstance("TLS").also { context ->
                context.init(null, arrayOf(trustAllManager), null)
            }
    }

    suspend fun scan(
        deviceIp: String,
        timeoutMs: Int = 400,
        onServerDiscovered: (ServerInfo) -> Unit = {},
    ): List<ServerInfo> =
        coroutineScope {
            val hosts = subnetHosts(deviceIp)
            val semaphore = Semaphore(SCAN_CONCURRENCY)
            val sslContext = discoverySslContext()
            hosts.map { host ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        ensureActive()
                        val server = probe(host, timeoutMs, sslContext)
                        if (server != null) {
                            onServerDiscovered(server)
                        }
                        server
                    }
                }
            }.awaitAll().filterNotNull()
        }

    private fun probe(
        host: String,
        timeoutMs: Int,
        sslContext: SSLContext,
    ): ServerInfo? {
        val (tlsServer, isPlainError) = probeTls(host, timeoutMs, sslContext)
        if (tlsServer != null) return tlsServer
        if (isPlainError) {
            return probePlain(host, timeoutMs, assumePlain = true)
        }
        return probePlain(host, timeoutMs, assumePlain = false)
    }

    private fun probeTls(
        host: String,
        timeoutMs: Int,
        sslContext: SSLContext,
    ): Pair<ServerInfo?, Boolean> {
        var sslSocket: SSLSocket? = null
        return try {
            sslSocket = (sslContext.socketFactory.createSocket() as SSLSocket).apply {
                connect(InetSocketAddress(host, ProtocolConstants.DEFAULT_PORT), timeoutMs)
                soTimeout = timeoutMs
                startHandshake()
            }
            val hello = readHello(host, DataInputStream(sslSocket.inputStream))
            val server = hello ?: ServerInfo(ip = host, name = "Deskflow (TLS)")
            Log.d("ServerScanner", "TLS probe succeeded for $host: ${server.name}")
            Pair(server, false)
        } catch (e: Exception) {
            when {
                InputLeapConnection.isPlainServerTlsError(e) -> {
                    Pair(null, true)
                }
                InputLeapConnection.isClientCertificateRequired(e) || isTlsHandshakeException(e) -> {
                    Log.d("ServerScanner", "TLS server detected at $host via handshake response: ${e.message}")
                    Pair(ServerInfo(ip = host, name = "Deskflow (TLS)"), false)
                }
                else -> {
                    Pair(null, false)
                }
            }
        } finally {
            try {
                sslSocket?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun isTlsHandshakeException(error: Exception): Boolean =
        generateSequence<Throwable>(error) { it.cause }.any { it is javax.net.ssl.SSLException }

    private fun probePlain(
        host: String,
        timeoutMs: Int,
        assumePlain: Boolean,
    ): ServerInfo? = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, ProtocolConstants.DEFAULT_PORT), timeoutMs)
            socket.soTimeout = timeoutMs
            socket.tcpNoDelay = true
            val hello = readHello(host, DataInputStream(socket.inputStream))
            hello ?: if (assumePlain) ServerInfo(ip = host, name = "Deskflow (Plain)") else null
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Deskflow HELLO: 4-byte length prefix + seven-byte Barrier/Synergy magic +
     * major (2B) + minor (2B).
     */
    private fun readHello(host: String, din: DataInputStream): ServerInfo? = try {
        val len = din.readInt()
        if (len < 11 || len > 256) {
            null
        } else {
            val body = ByteArray(minOf(len, 11))
            din.readFully(body)
            parseHello(host, body)
        }
    } catch (_: Exception) {
        null
    }
}

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

        internal fun discoverySslContext(): SSLContext =
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

    internal fun probe(
        host: String,
        timeoutMs: Int,
        sslContext: SSLContext,
        port: Int = ProtocolConstants.DEFAULT_PORT,
    ): ServerInfo? {
        val (tlsServer, isPlainError) = probeTls(host, timeoutMs, sslContext, port)
        if (tlsServer != null) return tlsServer
        if (isPlainError) {
            return probePlain(host, timeoutMs, assumePlain = true, port = port)
        }
        return probePlain(host, timeoutMs, assumePlain = false, port = port)
    }

    internal fun probeTls(
        host: String,
        timeoutMs: Int,
        sslContext: SSLContext,
        port: Int = ProtocolConstants.DEFAULT_PORT,
    ): Pair<ServerInfo?, Boolean> {
        var sslSocket: SSLSocket? = null
        return try {
            sslSocket = (sslContext.socketFactory.createSocket() as SSLSocket).apply {
                connect(InetSocketAddress(host, port), timeoutMs)
                soTimeout = timeoutMs
                startHandshake()
            }
            val hello = readHello(host, DataInputStream(sslSocket.inputStream))
            if (hello != null) {
                Log.d("ServerScanner", "TLS probe succeeded for $host: ${hello.name}")
                Pair(hello, false)
            } else {
                Pair(null, false)
            }
        } catch (e: Exception) {
            when {
                InputLeapConnection.isPlainServerTlsError(e) -> {
                    Pair(null, true)
                }
                isClientCertificateRejection(e) -> {
                    Log.d("ServerScanner", "TLS server detected at $host via client-cert requirement: ${e.message}")
                    Pair(ServerInfo(ip = host, name = "Deskflow (TLS)", port = port), false)
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

    internal fun isClientCertificateRejection(error: Exception): Boolean {
        if (InputLeapConnection.isClientCertificateRequired(error)) return true
        val message = generateSequence<Throwable>(error) { it.cause }
            .joinToString(" ") { it.message.orEmpty() }
            .lowercase()
        return "empty client certificate chain" in message ||
            "bad certificate" in message ||
            "certificate required" in message ||
            "bad_certificate" in message ||
            "certificate_required" in message
    }

    internal fun probePlain(
        host: String,
        timeoutMs: Int,
        assumePlain: Boolean,
        port: Int = ProtocolConstants.DEFAULT_PORT,
    ): ServerInfo? = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            socket.tcpNoDelay = true
            val hello = readHello(host, DataInputStream(socket.inputStream))
            hello ?: if (assumePlain) ServerInfo(ip = host, name = "Deskflow (Plain)", port = port) else null
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Deskflow HELLO: 4-byte length prefix + seven-byte Barrier/Synergy magic +
     * major (2B) + minor (2B).
     */
    internal fun readHello(host: String, din: DataInputStream): ServerInfo? = try {
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

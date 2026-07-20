package com.inputleaf.android.network

import android.util.Log
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.protocol.ProtocolParser
import com.inputleaf.android.protocol.ProtocolWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocket

private const val TAG = "InputLeapConnection"
private const val HANDSHAKE_READ_TIMEOUT_MS = 15_000
private const val TLS_CONNECT_TIMEOUT_CACHED_MS = 800
private const val TLS_CONNECT_TIMEOUT_MS = 2_000
private const val TLS_HANDSHAKE_TIMEOUT_MS = 1_500
private const val PLAIN_CONNECT_TIMEOUT_CACHED_MS = 800
private const val PLAIN_CONNECT_TIMEOUT_MS = 2_000

class InputLeapConnection(
    private val ip: String,
    private val port: Int = 24800,
    private val preferredTransport: ServerTransport? = null,
    private val pinnedFingerprint: String? = null,
    private val onCertificate: suspend (X509Certificate) -> Boolean,
) {
    private val _events = MutableSharedFlow<InputLeapEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<InputLeapEvent> = _events

    private var socket: Socket? = null
    private var writer: ProtocolWriter? = null
    private var sharedDin: DataInputStream? = null
    private var sharedParser: ProtocolParser? = null
    private val readerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readJob: Job? = null

    data class ServerBanner(val major: Int, val minor: Int)

    suspend fun connect(screenName: String, screenWidth: Int, screenHeight: Int): ConnectResult =
        withContext(Dispatchers.IO) {
            val transports = buildTransportOrder()
            var lastError: Exception? = null
            for (transport in transports) {
                when (val opened = openSocket(transport)) {
                    is SocketOpenResult.Ok -> {
                        val result = runHandshake(
                            opened.socket,
                            opened.transport,
                            screenName,
                            screenWidth,
                            screenHeight,
                        )
                        if (result != null) {
                            return@withContext result
                        }
                        lastError = Exception("Handshake failed on $transport")
                    }
                    is SocketOpenResult.Rejected -> return@withContext ConnectResult.RejectedByUser
                    is SocketOpenResult.Failed -> lastError = opened.error
                }
            }
            Log.e(TAG, "All transports failed for $ip: ${lastError?.message}")
            ConnectResult.NetworkError
        }

    private fun buildTransportOrder(): List<ServerTransport> {
        preferredTransport?.let { cached ->
            val fallback = when (cached) {
                ServerTransport.TLS -> ServerTransport.PLAIN
                ServerTransport.PLAIN -> ServerTransport.TLS
            }
            return listOf(cached, fallback)
        }
        // No stored TLS fingerprint — plain server is likely; avoid a doomed TLS attempt first.
        if (pinnedFingerprint == null) {
            return listOf(ServerTransport.PLAIN, ServerTransport.TLS)
        }
        return listOf(ServerTransport.TLS, ServerTransport.PLAIN)
    }

    private sealed class SocketOpenResult {
        data class Ok(val socket: Socket, val transport: ServerTransport) : SocketOpenResult()
        data object Rejected : SocketOpenResult()
        data class Failed(val error: Exception) : SocketOpenResult()
    }

    private suspend fun openSocket(transport: ServerTransport): SocketOpenResult = try {
        when (transport) {
            ServerTransport.TLS -> openTlsSocket()
            ServerTransport.PLAIN -> SocketOpenResult.Ok(openPlainSocket(), ServerTransport.PLAIN)
        }
    } catch (e: Exception) {
        SocketOpenResult.Failed(e)
    }

    private suspend fun openTlsSocket(): SocketOpenResult {
        val connectTimeout = if (preferredTransport == ServerTransport.TLS) {
            TLS_CONNECT_TIMEOUT_CACHED_MS
        } else {
            TLS_CONNECT_TIMEOUT_MS
        }
        return try {
            val rawSocket = if (pinnedFingerprint != null) {
                val sslContext = TlsFingerprintManager.buildPinningSSLContext(pinnedFingerprint)
                val sslSock = sslContext.socketFactory.createSocket() as SSLSocket
                sslSock.connect(InetSocketAddress(ip, port), connectTimeout)
                sslSock.soTimeout = TLS_HANDSHAKE_TIMEOUT_MS
                sslSock.startHandshake()
                sslSock.soTimeout = HANDSHAKE_READ_TIMEOUT_MS
                sslSock
            } else {
                var capturedCert: X509Certificate? = null
                val sslContext = TlsFingerprintManager.buildCapturingSSLContext { cert ->
                    capturedCert = cert
                }
                val sslSock = sslContext.socketFactory.createSocket() as SSLSocket
                sslSock.connect(InetSocketAddress(ip, port), connectTimeout)
                sslSock.soTimeout = TLS_HANDSHAKE_TIMEOUT_MS
                sslSock.startHandshake()
                sslSock.soTimeout = HANDSHAKE_READ_TIMEOUT_MS
                val cert = capturedCert ?: run {
                    sslSock.close()
                    return SocketOpenResult.Failed(IllegalStateException("No certificate captured"))
                }
                if (!onCertificate(cert)) {
                    sslSock.close()
                    return SocketOpenResult.Rejected
                }
                sslSock
            }
            SocketOpenResult.Ok(rawSocket, ServerTransport.TLS)
        } catch (e: Exception) {
            Log.w(TAG, "TLS open failed for $ip: ${e.message}")
            SocketOpenResult.Failed(e)
        }
    }

    private fun openPlainSocket(): Socket {
        val connectTimeout = if (preferredTransport == ServerTransport.PLAIN) {
            PLAIN_CONNECT_TIMEOUT_CACHED_MS
        } else {
            PLAIN_CONNECT_TIMEOUT_MS
        }
        return Socket().apply {
            connect(InetSocketAddress(ip, port), connectTimeout)
            tcpNoDelay = true
            soTimeout = HANDSHAKE_READ_TIMEOUT_MS
        }
    }

  /**
   * Run the Input Leap handshake synchronously before returning.
   * Matches schengen client: server hello → client hello → QINF → DINF → LSYN/CIAK/CROP/DSOP.
   */
    private fun runHandshake(
        rawSocket: Socket,
        transport: ServerTransport,
        screenName: String,
        screenWidth: Int,
        screenHeight: Int,
    ): ConnectResult? {
        rawSocket.tcpNoDelay = true
        socket = rawSocket
        writer = ProtocolWriter(rawSocket.outputStream)
        val din = DataInputStream(rawSocket.inputStream)
        sharedDin = din
        val parser = ProtocolParser(din)
        sharedParser = parser

        var helloSent = false
        var dinfSent = false
        var sawPostDinf = false
        var bannerMajor = 1
        var bannerMinor = 6

        try {
            repeat(32) {
                val event = parser.readNext()
                Log.d(TAG, "Handshake recv: $event")
                when (event) {
                    is InputLeapEvent.Hello -> {
                        bannerMajor = event.majorVersion
                        bannerMinor = event.minorVersion
                        if (!helloSent) {
                            writer?.writeHelloBack(screenName, 1, 6)
                            helloSent = true
                            Log.d(TAG, "Handshake sent client hello as $screenName")
                        }
                    }
                    is InputLeapEvent.QueryInfo -> {
                        writer?.writeDataInfo(screenWidth, screenHeight, 0, 0, 0, 0)
                        dinfSent = true
                        Log.d(TAG, "Handshake sent DINF ${screenWidth}x$screenHeight")
                    }
                    is InputLeapEvent.KeepAlive -> {
                        writer?.writeKeepAlive()
                    }
                    is InputLeapEvent.ResetOptions -> {
                        if (dinfSent) sawPostDinf = true
                    }
                    is InputLeapEvent.Unhandled -> {
                        when (event.tag) {
                            "CIAK", "CROP", "DSOP", "LSYN" -> if (dinfSent) sawPostDinf = true
                        }
                    }
                    is InputLeapEvent.Incompatible, is InputLeapEvent.Busy -> {
                        Log.e(TAG, "Server rejected handshake: $event")
                        close()
                        return null
                    }
                    else -> Unit
                }
                if (helloSent && dinfSent && sawPostDinf) {
                    rawSocket.soTimeout = 0
                    readJob = readerScope.launch { readLoop(parser) }
                    Log.d(TAG, "Handshake complete via $transport")
                    return ConnectResult.Ok(ServerBanner(bannerMajor, bannerMinor), transport)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Handshake error: ${e.javaClass.simpleName}: ${e.message}")
            close()
            return null
        }

        // Lenient: some servers omit LSYN/CIAK/CROP/DSOP but accept DINF.
        if (helloSent && dinfSent) {
            rawSocket.soTimeout = 0
            readJob = readerScope.launch { readLoop(parser) }
            Log.d(TAG, "Handshake complete (lenient) via $transport")
            return ConnectResult.Ok(ServerBanner(bannerMajor, bannerMinor), transport)
        }

        Log.e(TAG, "Handshake incomplete hello=$helloSent dinf=$dinfSent post=$sawPostDinf")
        close()
        return null
    }

    private suspend fun readLoop(parser: ProtocolParser) {
        try {
            while (true) {
                val event = parser.readNext()
                Log.d(TAG, "Read event: $event")
                _events.emit(event)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Read loop ended: ${e.javaClass.simpleName}: ${e.message}")
            _events.emit(InputLeapEvent.Unhandled("__DISCONNECTED__"))
        }
    }

    fun clearHandshakeTimeout() {
        runCatching { socket?.soTimeout = 0 }
    }

    fun sendHelloBack(screenName: String) = writer?.writeHelloBack(screenName, 1, 6)
    fun sendDataInfo(w: Int, h: Int) = writer?.writeDataInfo(w, h, 0, 0, 0, 0)
    fun sendKeepAlive() = writer?.writeKeepAlive()
    fun sendInfoAck() = writer?.writeInfoAck()

    fun close() {
        readJob?.cancel()
        readJob = null
        runCatching { socket?.close() }
        socket = null
        writer = null
        sharedDin = null
        sharedParser = null
    }
}

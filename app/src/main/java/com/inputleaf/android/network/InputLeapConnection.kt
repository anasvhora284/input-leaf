package com.inputleaf.android.network

import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.protocol.ProtocolParser
import com.inputleaf.android.protocol.ProtocolWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import android.util.Log
import java.net.Socket
import javax.net.ssl.SSLSocket

private const val TAG = "InputLeapConnection"

class InputLeapConnection(
    private val ip: String,
    private val port: Int = 24800,
    private val onCertificate: suspend (X509Certificate) -> Boolean
) {
    private val _events = MutableSharedFlow<InputLeapEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<InputLeapEvent> = _events

    private var socket: Socket? = null
    private var writer: ProtocolWriter? = null
    // Single shared DataInputStream — MUST NOT create a second wrapper on the same socket stream
    private var sharedDin: java.io.DataInputStream? = null
    private val readerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class ServerBanner(val major: Int, val minor: Int)

    suspend fun connect(): ServerBanner? = withContext(Dispatchers.IO) {
        var rawSocket: Socket? = null
        try {
            var capturedCert: X509Certificate? = null
            val sslContext = TlsFingerprintManager.buildCapturingSSLContext { cert ->
                capturedCert = cert
            }
            val sslSock = sslContext.socketFactory.createSocket() as SSLSocket
            sslSock.connect(InetSocketAddress(ip, port), 5000)
            sslSock.startHandshake()
            
            val cert = capturedCert ?: run { sslSock.close(); return@withContext null }
            val accepted = onCertificate(cert)
            if (!accepted) { sslSock.close(); return@withContext null }
            
            rawSocket = sslSock
        } catch (e: Exception) {
            Log.w(TAG, "SSL connection failed, falling back to plain-text: ${e.message}")
            try {
                val plainSock = Socket()
                plainSock.connect(InetSocketAddress(ip, port), 5000)
                rawSocket = plainSock
            } catch (fallbackE: Exception) {
                Log.e(TAG, "Plain-text fallback failed: ${fallbackE.message}")
                return@withContext null
            }
        }
        
        val connectedSocket = rawSocket ?: return@withContext null
        socket = connectedSocket
        writer = ProtocolWriter(connectedSocket.outputStream)

        // ONE shared DataInputStream for the entire lifetime of this connection.
        // Creating two DataInputStream wrappers on the same socket InputStream causes
        // each to buffer independently — bytes consumed by one become invisible to the other,
        // which is exactly what caused the connect/disconnect loop.
        val din = java.io.DataInputStream(connectedSocket.inputStream)
        sharedDin = din

        // Read the server's initial Banner: [4-byte len]["Barrier" + version string]
        // Input Leap banner format: len(4) body starts with "Barrier" (7 bytes) then " " major/minor
        val bannerLen = din.readInt()
        if (bannerLen < 11 || bannerLen > 1024) {
            Log.e(TAG, "Banner length invalid: $bannerLen bytes")
            connectedSocket.close(); return@withContext null
        }
        val bannerBody = ByteArray(bannerLen)
        din.readFully(bannerBody)
        val bannerTag = String(bannerBody, 0, 4, Charsets.US_ASCII)
        if (bannerTag != "Barr") {
            Log.e(TAG, "Unexpected server greeting: $bannerTag")
            connectedSocket.close(); return@withContext null
        }
        val major = ((bannerBody[7].toInt() and 0xFF) shl 8) or (bannerBody[8].toInt() and 0xFF)
        val minor = ((bannerBody[9].toInt() and 0xFF) shl 8) or (bannerBody[10].toInt() and 0xFF)
        Log.d(TAG, "Server banner: InputLeap $major.$minor")

        readerScope.launch { readLoop(connectedSocket, din) }
        ServerBanner(major, minor)
    }

    private suspend fun readLoop(sock: Socket, din: java.io.DataInputStream) {
        try {
            val parser = ProtocolParser(din)  // reuses the already-positioned shared stream
            while (true) {
                val event = parser.readNext()
                Log.d(TAG, "Read event: $event")
                _events.emit(event)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Read loop ended: ${e.javaClass.simpleName}: ${e.message}")
            _events.emit(InputLeapEvent.Unhandled("__DISCONNECTED__"))
        }
    }

    fun sendHelloBack(screenName: String) = writer?.writeHelloBack(screenName, 1, 6)
    fun sendDataInfo(w: Int, h: Int) = writer?.writeDataInfo(w, h, 0, 0, 0, 0)
    fun sendKeepAlive() = writer?.writeKeepAlive()
    fun sendInfoAck() = writer?.writeInfoAck()

    fun close() {
        readerScope.coroutineContext[Job]?.cancel()
        runCatching { socket?.close() }
        socket = null; writer = null; sharedDin = null
    }
}

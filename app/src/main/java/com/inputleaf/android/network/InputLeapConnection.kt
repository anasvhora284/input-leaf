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
import javax.net.ssl.SSLSocket

private const val TAG = "InputLeapConnection"

class InputLeapConnection(
    private val ip: String,
    private val port: Int = 24800,
    private val onCertificate: suspend (X509Certificate) -> Boolean
) {
    private val _events = MutableSharedFlow<InputLeapEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<InputLeapEvent> = _events

    private var socket: SSLSocket? = null
    private var writer: ProtocolWriter? = null
    private val readerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class ServerBanner(val major: Int, val minor: Int)

    suspend fun connect(): ServerBanner? = withContext(Dispatchers.IO) {
        var capturedCert: X509Certificate? = null
        val sslContext = TlsFingerprintManager.buildCapturingSSLContext { cert ->
            capturedCert = cert
        }
        val raw = sslContext.socketFactory.createSocket() as SSLSocket
        raw.connect(InetSocketAddress(ip, port), 5000)
        raw.startHandshake()

        val cert = capturedCert ?: run { raw.close(); return@withContext null }
        val accepted = onCertificate(cert)
        if (!accepted) { raw.close(); return@withContext null }

        socket = raw
        writer = ProtocolWriter(raw.outputStream)

        // Read the server's initial Banner (length-prefixed: 4-byte len + "Barrier" + version)
        val din = java.io.DataInputStream(raw.inputStream)
        val bannerLen = din.readInt()
        if (bannerLen < 11) { raw.close(); return@withContext null }
        val bannerBody = ByteArray(bannerLen)
        din.readFully(bannerBody)
        val bannerTag = String(bannerBody, 0, 4, Charsets.US_ASCII)
        if (bannerTag != "Barr") {
            Log.e(TAG, "Unexpected server greeting tag: $bannerTag")
            raw.close(); return@withContext null
        }
        val major = ((bannerBody[7].toInt() and 0xFF) shl 8) or (bannerBody[8].toInt() and 0xFF)
        val minor = ((bannerBody[9].toInt() and 0xFF) shl 8) or (bannerBody[10].toInt() and 0xFF)
        Log.d(TAG, "Server banner: InputLeap $major.$minor")

        readerScope.launch { readLoop(raw) }
        ServerBanner(major, minor)
    }

    private suspend fun readLoop(sock: SSLSocket) {
        try {
            val parser = ProtocolParser(sock.inputStream)
            while (true) {
                val event = parser.readNext()
                Log.d(TAG, "Read event: $event")
                _events.emit(event)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Read loop ended: ${e.javaClass.simpleName}: ${e.message}", e)
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
        socket = null; writer = null
    }
}

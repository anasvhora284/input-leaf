package com.inputleaf.android.network

import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.protocol.ProtocolParser
import com.inputleaf.android.protocol.ProtocolWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
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

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        var capturedCert: X509Certificate? = null
        val sslContext = TlsFingerprintManager.buildCapturingSSLContext { cert ->
            capturedCert = cert
        }
        val raw = sslContext.socketFactory.createSocket() as SSLSocket
        raw.connect(InetSocketAddress(ip, port), 5000)
        raw.startHandshake()

        val cert = capturedCert ?: run { raw.close(); return@withContext false }
        val accepted = onCertificate(cert)
        if (!accepted) { raw.close(); return@withContext false }

        socket = raw
        writer = ProtocolWriter(raw.outputStream)
        readerScope.launch { readLoop(raw) }
        true
    }

    private suspend fun readLoop(sock: SSLSocket) {
        try {
            val parser = ProtocolParser(sock.inputStream)
            while (true) {
                val event = parser.readNext()
                _events.emit(event)
            }
        } catch (e: Exception) {
            println("$TAG: Read loop ended: ${e.message}")
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

package com.inputleaf.android.network

import android.util.Log
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.protocol.ProtocolConstants
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

private const val TAG = "InputLeapConnection"
private const val HANDSHAKE_READ_TIMEOUT_MS = 15_000
private const val TLS_CONNECT_TIMEOUT_CACHED_MS = 800
private const val TLS_CONNECT_TIMEOUT_MS = 2_000
private const val TLS_HANDSHAKE_TIMEOUT_MS = 1_500
private const val TLS_CLIENT_AUTH_HANDSHAKE_TIMEOUT_MS = 90_000
private const val PLAIN_CONNECT_TIMEOUT_CACHED_MS = 800
private const val PLAIN_CONNECT_TIMEOUT_MS = 2_000

class InputLeapConnection(
    private val ip: String,
    private val port: Int = 24800,
    private val preferredTransport: ServerTransport? = null,
    private val pinnedFingerprint: String? = null,
    private val transportPolicy: ConnectionTransportPolicy = ConnectionTransportPolicy.AUTO,
    private val clientCertificate: ClientCertificateMaterial? = null,
    private val logger: Logger = AndroidLogger,
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
    private val connectMutex = Mutex()

    /** Version advertised by the server before client-side minor-version negotiation. */
    data class ServerBanner(val major: Int, val minor: Int)

    /** Minimal logging seam for tests and advanced integrations. */
    interface Logger {
        fun debug(message: String)
        fun warn(message: String)
        fun error(message: String)
    }

    private object AndroidLogger : Logger {
        override fun debug(message: String) {
            Log.d(TAG, message)
        }

        override fun warn(message: String) {
            Log.w(TAG, message)
        }

        override fun error(message: String) {
            Log.e(TAG, message)
        }
    }

    /**
     * Opens and handshakes a connection. Attempts are serialized, and calling this while the
     * connection is already open throws [IllegalStateException]. Call [close] before reconnecting.
     */
    suspend fun connect(screenName: String, screenWidth: Int, screenHeight: Int): ConnectResult =
        connectMutex.withLock {
            check(socket == null) { "Connection is already open; close it before reconnecting" }
            withContext(Dispatchers.IO) {
                val detectedMode =
                    if (
                        transportPolicy == ConnectionTransportPolicy.AUTO &&
                        pinnedFingerprint == null
                    ) {
                        TransportProber.detect(ip, port)
                    } else {
                        null
                    }
                val transports =
                    if (
                        transportPolicy == ConnectionTransportPolicy.AUTO &&
                        pinnedFingerprint != null
                    ) {
                        listOf(ServerTransport.TLS)
                    } else {
                        TransportPolicy.order(
                            policy = transportPolicy,
                            preferredTransport = preferredTransport,
                            detectedMode = detectedMode,
                        )
                    }
                var lastFailure: ConnectResult.Failed? = null
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
                            if (result is ConnectResult.Ok) {
                                return@withContext result
                            }
                            if (result is ConnectResult.Failed) {
                                if (pinnedFingerprint != null ||
                                    !transportPolicy.shouldFallbackWithinAttempt(result.reason)
                                ) {
                                    return@withContext result
                                }
                                lastFailure = selectFailureToReport(lastFailure, result)
                            }
                        }
                        is SocketOpenResult.Rejected -> return@withContext ConnectResult.RejectedByUser
                        is SocketOpenResult.Failed -> {
                            if (pinnedFingerprint != null ||
                                !transportPolicy.shouldFallbackWithinAttempt(opened.failure.reason)
                            ) {
                                return@withContext opened.failure
                            }
                            lastFailure = selectFailureToReport(lastFailure, opened.failure)
                        }
                    }
                }
                val failure = lastFailure ?: ConnectResult.Failed(ConnectResult.FailureReason.NETWORK)
                logger.error("All transports failed for $ip: ${failure.reason} ${failure.detail}")
                failure
            }
        }

    private sealed class SocketOpenResult {
        data class Ok(val socket: Socket, val transport: ServerTransport) : SocketOpenResult()
        data object Rejected : SocketOpenResult()
        data class Failed(val failure: ConnectResult.Failed) : SocketOpenResult()
    }

    private suspend fun openSocket(transport: ServerTransport): SocketOpenResult = try {
        when (transport) {
            ServerTransport.TLS -> openTlsSocket()
            ServerTransport.PLAIN -> try {
                SocketOpenResult.Ok(openPlainSocket(), ServerTransport.PLAIN)
            } catch (e: Exception) {
                logger.warn("Plain open failed for $ip: ${e.message}")
                SocketOpenResult.Failed(
                    ConnectResult.Failed(ConnectResult.FailureReason.NETWORK, e.message),
                )
            }
        }
    } catch (e: Exception) {
        SocketOpenResult.Failed(
            ConnectResult.Failed(ConnectResult.FailureReason.NETWORK, e.message),
        )
    }

    private suspend fun openTlsSocket(): SocketOpenResult {
        var openedSocket: SSLSocket? = null
        val connectTimeout = if (
            transportPolicy == ConnectionTransportPolicy.AUTO &&
            preferredTransport == ServerTransport.TLS
        ) {
            TLS_CONNECT_TIMEOUT_CACHED_MS
        } else {
            TLS_CONNECT_TIMEOUT_MS
        }
        return try {
            var capturedCert: X509Certificate? = null
            val sslContext = TlsFingerprintManager.buildCapturingSSLContext(
                clientCertificate = clientCertificate,
                onCertificate = { cert -> capturedCert = cert },
            )
            val sslSock = sslContext.socketFactory.createSocket() as SSLSocket
            openedSocket = sslSock
            sslSock.connect(InetSocketAddress(ip, port), connectTimeout)
            sslSock.soTimeout = tlsHandshakeTimeoutMs()
            sslSock.startHandshake()
            sslSock.soTimeout = HANDSHAKE_READ_TIMEOUT_MS
            val cert = capturedCert ?: run {
                sslSock.close()
                openedSocket = null
                return SocketOpenResult.Failed(
                    ConnectResult.Failed(
                        ConnectResult.FailureReason.NETWORK,
                        "No certificate captured",
                    ),
                )
            }
            val fingerprint = TlsFingerprintManager.fingerprintOf(cert)
            val alreadyTrusted = pinnedFingerprint?.let(TlsFingerprintManager::normalizeFingerprint) == fingerprint
            if (!alreadyTrusted && !onCertificate(cert)) {
                sslSock.close()
                openedSocket = null
                return SocketOpenResult.Rejected
            }
            SocketOpenResult.Ok(sslSock, ServerTransport.TLS)
        } catch (e: Exception) {
            runCatching { openedSocket?.close() }
            if (clientCertificate == null && isClientCertificateRequired(e)) {
                logger.warn("Deskflow requires a client certificate")
                SocketOpenResult.Failed(
                    ConnectResult.Failed(
                        ConnectResult.FailureReason.CLIENT_CERT_REQUIRED,
                        e.message,
                    ),
                )
            } else if (isCertificateMismatch(e)) {
                logger.warn("TLS certificate changed for $ip")
                SocketOpenResult.Failed(
                    ConnectResult.Failed(
                        ConnectResult.FailureReason.CERTIFICATE_MISMATCH,
                        e.message,
                    ),
                )
            } else if (isPlainServerTlsError(e)) {
                logger.debug("TLS required, but $ip speaks plain Deskflow")
                SocketOpenResult.Failed(
                    ConnectResult.Failed(
                        ConnectResult.FailureReason.TLS_AGAINST_PLAIN_SERVER,
                        e.message,
                    ),
                )
            } else {
                logger.warn("TLS open failed for $ip: ${e.message}")
                SocketOpenResult.Failed(
                    ConnectResult.Failed(ConnectResult.FailureReason.NETWORK, e.message),
                )
            }
        }
    }

    private fun tlsHandshakeTimeoutMs(): Int =
        if (clientCertificate != null) {
            // Deskflow blocks the TLS handshake until the user trusts this phone's
            // certificate. Aborting at 1.5s drops that dialog and Auto then waits
            // 15s on a doomed plaintext attempt.
            TLS_CLIENT_AUTH_HANDSHAKE_TIMEOUT_MS
        } else {
            TLS_HANDSHAKE_TIMEOUT_MS
        }

    private fun openPlainSocket(): Socket {
        val connectTimeout = if (
            transportPolicy == ConnectionTransportPolicy.AUTO &&
            preferredTransport == ServerTransport.PLAIN
        ) {
            PLAIN_CONNECT_TIMEOUT_CACHED_MS
        } else {
            PLAIN_CONNECT_TIMEOUT_MS
        }
        // Resolve the destination before touching Socket. Inside Socket.apply,
        // `port` is Socket.port (0 until connected), not Deskflow's 24800.
        val destination = InetSocketAddress(ip, port)
        val socket = Socket()
        socket.connect(destination, connectTimeout)
        socket.tcpNoDelay = true
        socket.soTimeout = HANDSHAKE_READ_TIMEOUT_MS
        return socket
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
    ): ConnectResult {
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
        var bannerMajor = ProtocolConstants.PROTOCOL_MAJOR
        var bannerMinor = ProtocolConstants.PROTOCOL_MINOR

        try {
            repeat(32) {
                val event = parser.readNext()
                logger.debug("Handshake recv: $event")
                when (event) {
                    is InputLeapEvent.Hello -> {
                        bannerMajor = event.majorVersion
                        bannerMinor = event.minorVersion
                        if (!helloSent) {
                            val negotiatedProtocol = event.protocol
                            val negotiatedMinor =
                                ProtocolConstants.negotiateMinor(event.minorVersion)
                            writer?.writeHelloBack(
                                screenName = screenName,
                                major = ProtocolConstants.PROTOCOL_MAJOR,
                                minor = negotiatedMinor,
                                protocol = negotiatedProtocol,
                            )
                            helloSent = true
                            logger.debug(
                                "Handshake sent ${negotiatedProtocol.magic} client hello " +
                                    "as $screenName using 1.$negotiatedMinor",
                            )
                        }
                    }
                    is InputLeapEvent.QueryInfo -> {
                        writer?.writeDataInfo(screenWidth, screenHeight, 0, 0, 0, 0)
                        dinfSent = true
                        logger.debug("Handshake sent DINF ${screenWidth}x$screenHeight")
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
                    is InputLeapEvent.Incompatible -> {
                        logger.error("Server rejected handshake: $event")
                        close()
                        return ConnectResult.Failed(
                            ConnectResult.FailureReason.INCOMPATIBLE,
                            "Server requires ${event.major}.${event.minor}",
                        )
                    }
                    is InputLeapEvent.Busy -> {
                        logger.error("Server rejected handshake: busy")
                        close()
                        return ConnectResult.Failed(ConnectResult.FailureReason.BUSY)
                    }
                    else -> Unit
                }
                if (helloSent && dinfSent && sawPostDinf) {
                    rawSocket.soTimeout = 0
                    readJob = readerScope.launch { readLoop(parser) }
                    logger.debug("Handshake complete via $transport")
                    return ConnectResult.Ok(ServerBanner(bannerMajor, bannerMinor), transport)
                }
            }
        } catch (e: Exception) {
            logger.error("Handshake error: ${e.javaClass.simpleName}: ${e.message}")
            close()
            return ConnectResult.Failed(ConnectResult.FailureReason.HANDSHAKE, e.message)
        }

        // Lenient: some servers omit LSYN/CIAK/CROP/DSOP but accept DINF.
        if (helloSent && dinfSent) {
            rawSocket.soTimeout = 0
            readJob = readerScope.launch { readLoop(parser) }
            logger.debug("Handshake complete (lenient) via $transport")
            return ConnectResult.Ok(ServerBanner(bannerMajor, bannerMinor), transport)
        }

        logger.error("Handshake incomplete hello=$helloSent dinf=$dinfSent post=$sawPostDinf")
        close()
        return ConnectResult.Failed(
            ConnectResult.FailureReason.HANDSHAKE,
            "Incomplete handshake: hello=$helloSent, deviceInfo=$dinfSent",
        )
    }

    private suspend fun readLoop(parser: ProtocolParser) {
        try {
            while (true) {
                val event = parser.readNext()
                if (event !is InputLeapEvent.MouseMoveAbs &&
                    event !is InputLeapEvent.MouseMoveRel &&
                    event !is InputLeapEvent.KeepAlive
                ) {
                    logger.debug("Read event: $event")
                }
                _events.emit(event)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Read loop ended: ${e.javaClass.simpleName}: ${e.message}")
            _events.emit(InputLeapEvent.Unhandled("__DISCONNECTED__"))
        }
    }

    fun clearHandshakeTimeout() {
        runCatching { socket?.soTimeout = 0 }
    }

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

    companion object {
        internal fun selectFailureToReport(
            current: ConnectResult.Failed?,
            candidate: ConnectResult.Failed,
        ): ConnectResult.Failed {
            val candidatePriority = failurePriority(candidate.reason)
            val currentPriority = current?.let { failurePriority(it.reason) }
            return if (currentPriority == null || candidatePriority > currentPriority) {
                candidate
            } else {
                current
            }
        }

        private fun failurePriority(reason: ConnectResult.FailureReason): Int = when (reason) {
            ConnectResult.FailureReason.CERTIFICATE_MISMATCH,
            ConnectResult.FailureReason.CLIENT_CERT_REQUIRED,
            ConnectResult.FailureReason.INCOMPATIBLE,
            ConnectResult.FailureReason.BUSY -> 4
            ConnectResult.FailureReason.NETWORK -> 3
            ConnectResult.FailureReason.HANDSHAKE -> 2
            ConnectResult.FailureReason.TLS_AGAINST_PLAIN_SERVER -> 1
        }

        internal fun isCertificateMismatch(error: Exception): Boolean =
            generateSequence<Throwable>(error) { it.cause }
                .any { "certificate fingerprint mismatch" in it.message.orEmpty().lowercase() }

        internal fun isClientCertificateRequired(error: Exception): Boolean {
            val message = generateSequence<Throwable>(error) { it.cause }
                .joinToString(" ") { it.message.orEmpty() }
                .lowercase()
            return "certificate required" in message ||
                "certificate_required" in message ||
                "bad certificate" in message ||
                "bad_certificate" in message
        }

        internal fun isPlainServerTlsError(error: Exception): Boolean {
            val causes = generateSequence<Throwable>(error) { it.cause }.toList()
            val message = causes.joinToString(" ") { it.message.orEmpty() }.lowercase()
            return causes.any { it is SSLException } && (
                "unable to parse tls packet header" in message ||
                    "not an sslv2 hello" in message ||
                    "unsupported or unrecognized ssl message" in message
            )
        }
    }
}

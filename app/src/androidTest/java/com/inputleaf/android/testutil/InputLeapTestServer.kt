package com.inputleaf.android.testutil

import com.inputleaf.android.network.SelfSignedRsaCertificate
import com.inputleaf.android.protocol.ProtocolConstants
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * The service under test always dials the fixed Input Leap port, so loopback listeners must
 * bind it explicitly. SO_REUSEADDR keeps rebinding between sequential tests safe.
 */
internal const val INPUT_LEAP_TEST_PORT = 24800

internal fun boundLoopbackSocket(port: Int = INPUT_LEAP_TEST_PORT): ServerSocket =
    ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), port), 50)
    }

/** Writes one length-prefixed frame with [body], mirroring the client's framing. */
internal fun writeFrame(output: DataOutputStream, body: ByteArray) {
    output.writeInt(body.size)
    output.write(body)
    output.flush()
}

/** Reads one length-prefixed frame from the client. */
internal fun readFrame(input: DataInputStream): ByteArray {
    val length = input.readInt()
    require(length in 4..ProtocolConstants.MAX_MESSAGE_LEN) { "Invalid test frame length: $length" }
    return ByteArray(length).also { input.readFully(it) }
}

/** Server hello: Barrier magic + protocol major/minor (no tag prefix, like the real server). */
internal fun serverHelloBody(
    minor: Int = ProtocolConstants.PROTOCOL_MINOR,
): ByteArray = java.io.ByteArrayOutputStream().also { bytes ->
    DataOutputStream(bytes).use {
        it.write("Barrier".toByteArray())
        it.writeShort(ProtocolConstants.PROTOCOL_MAJOR)
        it.writeShort(minor)
    }
}.toByteArray()

internal fun tagFrame(tag: String, payload: ByteArray = ByteArray(0)): ByteArray =
    tag.toByteArray(Charsets.US_ASCII) + payload

/**
 * Plays the server half of the Input Leap handshake: server hello, QINF, CIAK. The client's
 * HELO and DINF frames are validated only for their magic so the fixture works with any
 * device screen size and screen name.
 */
internal fun performServerHandshake(socket: Socket) {
    val input = DataInputStream(socket.inputStream)
    val output = DataOutputStream(socket.outputStream)
    writeFrame(output, serverHelloBody())
    val clientHello = readFrame(input)
    check(clientHello.size >= 11 && String(clientHello, 0, 7, Charsets.US_ASCII) == "Barrier") {
        "Expected client Barrier hello"
    }
    writeFrame(output, tagFrame(ProtocolConstants.TAG_QUERY_INFO))
    val deviceInfo = readFrame(input)
    check(String(deviceInfo, 0, 4, Charsets.US_ASCII) == ProtocolConstants.TAG_DATA_INFO) {
        "Expected client DINF"
    }
    writeFrame(output, tagFrame(ProtocolConstants.TAG_INFO_ACK))
}

/** Enter payload per parser: x(2) y(2) sequence(4) flags(2). */
internal fun enterFrame(): ByteArray = tagFrame(ProtocolConstants.TAG_ENTER, ByteArray(10))

internal fun keepAliveFrame(): ByteArray = tagFrame(ProtocolConstants.TAG_KEEPALIVE)

internal fun leaveFrame(): ByteArray = tagFrame(ProtocolConstants.TAG_LEAVE)

/** MouseMoveAbs payload: x(2) y(2). */
internal fun mouseMoveAbsFrame(x: Int, y: Int): ByteArray {
    val payload = ByteArray(4)
    payload[0] = (x shr 8).toByte(); payload[1] = x.toByte()
    payload[2] = (y shr 8).toByte(); payload[3] = y.toByte()
    return tagFrame(ProtocolConstants.TAG_MOUSE_MOVE, payload)
}

/** MouseMoveRel payload: dx(4) dy(4). */
internal fun mouseMoveRelFrame(dx: Int, dy: Int): ByteArray {
    val payload = ByteArray(8)
    for (index in 0 until 4) {
        payload[index] = (dx shr (24 - 8 * index)).toByte()
        payload[4 + index] = (dy shr (24 - 8 * index)).toByte()
    }
    return tagFrame(ProtocolConstants.TAG_MOUSE_REL, payload)
}

/** KeyDown payload: key(2) modifier(2) keyCode(2). */
internal fun keyDownFrame(key: Int, modifier: Int = 0, keyCode: Int = 0): ByteArray {
    val payload = ByteArray(6)
    payload[0] = (key shr 8).toByte(); payload[1] = key.toByte()
    payload[2] = (modifier shr 8).toByte(); payload[3] = modifier.toByte()
    payload[4] = (keyCode shr 8).toByte(); payload[5] = keyCode.toByte()
    return tagFrame(ProtocolConstants.TAG_KEY_DOWN, payload)
}

/**
 * Terminations a plain listener sees while the client probes for TLS or reads a plain hello:
 * abrupt probe closes surface as EOF/SocketException, and TLS handshake bytes desynchronize
 * the frame reader into an invalid length.
 */
internal fun isExpectedPlainProbeTermination(failure: Exception): Boolean =
    failure is EOFException ||
        failure is SocketException ||
        (failure is IllegalArgumentException &&
            failure.message.orEmpty().startsWith("Invalid test frame length:"))

/**
 * Loopback TLS listener for the connected tests. Uses the app's own self-signed RSA identity
 * generator; no client certificate is requested, so the client under test completes the TLS
 * handshake and then decides via its fingerprint callback whether to trust the leaf.
 */
internal class TlsLoopbackServer(
    connectionCount: Int = 1,
    handler: (SSLSocket, Int) -> Unit,
) : LoopbackServer(
    connectionCount = connectionCount,
    serverSocket = newTlsServerSocket(),
    handler = { socket, index -> handler(socket as SSLSocket, index) },
) {
    private companion object {
        fun newTlsServerSocket(): SSLServerSocket {
            val material = SelfSignedRsaCertificate.generate()
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                load(material.pkcs12.inputStream(), material.password)
            }
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore, material.password) }
                .keyManagers
            val context = SSLContext.getInstance("TLS").apply {
                init(keyManagers, null, SecureRandom())
            }
            return (context.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), INPUT_LEAP_TEST_PORT), 50)
            }
        }
    }
}

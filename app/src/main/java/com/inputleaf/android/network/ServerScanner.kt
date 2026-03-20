package com.inputleaf.android.network

import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.protocol.ProtocolConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.DataInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import android.util.Log
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class ServerScanner {
    companion object {
        private const val BARRIER_TAG = "Barr"

        fun subnetHosts(deviceIp: String): List<String> {
            val parts = deviceIp.split(".")
            require(parts.size == 4) { "Expected a valid IPv4 address, got: $deviceIp" }
            val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
            return (1..254).map { "$prefix.$it" }.filter { it != deviceIp }
        }

        private val trustAllSslContext: SSLContext by lazy {
            val tm = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            }
            SSLContext.getInstance("TLS").also { it.init(null, arrayOf(tm), null) }
        }
    }

    suspend fun scan(deviceIp: String, timeoutMs: Int = 500): List<ServerInfo> =
        coroutineScope {
            val hosts = subnetHosts(deviceIp)
            val semaphore = Semaphore(32)
            hosts.map { host ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { probe(host, timeoutMs) }
                }
            }.awaitAll().filterNotNull()
        }

    private fun probe(host: String, timeoutMs: Int): ServerInfo? {
        val tls = probeTls(host, timeoutMs)
        if (tls != null) return tls
        val plain = probePlain(host, timeoutMs)
        if (plain != null) return plain
        return null
    }

    private fun probePlain(host: String, timeoutMs: Int): ServerInfo? = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, ProtocolConstants.DEFAULT_PORT), timeoutMs)
            socket.soTimeout = timeoutMs
            readHello(host, DataInputStream(socket.inputStream))
        }
    } catch (e: Exception) {
        Log.v("ServerScanner", "Plain probe failed for $host: ${e.message}")
        null
    }

    private fun probeTls(host: String, timeoutMs: Int): ServerInfo? = try {
        val raw = trustAllSslContext.socketFactory.createSocket()
        val sslSocket = (raw as SSLSocket).apply {
            connect(InetSocketAddress(host, ProtocolConstants.DEFAULT_PORT), timeoutMs)
            soTimeout = timeoutMs
            startHandshake()
        }
        val result = sslSocket.use { readHello(host, DataInputStream(it.inputStream)) }
        if (result != null) Log.d("ServerScanner", "TLS probe succeeded for $host")
        result
    } catch (e: Exception) {
        Log.v("ServerScanner", "TLS probe failed for $host: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    /**
     * InputLeap HELLO: 4-byte length prefix + "Barrier" (7 bytes) + major (2B) + minor (2B).
     * Length should be 11; first 4 bytes of body are "Barr".
     */
    private fun readHello(host: String, din: DataInputStream): ServerInfo? {
        val len = din.readInt()
        if (len < 11 || len > 256) return null
        val body = ByteArray(minOf(len, 11))
        din.readFully(body)
        val tag = String(body, 0, 4, Charsets.US_ASCII)
        if (tag != BARRIER_TAG) return null
        val major = ((body[7].toInt() and 0xFF) shl 8) or (body[8].toInt() and 0xFF)
        val minor = ((body[9].toInt() and 0xFF) shl 8) or (body[10].toInt() and 0xFF)
        return ServerInfo(ip = host, name = "InputLeap $major.$minor")
    }
}

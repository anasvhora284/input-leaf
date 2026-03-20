package com.inputleaf.android.network

import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.protocol.ProtocolConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket

class ServerScanner {
    companion object {
        fun subnetHosts(deviceIp: String): List<String> {
            val parts = deviceIp.split(".")
            val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
            return (1..254).map { "$prefix.$it" }.filter { it != deviceIp }
        }
    }

    suspend fun scan(deviceIp: String, timeoutMs: Int = 300): List<ServerInfo> =
        coroutineScope {
            val hosts = subnetHosts(deviceIp)
            val semaphore = Semaphore(32)
            hosts.map { host ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { probe(host, timeoutMs) }
                }
            }.awaitAll().filterNotNull()
        }

    private fun probe(host: String, timeoutMs: Int): ServerInfo? = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, ProtocolConstants.DEFAULT_PORT), timeoutMs)
            socket.soTimeout = timeoutMs
            val lenBuf = ByteArray(4)
            socket.inputStream.read(lenBuf)
            val len = ((lenBuf[0].toInt() and 0xFF) shl 24) or
                      ((lenBuf[1].toInt() and 0xFF) shl 16) or
                      ((lenBuf[2].toInt() and 0xFF) shl 8) or
                      (lenBuf[3].toInt() and 0xFF)
            if (len < 4 || len > 256) return null
            val body = ByteArray(minOf(len, 32))
            socket.inputStream.read(body)
            val tag = String(body, 0, 4, Charsets.US_ASCII)
            if (tag != ProtocolConstants.TAG_HELLO) return null
            val nameLen = ((body[8].toInt() and 0xFF) shl 24) or
                          ((body[9].toInt() and 0xFF) shl 16) or
                          ((body[10].toInt() and 0xFF) shl 8) or
                          (body[11].toInt() and 0xFF)
            val name = if (nameLen > 0 && nameLen <= 256 && body.size >= 12 + nameLen)
                String(body, 12, nameLen, Charsets.UTF_8) else ""
            ServerInfo(ip = host, name = name)
        }
    } catch (e: Exception) { null }
}

package com.inputleaf.android.testutil

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal const val LOOPBACK_HOST = "127.0.0.1"

internal open class LoopbackServer(
    connectionCount: Int = 1,
    private val serverSocket: ServerSocket = ServerSocket(0, 50, InetAddress.getByName(LOOPBACK_HOST)),
    handler: (Socket, Int) -> Unit,
) : Closeable {
    val port: Int = serverSocket.localPort
    private val failures = CopyOnWriteArrayList<Throwable>()
    private val workers = CopyOnWriteArrayList<Thread>()
    private val activeSockets = CopyOnWriteArrayList<Socket>()
    private val ready = CountDownLatch(1)
    // Keep the ephemeral port reserved until close(). A transport fallback can otherwise
    // connect to a later test that was assigned this port after the listener was released.
    private val acceptThread = thread(name = "loopback-accept-$port") {
        ready.countDown()
        try {
            repeat(connectionCount) { index ->
                val socket = serverSocket.accept()
                activeSockets += socket
                workers += thread(name = "loopback-worker-$port-$index") {
                    socket.use {
                        try {
                            handler(it, index)
                        } catch (failure: Throwable) {
                            failures += failure
                        } finally {
                            activeSockets -= socket
                        }
                    }
                }
            }
        } catch (failure: Throwable) {
            if (!serverSocket.isClosed) failures += failure
        }
    }

    init {
        check(ready.await(1, TimeUnit.SECONDS)) { "Loopback server did not start" }
    }

    override fun close() {
        serverSocket.close()
        acceptThread.join(2_000)
        check(!acceptThread.isAlive) { "Loopback accept thread did not stop" }

        activeSockets.forEach { it.close() }
        workers.forEach { it.join(2_000) }
        check(workers.none { it.isAlive }) { "Loopback worker thread did not stop" }
        failures.firstOrNull()?.let { throw AssertionError("Loopback server failed", it) }
    }
}

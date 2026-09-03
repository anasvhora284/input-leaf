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

/**
 * A local-only server fixture that expects exactly [connectionCount] handler connections.
 *
 * Each expected connection runs [handler] on its own worker thread and its socket is closed when
 * the handler returns. The listener remains open until [close] so a connection beyond the expected
 * count is accepted, closed, and reported as a test failure instead of being left in the backlog.
 * Callers must close this fixture; [close] stops the accept thread, closes active sockets, waits for
 * all workers, and reports the first failure with any later failures suppressed.
 */
internal open class LoopbackServer(
    private val connectionCount: Int = 1,
    serverSocket: ServerSocket? = null,
    private val handler: (Socket, Int) -> Unit,
) : Closeable {
    private val serverSocket: ServerSocket
    val port: Int
    private val failures = CopyOnWriteArrayList<Throwable>()
    private val workers = CopyOnWriteArrayList<Thread>()
    private val activeSockets = CopyOnWriteArrayList<Socket>()
    private val ready = CountDownLatch(1)
    // Keep the ephemeral port reserved until close(). A transport fallback can otherwise
    // connect to a later test that was assigned this port after the listener was released.
    private val acceptThread: Thread

    init {
        require(connectionCount > 0) { "connectionCount must be positive" }
        this.serverSocket = serverSocket ?: ServerSocket(
            0,
            50,
            InetAddress.getByName(LOOPBACK_HOST),
        )
        require(
            !this.serverSocket.isClosed &&
                this.serverSocket.isBound &&
                this.serverSocket.inetAddress.isLoopbackAddress,
        ) { "Loopback server socket must be open and bound to a loopback address" }
        port = this.serverSocket.localPort

        acceptThread = thread(name = "loopback-accept-$port") {
            ready.countDown()
            try {
                var index = 0
                while (true) {
                    val socket = this@LoopbackServer.serverSocket.accept()
                    if (index < connectionCount) {
                        startWorker(socket, index++)
                    } else {
                        socket.use {
                            failures += AssertionError(
                                "Unexpected connection after $connectionCount expected connections",
                            )
                        }
                    }
                }
            } catch (failure: Throwable) {
                if (!this@LoopbackServer.serverSocket.isClosed) failures += failure
            }
        }
        check(ready.await(1, TimeUnit.SECONDS)) { "Loopback server did not start" }
    }

    override fun close() {
        serverSocket.close()
        acceptThread.join(2_000)
        check(!acceptThread.isAlive) { "Loopback accept thread did not stop" }

        activeSockets.forEach { it.close() }
        workers.forEach { it.join(2_000) }
        check(workers.none { it.isAlive }) { "Loopback worker thread did not stop" }

        failures.firstOrNull()?.let { primary ->
            throw AssertionError("Loopback server failed", primary).apply {
                failures.drop(1).forEach(::addSuppressed)
            }
        }
    }

    private fun startWorker(socket: Socket, index: Int) {
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
}

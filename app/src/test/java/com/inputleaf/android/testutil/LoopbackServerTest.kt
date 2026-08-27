package com.inputleaf.android.testutil

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LoopbackServerTest {
    @Test fun `requires a positive connection count and loopback listener`() {
        assertThrows(IllegalArgumentException::class.java) {
            LoopbackServer(connectionCount = 0) { _, _ -> }
        }
        assertThrows(IllegalArgumentException::class.java) {
            LoopbackServer(connectionCount = -1) { _, _ -> }
        }
        ServerSocket(0).use { listener ->
            assertThrows(IllegalArgumentException::class.java) {
                LoopbackServer(serverSocket = listener) { _, _ -> }
            }
        }
    }

    @Test fun `default listener serves a loopback client`() {
        LoopbackServer { socket, index ->
            assertThat(index).isEqualTo(0)
            assertThat(socket.localAddress.isLoopbackAddress).isTrue()
            assertThat(socket.inetAddress.isLoopbackAddress).isTrue()
            socket.outputStream.write(TEST_BYTE)
        }.use { server ->
            loopbackClient(server.port).use { client ->
                assertThat(client.inetAddress.isLoopbackAddress).isTrue()
                assertThat(client.inputStream.read()).isEqualTo(TEST_BYTE)
            }
        }
    }

    @Test fun `reports connections beyond the expected count`() {
        val expectedConnectionAccepted = CountDownLatch(1)
        val releaseExpectedConnection = CountDownLatch(1)
        val failure = assertLoopbackServerFailure {
            LoopbackServer { _, _ ->
                expectedConnectionAccepted.countDown()
                assertThat(releaseExpectedConnection.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
            }.use { server ->
                try {
                    loopbackClient(server.port).use {
                        assertThat(
                            expectedConnectionAccepted.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                        ).isTrue()
                        loopbackClient(server.port).use { unexpected ->
                            assertThat(unexpected.inputStream.read()).isEqualTo(-1)
                        }
                    }
                } finally {
                    releaseExpectedConnection.countDown()
                }
            }
        }

        assertThat(failure.cause).hasMessageThat()
            .contains("Unexpected connection after 1 expected connections")
    }

    @Test fun `reports every handler failure`() {
        val firstFailure = IllegalStateException("first handler failed")
        val secondFailure = IllegalArgumentException("second handler failed")
        val failure = assertLoopbackServerFailure {
            LoopbackServer(connectionCount = 2) { _, index ->
                throw if (index == 0) firstFailure else secondFailure
            }.use { server ->
                loopbackClient(server.port).use { first ->
                    assertThat(first.inputStream.read()).isEqualTo(-1)
                }
                loopbackClient(server.port).use { second ->
                    assertThat(second.inputStream.read()).isEqualTo(-1)
                }
            }
        }

        assertThat(listOfNotNull(failure.cause) + failure.suppressed)
            .containsExactly(firstFailure, secondFailure)
    }

    @Test fun `close unblocks a blocked handler`() {
        val handlerStarted = CountDownLatch(1)
        val handlerStopped = CountDownLatch(1)
        LoopbackServer { socket, _ ->
            handlerStarted.countDown()
            try {
                socket.inputStream.read()
            } catch (failure: SocketException) {
                if (!socket.isClosed) throw failure
            } finally {
                handlerStopped.countDown()
            }
        }.use { server ->
            loopbackClient(server.port).use {
                assertThat(handlerStarted.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
                assertThat(handlerStopped.count).isEqualTo(1)

                server.close()

                assertThat(handlerStopped.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
            }
        }
    }

    @Test fun `close can be called repeatedly`() {
        LoopbackServer { _, _ -> }.use { server ->
            loopbackClient(server.port).use { client ->
                assertThat(client.inputStream.read()).isEqualTo(-1)
            }

            server.close()
            server.close()
        }
    }

    private fun assertLoopbackServerFailure(block: () -> Unit): AssertionError {
        val failure = assertThrows(AssertionError::class.java) { block() }
        if (failure.message != SERVER_FAILURE_MESSAGE) throw failure
        return failure
    }

    private fun loopbackClient(port: Int): Socket {
        val client = Socket()
        try {
            client.connect(InetSocketAddress(LOOPBACK_HOST, port), TEST_TIMEOUT_MS.toInt())
            client.soTimeout = TEST_TIMEOUT_MS.toInt()
            return client
        } catch (failure: Throwable) {
            try {
                client.close()
            } catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

    private companion object {
        const val SERVER_FAILURE_MESSAGE = "Loopback server failed"
        const val TEST_BYTE = 0x2A
        const val TEST_TIMEOUT_MS = 1_000L
    }
}

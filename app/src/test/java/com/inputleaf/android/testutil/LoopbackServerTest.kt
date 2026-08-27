package com.inputleaf.android.testutil

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
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

    @Test fun `reports connections beyond the expected count`() {
        val expectedConnectionAccepted = CountDownLatch(1)
        val releaseExpectedConnection = CountDownLatch(1)
        val server = LoopbackServer { _, _ ->
            expectedConnectionAccepted.countDown()
            releaseExpectedConnection.await()
        }

        try {
            Socket(LOOPBACK_HOST, server.port).use {
                assertThat(expectedConnectionAccepted.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue()
                Socket(LOOPBACK_HOST, server.port).use { unexpected ->
                    unexpected.soTimeout = TEST_TIMEOUT_MS.toInt()
                    assertThat(unexpected.inputStream.read()).isEqualTo(-1)
                }
            }
            releaseExpectedConnection.countDown()

            val failure = assertThrows(AssertionError::class.java) { server.close() }
            assertThat(failure.cause).hasMessageThat()
                .contains("Unexpected connection after 1 expected connections")
        } finally {
            releaseExpectedConnection.countDown()
            runCatching { server.close() }
        }
    }

    @Test fun `reports every handler failure`() {
        val firstFailure = IllegalStateException("first handler failed")
        val secondFailure = IllegalArgumentException("second handler failed")
        val server = LoopbackServer(connectionCount = 2) { _, index ->
            throw if (index == 0) firstFailure else secondFailure
        }

        try {
            Socket(LOOPBACK_HOST, server.port).use { first ->
                first.soTimeout = TEST_TIMEOUT_MS.toInt()
                assertThat(first.inputStream.read()).isEqualTo(-1)
            }
            Socket(LOOPBACK_HOST, server.port).use { second ->
                second.soTimeout = TEST_TIMEOUT_MS.toInt()
                assertThat(second.inputStream.read()).isEqualTo(-1)
            }

            val failure = assertThrows(AssertionError::class.java) { server.close() }
            assertThat(failure.cause).isSameInstanceAs(firstFailure)
            assertThat(failure.suppressed.asList()).containsExactly(secondFailure)
        } finally {
            runCatching { server.close() }
        }
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 1_000L
    }
}

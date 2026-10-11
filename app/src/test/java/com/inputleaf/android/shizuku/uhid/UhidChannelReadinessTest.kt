package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Readiness is driven through [UhidReadinessConfig]'s injectable `sleeper`/`nanoTime`
 * rather than wall-clock timing, so no assertion depends on how fast the machine is.
 * The kernel event is written into the pipe before createDevice instead of from a
 * feeder thread that sleeps, which is what previously made these races.
 */
@RunWith(RobolectricTestRunner::class)
class UhidChannelReadinessTest {

    /** Virtual clock: `sleeper` advances it, and yields briefly so the reader thread runs. */
    private class TestClock {
        var nowNanos = 0L
            private set

        fun nanoTime(): Long = nowNanos

        fun sleep(ms: Long) {
            nowNanos += TimeUnit.MILLISECONDS.toNanos(ms)
            Thread.sleep(1) // let the real reader thread drain the pipe
        }

        fun elapsedMs(): Long = TimeUnit.NANOSECONDS.toMillis(nowNanos)
    }

    @Test
    fun `OPEN completes readiness without burning the whole timeout`() {
        val clock = TestClock()
        val kernel = PipedOutputStream()
        val input = PipedInputStream(kernel, 8192)
        kernel.write(typeWord(UhidProtocol.UHID_OPEN))
        kernel.flush()

        val channel = UhidChannel.forTesting(
            ByteArrayOutputStream(),
            input,
            UhidReadinessConfig(
                openTimeoutMs = 400,
                presenceTimeoutMs = 150,
                presence = { _, _ -> true },
                sleeper = clock::sleep,
                nanoTime = clock::nanoTime,
            ),
        )

        channel.createDevice("Input Leaf Keyboard HID", byteArrayOf(1), uniq = "inputleaf-kbd")

        assertThat(clock.elapsedMs()).isLessThan(400)
        channel.close()
        kernel.close()
    }

    @Test
    fun `presence probe runs once OPEN arrives`() {
        val clock = TestClock()
        val kernel = PipedOutputStream()
        val input = PipedInputStream(kernel, 8192)
        kernel.write(typeWord(UhidProtocol.UHID_OPEN))
        kernel.flush()

        var presenceChecks = 0
        val channel = UhidChannel.forTesting(
            ByteArrayOutputStream(),
            input,
            UhidReadinessConfig(
                openTimeoutMs = 400,
                presenceTimeoutMs = 400,
                presence = { name, uniq ->
                    presenceChecks++
                    name == "mouse" && uniq == "inputleaf-mouse"
                },
                sleeper = clock::sleep,
                nanoTime = clock::nanoTime,
            ),
        )

        channel.createDevice("mouse", byteArrayOf(1), uniq = "inputleaf-mouse")

        assertThat(presenceChecks).isGreaterThan(0)
        assertThat(clock.elapsedMs()).isLessThan(800)
        channel.close()
        kernel.close()
    }

    @Test
    fun `neither START nor OPEN degrades within the bounded window instead of hanging`() {
        val clock = TestClock()
        val kernel = PipedOutputStream()
        val input = PipedInputStream(kernel, 8192)
        var presenceChecks = 0

        val channel = UhidChannel.forTesting(
            ByteArrayOutputStream(),
            input,
            UhidReadinessConfig(
                openTimeoutMs = 300,
                presenceTimeoutMs = 150,
                presence = { _, _ -> presenceChecks++; true },
                sleeper = clock::sleep,
                nanoTime = clock::nanoTime,
            ),
        )

        channel.createDevice("mouse", byteArrayOf(1), uniq = "inputleaf-mouse")

        // Gave up on START/OPEN and never reached the presence stage.
        assertThat(clock.elapsedMs()).isAtLeast(300)
        assertThat(presenceChecks).isEqualTo(0)
        channel.close()
        kernel.close()
    }

    @Test
    fun `START completes readiness without burning the whole timeout`() {
        val clock = TestClock()
        val kernel = PipedOutputStream()
        val input = PipedInputStream(kernel, 8192)
        kernel.write(typeWord(UhidProtocol.UHID_START))
        kernel.write(ByteArray(UhidProtocol.payloadSize(UhidProtocol.UHID_START)))
        kernel.flush()
        var presenceChecks = 0

        val channel = UhidChannel.forTesting(
            ByteArrayOutputStream(),
            input,
            UhidReadinessConfig(
                openTimeoutMs = 300,
                presenceTimeoutMs = 150,
                presence = { _, _ -> presenceChecks++; true },
                sleeper = clock::sleep,
                nanoTime = clock::nanoTime,
            ),
        )

        channel.createDevice("mouse", byteArrayOf(1), uniq = "inputleaf-mouse")

        assertThat(clock.elapsedMs()).isLessThan(300)
        assertThat(presenceChecks).isGreaterThan(0)
        channel.close()
        kernel.close()
    }

    @Test
    fun `stopReader terminates reader thread promptly without leaking thread`() {
        val kernel = PipedOutputStream()
        val input = PipedInputStream(kernel, 8192)
        val channel = UhidChannel.forTesting(
            ByteArrayOutputStream(),
            input,
            UhidReadinessConfig(
                openTimeoutMs = 50,
                presenceTimeoutMs = 50,
            ),
        )

        channel.createDevice("test", byteArrayOf(1))

        val readerThreads = Thread.getAllStackTraces().keys.filter { it.name == "uhid-reader" }
        for (thread in readerThreads) {
            assertThat(thread.isAlive).isFalse()
        }
        channel.close()
        kernel.close()
    }

    private fun typeWord(type: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(type).array()
}

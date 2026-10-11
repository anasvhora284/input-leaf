package com.inputleaf.android.shizuku.uhid

import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.system.StructPollfd
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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

    @Test
    fun `poll with POLLIN reads START and finishes readiness`() {
        val clock = TestClock()
        val kernel = PipedOutputStream()
        val input = PipedInputStream(kernel, 8192)
        kernel.write(typeWord(UhidProtocol.UHID_START))
        kernel.write(ByteArray(UhidProtocol.payloadSize(UhidProtocol.UHID_START)))
        kernel.flush()
        val polls = AtomicInteger()
        val pipe = ParcelFileDescriptor.createPipe()

        val channel = UhidChannel.forTesting(
            ByteArrayOutputStream(),
            input,
            UhidReadinessConfig(
                openTimeoutMs = 300,
                presenceTimeoutMs = 50,
                presence = { _, _ -> true },
                sleeper = clock::sleep,
                nanoTime = clock::nanoTime,
            ),
            readPfd = pipe[0],
            poll = { fds, _ ->
                polls.incrementAndGet()
                fds[0].revents = OsConstants.POLLIN.toShort()
                1
            },
        )

        channel.createDevice("mouse", byteArrayOf(1), uniq = "inputleaf-mouse")

        assertThat(polls.get()).isGreaterThan(0)
        assertThat(clock.elapsedMs()).isLessThan(300)
        channel.close()
        pipe[1].close()
        kernel.close()
    }

    @Test
    fun `poll timeout keeps the reader looping until START arrives`() {
        val clock = TestClock()
        val kernel = PipedOutputStream()
        val input = PipedInputStream(kernel, 8192)
        kernel.write(typeWord(UhidProtocol.UHID_START))
        kernel.write(ByteArray(UhidProtocol.payloadSize(UhidProtocol.UHID_START)))
        kernel.flush()
        val polls = AtomicInteger()
        val pipe = ParcelFileDescriptor.createPipe()

        val channel = UhidChannel.forTesting(
            ByteArrayOutputStream(),
            input,
            UhidReadinessConfig(
                openTimeoutMs = 300,
                presenceTimeoutMs = 50,
                presence = { _, _ -> true },
                sleeper = clock::sleep,
                nanoTime = clock::nanoTime,
            ),
            readPfd = pipe[0],
            poll = { fds, _ ->
                if (polls.incrementAndGet() == 1) {
                    0
                } else {
                    fds[0].revents = OsConstants.POLLIN.toShort()
                    1
                }
            },
        )

        channel.createDevice("mouse", byteArrayOf(1), uniq = "inputleaf-mouse")

        assertThat(polls.get()).isGreaterThan(1)
        assertThat(clock.elapsedMs()).isLessThan(300)
        channel.close()
        pipe[1].close()
        kernel.close()
    }

    @Test
    fun `poll without POLLIN stops the reader and the open window expires`() {
        val clock = TestClock()
        val kernel = PipedOutputStream()
        val input = PipedInputStream(kernel, 8192)
        val polls = AtomicInteger()
        val pipe = ParcelFileDescriptor.createPipe()

        val channel = UhidChannel.forTesting(
            ByteArrayOutputStream(),
            input,
            UhidReadinessConfig(
                openTimeoutMs = 40,
                presenceTimeoutMs = 40,
                sleeper = clock::sleep,
                nanoTime = clock::nanoTime,
            ),
            readPfd = pipe[0],
            poll = { fds, _ ->
                polls.incrementAndGet()
                fds[0].revents = OsConstants.POLLERR.toShort()
                1
            },
        )

        channel.createDevice("mouse", byteArrayOf(1), uniq = "inputleaf-mouse")

        assertThat(polls.get()).isEqualTo(1)
        assertThat(clock.elapsedMs()).isAtLeast(40)
        channel.close()
        pipe[1].close()
        kernel.close()
    }

    @Test
    fun `LinkageError falls back to the stream and sleeps while it is empty`() {
        val clock = TestClock()
        val kernel = PipedOutputStream()
        val input = PipedInputStream(kernel, 8192)
        kernel.write(typeWord(UhidProtocol.UHID_START))
        kernel.write(ByteArray(UhidProtocol.payloadSize(UhidProtocol.UHID_START)))
        kernel.flush()
        val polls = AtomicInteger()
        val pipe = ParcelFileDescriptor.createPipe()
        var spins = 0

        val channel = UhidChannel.forTesting(
            ByteArrayOutputStream(),
            input,
            UhidReadinessConfig(
                openTimeoutMs = 80,
                presenceTimeoutMs = 20,
                presence = { _, _ -> true },
                sleeper = { ms ->
                    spins++
                    if (polls.get() < 2 && spins < 200) {
                        Thread.sleep(1)
                    } else {
                        clock.sleep(ms)
                    }
                },
                nanoTime = clock::nanoTime,
            ),
            readPfd = pipe[0],
            poll = { _, _ ->
                polls.incrementAndGet()
                throw LinkageError("Os.poll unavailable")
            },
        )

        channel.createDevice("mouse", byteArrayOf(1), uniq = "inputleaf-mouse")

        assertThat(polls.get()).isAtLeast(2)
        assertThat(clock.elapsedMs()).isLessThan(80)
        channel.close()
        pipe[1].close()
        kernel.close()
    }

    private fun typeWord(type: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(type).array()
}

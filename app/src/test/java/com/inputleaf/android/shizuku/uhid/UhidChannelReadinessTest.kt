package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UhidChannelReadinessTest {

    @Test
    fun `OPEN becomes ready without the old 1500ms wait`() {
        val kernel = PipedOutputStream()
        val input = PipedInputStream(kernel, 8192)
        val channel = UhidChannel.forTesting(
            ByteArrayOutputStream(),
            input,
            UhidReadinessConfig(
                openTimeoutMs = 400,
                presenceTimeoutMs = 150,
                presence = { _, _ -> true },
            ),
        )
        thread(isDaemon = true) {
            Thread.sleep(25)
            kernel.write(typeWord(UhidProtocol.UHID_OPEN))
            kernel.flush()
        }

        val startedAt = System.nanoTime()
        channel.createDevice("Input Leaf Keyboard HID", byteArrayOf(1), uniq = "inputleaf-kbd")
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertThat(elapsedMs).isLessThan(800)
        channel.close()
        kernel.close()
    }

    @Test
    fun `presence probe can complete readiness immediately after OPEN`() {
        val kernel = PipedOutputStream()
        val input = PipedInputStream(kernel, 8192)
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
            ),
        )
        thread(isDaemon = true) {
            Thread.sleep(20)
            kernel.write(typeWord(UhidProtocol.UHID_OPEN))
            kernel.flush()
        }

        val startedAt = System.nanoTime()
        channel.createDevice("mouse", byteArrayOf(1), uniq = "inputleaf-mouse")
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertThat(presenceChecks).isGreaterThan(0)
        assertThat(elapsedMs).isLessThan(500)
        channel.close()
        kernel.close()
    }

    private fun typeWord(type: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(type).array()
}

package com.inputleaf.android.shizuku

import android.os.Build
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.shizuku.uhid.HidMouse
import com.inputleaf.android.shizuku.uhid.UhidChannel
import com.inputleaf.android.shizuku.uhid.UhidProtocol
import com.inputleaf.android.shizuku.uhid.UhidReadinessConfig
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class InputInjectorServiceEnterTest {

    @Test
    fun `enter then attach warps on the injector that owns the fd after OPEN`() {
        val kernel = PipedOutputStream()
        val input = PipedInputStream(kernel, 8192)
        val output = ByteArrayOutputStream()
        val channel = UhidChannel.forTesting(
            output,
            input,
            UhidReadinessConfig(
                openTimeoutMs = 400,
                presenceTimeoutMs = 10,
                presence = { _, _ -> true },
            ),
        )
        val service = InputInjectorService { channel }

        service.onHidMouseEnter(0, 100, 1079, 2413, 0)
        assertThat(service.injectHidMouse(1, 0, 0, 0)).isFalse()

        thread(isDaemon = true) {
            Thread.sleep(25)
            kernel.write(typeWord(UhidProtocol.UHID_OPEN))
            kernel.flush()
        }

        val startedAt = System.nanoTime()
        assertThat(service.openVirtualMouse()).isTrue()
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        assertThat(elapsedMs).isLessThan(800)

        val packet = output.toByteArray()
        assertThat(typeAt(packet, 0)).isEqualTo(UhidProtocol.UHID_CREATE2)
        val input2Offsets = input2Offsets(packet)
        assertThat(input2Offsets).isNotEmpty()
        assertThat(input2Offsets.first()).isGreaterThan(0)
        assertThat(input2Offsets.any { mouseDx(packet, it) == HidMouse.MIN_DELTA }).isTrue()

        service.closeVirtualMouse()
        kernel.close()
    }

    @Test
    fun `leave before attach drops enter so CREATE2 does not warp`() {
        val output = ByteArrayOutputStream()
        val channel = UhidChannel.forTesting(output)
        val service = InputInjectorService { channel }

        service.onHidMouseEnter(0, 0, 1079, 2413, 0)
        service.onHidMouseLeave()
        assertThat(service.openVirtualMouse()).isTrue()

        val packet = output.toByteArray()
        assertThat(typeAt(packet, 0)).isEqualTo(UhidProtocol.UHID_CREATE2)
        assertThat(input2Offsets(packet)).isEmpty()

        service.closeVirtualMouse()
    }

    @Test
    fun `compensation writes to the same channel that received CREATE2`() {
        val mouseOut = ByteArrayOutputStream()
        val otherOut = ByteArrayOutputStream()
        val mouseChannel = UhidChannel.forTesting(mouseOut)
        UhidChannel.forTesting(otherOut).use { other ->
            other.createDevice("other", byteArrayOf(1), uniq = "other")
        }
        val service = InputInjectorService { mouseChannel }

        service.onHidMouseEnter(1079, 2413, 1079, 2413, 0)
        assertThat(service.openVirtualMouse()).isTrue()

        assertThat(input2Offsets(otherOut.toByteArray())).isEmpty()
        val mousePacket = mouseOut.toByteArray()
        assertThat(typeAt(mousePacket, 0)).isEqualTo(UhidProtocol.UHID_CREATE2)
        val input2 = input2Offsets(mousePacket)
        assertThat(input2).isNotEmpty()
        assertThat(input2.any { mouseDx(mousePacket, it) == HidMouse.MAX_DELTA }).isTrue()

        service.closeVirtualMouse()
    }

    @Test
    fun `warm daemon reports no warp on an idempotent reopen so the client snaps itself`() {
        val output = ByteArrayOutputStream()
        val channel = UhidChannel.forTesting(output)
        val service = InputInjectorService { channel }

        service.onHidMouseEnter(1079, 2413, 1079, 2413, 0)
        assertThat(service.openVirtualMouse()).isTrue()
        // The create branch warped, so the client must not also snap.
        assertThat(service.consumeEnterWarpApplied()).isTrue()
        // Reading clears it; a second read is not a second warp.
        assertThat(service.consumeEnterWarpApplied()).isFalse()

        // A root daemon that survived an app rebind still has the mouse open. This
        // open emits no INPUT2, so the client has to send its own snap.
        val beforeReopen = output.toByteArray().size
        service.onHidMouseEnter(0, 0, 1079, 2413, 0)
        assertThat(service.openVirtualMouse()).isTrue()
        assertThat(output.toByteArray().size).isEqualTo(beforeReopen)
        assertThat(service.consumeEnterWarpApplied()).isFalse()

        service.closeVirtualMouse()
    }

    @Test
    fun `an open that warped nothing reports false`() {
        val output = ByteArrayOutputStream()
        val service = InputInjectorService { UhidChannel.forTesting(output) }

        assertThat(service.openVirtualMouse()).isTrue()
        assertThat(input2Offsets(output.toByteArray())).isEmpty()
        assertThat(service.consumeEnterWarpApplied()).isFalse()

        service.closeVirtualMouse()
    }

    private fun typeWord(type: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(type).array()

    private fun typeAt(packet: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(packet, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun input2Offsets(packet: ByteArray): List<Int> {
        val offsets = mutableListOf<Int>()
        var offset = 0
        while (offset + 4 <= packet.size) {
            when (typeAt(packet, offset)) {
                UhidProtocol.UHID_CREATE2 -> offset += UhidProtocol.CREATE2_PACKET_SIZE
                UhidProtocol.UHID_INPUT2 -> {
                    offsets += offset
                    offset += UhidProtocol.INPUT2_PACKET_SIZE
                }
                UhidProtocol.UHID_DESTROY -> offset += UhidProtocol.EVENT_PACKET_SIZE
                else -> offset += 4
            }
        }
        return offsets
    }

    private fun mouseDx(packet: ByteArray, input2At: Int): Int {
        val lo = packet[input2At + 6 + 1].toInt() and 0xFF
        val hi = packet[input2At + 6 + 2].toInt()
        return (hi shl 8) or lo
    }
}

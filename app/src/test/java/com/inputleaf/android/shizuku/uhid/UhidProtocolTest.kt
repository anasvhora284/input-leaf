package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test

class UhidProtocolTest {

    @Test
    fun `create2 packet matches the kernel struct layout`() {
        // The committed uhid-server wrote rd_size as a u32 at 132, which lands inside
        // phys[]. It must be a u16 at 260, after name[128] + phys[64] + uniq[64].
        assertThat(UhidProtocol.CREATE2_HEADER_SIZE).isEqualTo(276)
        assertThat(UhidProtocol.CREATE2_PACKET_SIZE).isEqualTo(4376)
        assertThat(UhidProtocol.INPUT2_PACKET_SIZE).isEqualTo(4102)
        assertThat(UhidProtocol.OFFSET_RD_SIZE).isEqualTo(260)
        assertThat(UhidProtocol.OFFSET_BUS).isEqualTo(262)
        assertThat(UhidProtocol.OFFSET_DESCRIPTOR).isEqualTo(280)
    }

    @Test
    fun `create2 writes type, name, descriptor size and bus`() {
        val descriptor = RelativePointer.DESCRIPTOR
        val packet = UhidProtocol.create2Packet("Input Leaf Pointer", descriptor, vendor = 0x1209, product = 1)
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)

        assertThat(packet.size).isEqualTo(UhidProtocol.CREATE2_PACKET_SIZE)
        assertThat(buffer.getInt(0)).isEqualTo(UhidProtocol.UHID_CREATE2)
        assertThat(String(packet, 4, "Input Leaf Pointer".length)).isEqualTo("Input Leaf Pointer")
        assertThat(buffer.getShort(UhidProtocol.OFFSET_RD_SIZE).toInt()).isEqualTo(descriptor.size)
        assertThat(buffer.getShort(UhidProtocol.OFFSET_BUS).toInt()).isEqualTo(UhidProtocol.BUS_USB)
        assertThat(buffer.getInt(UhidProtocol.OFFSET_BUS + 2)).isEqualTo(0x1209)
    }

    @Test
    fun `create2 copies the descriptor to the right offset`() {
        val descriptor = byteArrayOf(1, 2, 3, 4, 5)
        val packet = UhidProtocol.create2Packet("d", descriptor)

        assertThat(packet.copyOfRange(280, 285).toList()).containsExactly(
            1.toByte(), 2.toByte(), 3.toByte(), 4.toByte(), 5.toByte(),
        ).inOrder()
    }

    @Test
    fun `create2 truncates an over-long name and keeps it NUL terminated`() {
        val packet = UhidProtocol.create2Packet("x".repeat(400), byteArrayOf(1))
        assertThat(packet.size).isEqualTo(UhidProtocol.CREATE2_PACKET_SIZE)
        // name[128] occupies offsets 4..131, so the terminator is the last of those.
        assertThat(packet[130]).isEqualTo('x'.code.toByte())
        assertThat(packet[131]).isEqualTo(0.toByte())
    }

    @Test
    fun `create2 rejects an oversized descriptor`() {
        val tooBig = ByteArray(UhidProtocol.HID_MAX_DESCRIPTOR_SIZE + 1)
        runCatching { UhidProtocol.create2Packet("d", tooBig) }
            .onSuccess { error("expected a rejection") }
            .onFailure { assertThat(it).isInstanceOf(IllegalArgumentException::class.java) }
    }

    @Test
    fun `input2 writes type, size and payload`() {
        val report = byteArrayOf(0x01, 0x02, 0x03)
        val packet = UhidProtocol.input2Packet(report)
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)

        assertThat(packet.size).isEqualTo(UhidProtocol.INPUT2_PACKET_SIZE)
        assertThat(buffer.getInt(0)).isEqualTo(UhidProtocol.UHID_INPUT2)
        assertThat(buffer.getShort(4).toInt()).isEqualTo(3)
        assertThat(packet.copyOfRange(6, 9).toList())
            .containsExactly(0x01.toByte(), 0x02.toByte(), 0x03.toByte()).inOrder()
    }

    @Test
    fun `destroy packet is a bare type word`() {
        val packet = UhidProtocol.destroyPacket()
        assertThat(packet.size).isEqualTo(4)
        assertThat(ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getInt(0))
            .isEqualTo(UhidProtocol.UHID_DESTROY)
    }

    @Test
    fun `waitForStart recognises the START event`() {
        val stream = ByteArrayInputStream(typeWord(UhidProtocol.UHID_START))

        assertThat(UhidProtocol.waitForStart(stream, timeoutMs = 100)).isTrue()
    }

    @Test
    fun `waitForStart gives up when START never arrives`() {
        var clock = 0L
        val stream = ByteArrayInputStream(ByteArray(0))

        val started = UhidProtocol.waitForStart(stream, timeoutMs = 50) {
            clock += 20
            clock
        }

        assertThat(started).isFalse()
    }

    private fun typeWord(type: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(type).array()
}

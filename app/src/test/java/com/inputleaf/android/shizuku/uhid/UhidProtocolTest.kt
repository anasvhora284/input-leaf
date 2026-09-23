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
        val descriptor = HidKeyboard.DESCRIPTOR
        val packet = UhidProtocol.create2Packet(
            "Input Leaf Keyboard HID",
            descriptor,
            vendor = 0x1209,
            product = 1,
            uniq = "inputleaf-kbd",
        )
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)

        assertThat(packet.size).isEqualTo(UhidProtocol.CREATE2_PACKET_SIZE)
        assertThat(buffer.getInt(0)).isEqualTo(UhidProtocol.UHID_CREATE2)
        assertThat(String(packet, 4, "Input Leaf Keyboard HID".length)).isEqualTo("Input Leaf Keyboard HID")
        assertThat(String(packet, UhidProtocol.OFFSET_UNIQ, "inputleaf-kbd".length)).isEqualTo("inputleaf-kbd")
        assertThat(buffer.getShort(UhidProtocol.OFFSET_RD_SIZE).toInt()).isEqualTo(descriptor.size)
        assertThat(buffer.getShort(UhidProtocol.OFFSET_BUS).toInt()).isEqualTo(UhidProtocol.BUS_USB)
        assertThat(buffer.getInt(UhidProtocol.OFFSET_BUS + 2)).isEqualTo(0x1209)
        assertThat(buffer.getInt(UhidProtocol.OFFSET_BUS + 6)).isEqualTo(1)
    }

    @Test
    fun `keyboard and mouse uniq strings differ`() {
        val keyboard = UhidProtocol.create2Packet("kb", byteArrayOf(1), vendor = 0x1209, product = 1, uniq = "inputleaf-kbd")
        val mouse = UhidProtocol.create2Packet("ms", byteArrayOf(2), vendor = 0x1209, product = 2, uniq = "inputleaf-mouse")
        val kbUniq = String(keyboard, UhidProtocol.OFFSET_UNIQ, "inputleaf-kbd".length)
        val msUniq = String(mouse, UhidProtocol.OFFSET_UNIQ, "inputleaf-mouse".length)
        assertThat(kbUniq).isNotEqualTo(msUniq)
    }

    @Test
    fun `event types match the kernel uhid ABI`() {
        // Literal numbers on purpose: asserting against the constants would only prove
        // the file agrees with itself, which is how START=4 / OPEN=6 survived before.
        // Source: enum uhid_event_type, uapi/linux/uhid.h.
        assertThat(UhidProtocol.UHID_DESTROY).isEqualTo(1)
        assertThat(UhidProtocol.UHID_START).isEqualTo(2)
        assertThat(UhidProtocol.UHID_STOP).isEqualTo(3)
        assertThat(UhidProtocol.UHID_OPEN).isEqualTo(4)
        assertThat(UhidProtocol.UHID_CLOSE).isEqualTo(5)
        assertThat(UhidProtocol.UHID_OUTPUT).isEqualTo(6)
        assertThat(UhidProtocol.UHID_GET_REPORT).isEqualTo(9)
        assertThat(UhidProtocol.UHID_CREATE2).isEqualTo(11)
        assertThat(UhidProtocol.UHID_INPUT2).isEqualTo(12)
        assertThat(UhidProtocol.UHID_SET_REPORT).isEqualTo(13)
    }

    @Test
    fun `payload sizes match the packed structs in uhid_h`() {
        assertThat(UhidProtocol.payloadSize(UhidProtocol.UHID_START)).isEqualTo(8)
        assertThat(UhidProtocol.payloadSize(UhidProtocol.UHID_STOP)).isEqualTo(0)
        assertThat(UhidProtocol.payloadSize(UhidProtocol.UHID_OPEN)).isEqualTo(0)
        assertThat(UhidProtocol.payloadSize(UhidProtocol.UHID_CLOSE)).isEqualTo(0)
        // The regression that mattered: OUTPUT was treated as zero-payload, so draining
        // one would leave 4099 bytes in the stream and misframe every later read.
        assertThat(UhidProtocol.payloadSize(UhidProtocol.UHID_OUTPUT)).isEqualTo(4099)
        assertThat(UhidProtocol.payloadSize(UhidProtocol.UHID_GET_REPORT)).isEqualTo(6)
        assertThat(UhidProtocol.payloadSize(UhidProtocol.UHID_INPUT2)).isEqualTo(4098)
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
    fun `destroy packet is a full event with DESTROY type`() {
        val packet = UhidProtocol.destroyPacket()
        assertThat(packet.size).isEqualTo(UhidProtocol.EVENT_PACKET_SIZE)
        assertThat(ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getInt(0))
            .isEqualTo(UhidProtocol.UHID_DESTROY)
        assertThat(packet.drop(4).all { it == 0.toByte() }).isTrue()
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

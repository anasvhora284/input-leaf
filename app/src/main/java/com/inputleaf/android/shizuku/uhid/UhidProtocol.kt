package com.inputleaf.android.shizuku.uhid

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Packet layout for `/dev/uhid`, from Linux `uhid.h`.
 *
 * Unlike uinput, uhid needs no `ioctl` at all — every operation is a plain `write` of a
 * fixed-size `uhid_event`. That is why this transport carries no hidden-API risk.
 *
 * The field that is easy to get wrong: `rd_size` is a **u16 at offset 260**, after
 * `name[128]`, `phys[64]` and `uniq[64]`. Writing it as a u32 at 132 lands inside
 * `phys[]` and the kernel rejects or misparses the device.
 */
internal object UhidProtocol {

    const val UHID_DESTROY = 1
    const val UHID_START = 4
    const val UHID_CREATE2 = 11
    const val UHID_INPUT2 = 12

    const val HID_MAX_DESCRIPTOR_SIZE = 4096
    const val UHID_DATA_MAX = 4096

    private const val NAME_SIZE = 128
    private const val PHYS_SIZE = 64
    private const val UNIQ_SIZE = 64

    /** name + phys + uniq + rd_size + bus + vendor + product + version + country */
    const val CREATE2_HEADER_SIZE =
        NAME_SIZE + PHYS_SIZE + UNIQ_SIZE + 2 + 2 + 4 + 4 + 4 + 4

    const val CREATE2_PACKET_SIZE = 4 + CREATE2_HEADER_SIZE + HID_MAX_DESCRIPTOR_SIZE
    const val INPUT2_PACKET_SIZE = 4 + 2 + UHID_DATA_MAX

    const val OFFSET_RD_SIZE = 4 + NAME_SIZE + PHYS_SIZE + UNIQ_SIZE
    const val OFFSET_BUS = OFFSET_RD_SIZE + 2
    const val OFFSET_DESCRIPTOR = 4 + CREATE2_HEADER_SIZE

    const val BUS_USB = 0x0003

    fun create2Packet(
        name: String,
        descriptor: ByteArray,
        vendor: Int = 0,
        product: Int = 0,
    ): ByteArray {
        require(descriptor.size <= HID_MAX_DESCRIPTOR_SIZE) {
            "Descriptor too large: ${descriptor.size}"
        }
        val packet = ByteBuffer.allocate(CREATE2_PACKET_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        packet.putInt(0, UHID_CREATE2)

        val nameBytes = name.toByteArray(Charsets.UTF_8)
        // Leave room for the trailing NUL inside name[128].
        System.arraycopy(nameBytes, 0, packet.array(), 4, minOf(nameBytes.size, NAME_SIZE - 1))

        packet.putShort(OFFSET_RD_SIZE, descriptor.size.toShort())
        packet.putShort(OFFSET_BUS, BUS_USB.toShort())
        packet.putInt(OFFSET_BUS + 2, vendor)
        packet.putInt(OFFSET_BUS + 6, product)

        System.arraycopy(descriptor, 0, packet.array(), OFFSET_DESCRIPTOR, descriptor.size)
        return packet.array()
    }

    fun input2Packet(report: ByteArray): ByteArray {
        require(report.size <= UHID_DATA_MAX) { "Report too large: ${report.size}" }
        val packet = ByteBuffer.allocate(INPUT2_PACKET_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        packet.putInt(0, UHID_INPUT2)
        packet.putShort(4, report.size.toShort())
        System.arraycopy(report, 0, packet.array(), 6, report.size)
        return packet.array()
    }

    fun destroyPacket(): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(UHID_DESTROY).array()

    /**
     * Waits briefly for [UHID_START], draining any other kernel event that arrives first.
     * Returns whether START was seen; the caller proceeds either way, since some kernels
     * deliver it late and the device still works.
     */
    fun waitForStart(input: InputStream, timeoutMs: Long, now: () -> Long = System::currentTimeMillis): Boolean {
        val deadline = now() + timeoutMs
        val header = ByteArray(4)
        var offset = 0
        while (now() < deadline) {
            if (input.available() <= 0) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
                continue
            }
            val read = input.read(header, offset, 4 - offset)
            if (read < 0) return false
            offset += read
            if (offset < 4) continue

            val type = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
            if (type == UHID_START) return true
            drainPayload(input, type)
            offset = 0
        }
        return false
    }

    private fun drainPayload(input: InputStream, type: Int) {
        var remaining = payloadSize(type)
        while (remaining > 0) {
            val skipped = input.skip(remaining.toLong())
            if (skipped <= 0) return
            remaining -= skipped.toInt()
        }
    }

    private fun payloadSize(type: Int): Int = when (type) {
        UHID_START, 5, 6, 7 -> 0
        UHID_INPUT2 -> 2 + UHID_DATA_MAX
        UHID_CREATE2 -> CREATE2_HEADER_SIZE + HID_MAX_DESCRIPTOR_SIZE
        else -> UHID_DATA_MAX
    }

    private const val POLL_INTERVAL_MS = 10L
}

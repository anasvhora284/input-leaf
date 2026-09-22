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
 *
 * The event-type constants below are the kernel's, and the tests assert the literal
 * numbers so the ABI is guarded rather than merely self-consistent. They previously
 * read START=4 and OPEN=6, which are really the kernel's OPEN and OUTPUT: the code
 * happened to gate on the right practical signal under the wrong name, while the
 * "OPEN" branch waited on a host→device report that never arrives.
 */
internal object UhidProtocol {

    // enum uhid_event_type, in declaration order from uapi/linux/uhid.h. These are wire
    // values: do not renumber them to suit the code.
    const val UHID_DESTROY = 1

    /** hid-core created the device. Nothing is necessarily listening yet. */
    const val UHID_START = 2
    const val UHID_STOP = 3

    /**
     * A consumer (Android's EventHub) opened the evdev node — safe to send the first
     * input report. This, not [UHID_START], is the readiness gate: START fires before
     * EventHub attaches, so writing INPUT2 on START races the very drop that the
     * readiness wait exists to prevent.
     */
    const val UHID_OPEN = 4
    const val UHID_CLOSE = 5

    /** Device→host report. Carries `data[4096] + u16 size + u8 rtype`. */
    const val UHID_OUTPUT = 6
    const val UHID_GET_REPORT = 9
    const val UHID_CREATE2 = 11
    const val UHID_INPUT2 = 12
    const val UHID_SET_REPORT = 13

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
    /** Largest possible `struct uhid_event`, used for reads from `/dev/uhid`. */
    const val EVENT_PACKET_SIZE = CREATE2_PACKET_SIZE

    const val OFFSET_PHYS = 4 + NAME_SIZE
    const val OFFSET_UNIQ = OFFSET_PHYS + PHYS_SIZE
    const val OFFSET_RD_SIZE = OFFSET_UNIQ + UNIQ_SIZE
    const val OFFSET_BUS = OFFSET_RD_SIZE + 2
    const val OFFSET_DESCRIPTOR = 4 + CREATE2_HEADER_SIZE

    const val BUS_USB = 0x0003

    fun create2Packet(
        name: String,
        descriptor: ByteArray,
        vendor: Int = 0,
        product: Int = 0,
        uniq: String = "",
    ): ByteArray {
        require(descriptor.size <= HID_MAX_DESCRIPTOR_SIZE) {
            "Descriptor too large: ${descriptor.size}"
        }
        val packet = ByteBuffer.allocate(CREATE2_PACKET_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        packet.putInt(0, UHID_CREATE2)

        writeFixedString(packet.array(), 4, NAME_SIZE, name)
        writeFixedString(packet.array(), OFFSET_PHYS, PHYS_SIZE, "")
        writeFixedString(packet.array(), OFFSET_UNIQ, UNIQ_SIZE, uniq)

        packet.putShort(OFFSET_RD_SIZE, descriptor.size.toShort())
        packet.putShort(OFFSET_BUS, BUS_USB.toShort())
        packet.putInt(OFFSET_BUS + 2, vendor)
        packet.putInt(OFFSET_BUS + 6, product)

        System.arraycopy(descriptor, 0, packet.array(), OFFSET_DESCRIPTOR, descriptor.size)
        return packet.array()
    }

    private fun writeFixedString(array: ByteArray, offset: Int, size: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        System.arraycopy(bytes, 0, array, offset, minOf(bytes.size, size - 1))
    }

    /**
     * Writes an INPUT2 event into [dest], which must be at least [INPUT2_PACKET_SIZE].
     * Reusing [dest] avoids a 4KB allocation on every mouse report in the injector.
     */
    fun writeInput2Into(dest: ByteArray, report: ByteArray) {
        require(dest.size >= INPUT2_PACKET_SIZE) { "INPUT2 dest too small: ${dest.size}" }
        require(report.size <= UHID_DATA_MAX) { "Report too large: ${report.size}" }
        val buffer = ByteBuffer.wrap(dest).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0, UHID_INPUT2)
        buffer.putShort(4, report.size.toShort())
        System.arraycopy(report, 0, dest, 6, report.size)
    }

    fun input2Packet(report: ByteArray): ByteArray {
        val packet = ByteArray(INPUT2_PACKET_SIZE)
        writeInput2Into(packet, report)
        return packet
    }

    /**
     * Full `struct uhid_event` with [UHID_DESTROY] in the type word. A 4-byte write is
     * legal on AOSP, but ColorOS InputReader has been observed to keep evdev nodes if
     * the write is shorter than the event union.
     */
    fun destroyPacket(): ByteArray {
        val packet = ByteArray(EVENT_PACKET_SIZE)
        ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).putInt(0, UHID_DESTROY)
        return packet
    }

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

    /**
     * Payload bytes following the u32 type word, from the packed structs in `uhid.h`.
     * Used to step over an event whose body we do not care about, so a wrong size here
     * desynchronises every later read on the stream.
     */
    internal fun payloadSize(type: Int): Int = when (type) {
        UHID_START -> 8 // struct uhid_start_req { u64 dev_flags }
        UHID_STOP, UHID_OPEN, UHID_CLOSE -> 0
        UHID_OUTPUT -> UHID_DATA_MAX + 2 + 1 // data[4096] + u16 size + u8 rtype
        UHID_GET_REPORT -> 6 // u32 id + u8 rnum + u8 rtype
        UHID_SET_REPORT -> 4 + 1 + 1 + 2 + UHID_DATA_MAX
        UHID_INPUT2 -> 2 + UHID_DATA_MAX
        UHID_CREATE2 -> CREATE2_HEADER_SIZE + HID_MAX_DESCRIPTOR_SIZE
        else -> UHID_DATA_MAX
    }

    private const val POLL_INTERVAL_MS = 10L
}

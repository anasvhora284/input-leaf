package com.inputleaf.android.protocol

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.InputLeapEvent
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class ProtocolParserTest {
    private fun frameOf(tag: String, payload: ByteArray = byteArrayOf()): ByteArray =
        frameOfBody(tag.toByteArray(Charsets.US_ASCII) + payload)

    private fun frameOfBody(body: ByteArray): ByteArray {
        val length = body.size
        return byteArrayOf(
            (length shr 24).toByte(), (length shr 16).toByte(),
            (length shr 8).toByte(), length.toByte()
        ) + body
    }

    private fun u16(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())
    private fun u32(value: Int) = byteArrayOf(
        (value shr 24).toByte(), (value shr 16).toByte(),
        (value shr 8).toByte(), value.toByte()
    )
    private fun parse(tag: String, payload: ByteArray = byteArrayOf()) =
        ProtocolParser(ByteArrayInputStream(frameOf(tag, payload))).readNext()

    @Test fun `parses HELO with a Unicode server name`() {
        val name = "桌面-pc"
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        assertThat(parse("HELO", u16(1) + u16(6) + u32(nameBytes.size) + nameBytes))
            .isEqualTo(InputLeapEvent.Hello(1, 6, name))
    }

    @Test fun `parses Barrier and Synergy hello variants with and without a name`() {
        for (tag in listOf("Barrier", "Synergy")) {
            val prefix = tag.toByteArray(Charsets.US_ASCII) + u16(1) + u16(8)
            assertThat(ProtocolParser(ByteArrayInputStream(frameOfBody(prefix))).readNext())
                .isEqualTo(InputLeapEvent.Hello(1, 8, ""))

            val name = "server"
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val namedHello = prefix + u32(nameBytes.size) + nameBytes
            assertThat(ProtocolParser(ByteArrayInputStream(frameOfBody(namedHello))).readNext())
                .isEqualTo(InputLeapEvent.Hello(1, 8, name))
        }
    }

    @Test fun `rejects malformed UTF-8 in hello names`() {
        val invalidUtf8 = byteArrayOf(0xC3.toByte())
        assertThrows(IllegalArgumentException::class.java) {
            parse("HELO", u16(1) + u16(6) + u32(invalidUtf8.size) + invalidUtf8)
        }

        for (tag in listOf("Barrier", "Synergy")) {
            val body = tag.toByteArray(Charsets.US_ASCII) + u16(1) + u16(8) +
                u32(invalidUtf8.size) + invalidUtf8
            assertThrows(IllegalArgumentException::class.java) {
                ProtocolParser(ByteArrayInputStream(frameOfBody(body))).readNext()
            }
        }
    }

    @Test fun `parses control and keyboard messages`() {
        assertThat(parse("QINF")).isEqualTo(InputLeapEvent.QueryInfo())
        assertThat(parse("CINN", u16(1) + u16(2) + u32(-1) + u16(3)))
            .isEqualTo(InputLeapEvent.Enter(1, 2, -1, 3))
        assertThat(parse("COUT")).isEqualTo(InputLeapEvent.Leave)
        assertThat(parse("CALV")).isEqualTo(InputLeapEvent.KeepAlive)
        assertThat(parse("CROP")).isEqualTo(InputLeapEvent.ResetOptions)
        assertThat(parse("DKDN", u16(65) + u16(2) + u16(30)))
            .isEqualTo(InputLeapEvent.KeyDown(65, 2, 30))
        assertThat(parse("DKUP", u16(65) + u16(2) + u16(30)))
            .isEqualTo(InputLeapEvent.KeyUp(65, 2, 30))
        assertThat(parse("DKRP", u16(65) + u16(2) + u16(4) + u16(30)))
            .isEqualTo(InputLeapEvent.KeyRepeat(65, 2, 4, 30))
        assertThat(parse("DKRP", u16(65) + u16(2) + u16(4)))
            .isEqualTo(InputLeapEvent.KeyRepeat(65, 2, 4, 0))
    }

    @Test fun `parses mouse messages with signed coordinates`() {
        assertThat(parse("DMMV", u16(65535) + u16(1)))
            .isEqualTo(InputLeapEvent.MouseMoveAbs(65535, 1))
        assertThat(parse("DMRM", u32(-1) + u32(Int.MIN_VALUE)))
            .isEqualTo(InputLeapEvent.MouseMoveRel(-1, Int.MIN_VALUE))
        assertThat(parse("DMDN", byteArrayOf(3))).isEqualTo(InputLeapEvent.MouseDown(3))
        assertThat(parse("DMUP", byteArrayOf(3))).isEqualTo(InputLeapEvent.MouseUp(3))
        assertThat(parse("DMWM", u16(-2) + u16(32767)))
            .isEqualTo(InputLeapEvent.MouseWheel(-2, 32767))
    }

    @Test fun `parses server error messages and unknown tags`() {
        assertThat(parse("EICV", u16(1) + u16(7))).isEqualTo(InputLeapEvent.Incompatible(1, 7))
        assertThat(parse("EBSY")).isEqualTo(InputLeapEvent.Busy)
        assertThat(parse("EUNK")).isEqualTo(InputLeapEvent.Unknown)
        assertThat(parse("EBAD")).isEqualTo(InputLeapEvent.BadMessage)
        assertThat(parse("ZZZZ")).isEqualTo(InputLeapEvent.Unhandled("ZZZZ"))
    }

    @Test fun `rejects trailing bytes in supported messages`() {
        val fixedMessages = listOf(
            "QINF" to byteArrayOf(),
            "CINN" to (u16(1) + u16(2) + u32(3) + u16(4)),
            "COUT" to byteArrayOf(),
            "CALV" to byteArrayOf(),
            "CROP" to byteArrayOf(),
            "DKDN" to (u16(65) + u16(2) + u16(30)),
            "DKUP" to (u16(65) + u16(2) + u16(30)),
            "DKRP" to (u16(65) + u16(2) + u16(4) + u16(30)),
            "DMMV" to (u16(1) + u16(2)),
            "DMRM" to (u32(1) + u32(2)),
            "DMDN" to byteArrayOf(1),
            "DMUP" to byteArrayOf(1),
            "DMWM" to (u16(1) + u16(2)),
            "EICV" to (u16(1) + u16(2)),
            "EBSY" to byteArrayOf(),
            "EUNK" to byteArrayOf(),
            "EBAD" to byteArrayOf()
        )
        for ((tag, payload) in fixedMessages) {
            assertThrows("$tag accepted trailing data", IllegalArgumentException::class.java) {
                parse(tag, payload + byteArrayOf(0))
            }
        }

        val helo = u16(1) + u16(6) + u32(0) + byteArrayOf(0)
        assertThrows(IllegalArgumentException::class.java) { parse("HELO", helo) }
        for (tag in listOf("Barrier", "Synergy")) {
            val body = tag.toByteArray(Charsets.US_ASCII) + u16(1) + u16(8) + u32(0) + byteArrayOf(0)
            assertThrows(IllegalArgumentException::class.java) {
                ProtocolParser(ByteArrayInputStream(frameOfBody(body))).readNext()
            }
        }
    }

    @Test fun `rejects truncated and malformed frames predictably`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProtocolParser(ByteArrayInputStream(byteArrayOf(0, 0))).readNext()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProtocolParser(ByteArrayInputStream(frameOf("DKDN", byteArrayOf(0)))).readNext()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProtocolParser(ByteArrayInputStream(frameOf("HELO", u16(1) + u16(6) + u32(2) + byteArrayOf(1)))).readNext()
        }
    }

    @Test fun `rejects invalid frame lengths`() {
        val oversized = u32(ProtocolConstants.MAX_MESSAGE_LEN + 1)
        assertThrows(IllegalArgumentException::class.java) {
            ProtocolParser(ByteArrayInputStream(oversized)).readNext()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProtocolParser(ByteArrayInputStream(u32(3) + byteArrayOf(1, 2, 3))).readNext()
        }
    }
}

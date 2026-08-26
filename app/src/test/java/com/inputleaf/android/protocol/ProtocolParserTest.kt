package com.inputleaf.android.protocol

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.model.WireProtocol
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.net.ProtocolException
import java.nio.charset.CharacterCodingException

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
        val name = "escritorio-áé-pc"
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        assertThat(parse("HELO", u16(1) + u16(6) + u32(nameBytes.size) + nameBytes))
            .isEqualTo(InputLeapEvent.Hello(1, 6, name))
    }

    @Test fun `parses Barrier and Synergy hello variants with and without a name`() {
        for (protocol in WireProtocol.entries) {
            val prefix = protocol.magic.toByteArray(Charsets.US_ASCII) + u16(1) + u16(8)
            assertThat(ProtocolParser(ByteArrayInputStream(frameOfBody(prefix))).readNext())
                .isEqualTo(InputLeapEvent.Hello(1, 8, "", protocol))

            val name = "server"
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val namedHello = prefix + u32(nameBytes.size) + nameBytes
            assertThat(ProtocolParser(ByteArrayInputStream(frameOfBody(namedHello))).readNext())
                .isEqualTo(InputLeapEvent.Hello(1, 8, name, protocol))
        }
    }

    @Test fun `rejects malformed UTF-8 in hello names with useful diagnostics`() {
        val invalidUtf8 = byteArrayOf(0xC3.toByte())
        val taggedFailure = assertThrows(ProtocolException::class.java) {
            parse("HELO", u16(1) + u16(6) + u32(invalidUtf8.size) + invalidUtf8)
        }
        assertThat(taggedFailure).hasMessageThat().isEqualTo("Malformed UTF-8 in HELO message")
        assertThat(taggedFailure).hasCauseThat().isInstanceOf(CharacterCodingException::class.java)

        for (tag in listOf("Barrier", "Synergy")) {
            val body = tag.toByteArray(Charsets.US_ASCII) + u16(1) + u16(8) +
                u32(invalidUtf8.size) + invalidUtf8
            val bannerFailure = assertThrows(ProtocolException::class.java) {
                ProtocolParser(ByteArrayInputStream(frameOfBody(body))).readNext()
            }
            assertThat(bannerFailure).hasMessageThat()
                .isEqualTo("Malformed UTF-8 in $tag message")
            assertThat(bannerFailure).hasCauseThat()
                .isInstanceOf(CharacterCodingException::class.java)
        }
    }

    @Test fun `validates Barrier and Synergy hello structure boundaries`() {
        for (protocol in WireProtocol.entries) {
            val prefix = protocol.magic.toByteArray(Charsets.US_ASCII) + u16(1) + u16(8)

            val shortFailure = assertThrows(ProtocolException::class.java) {
                ProtocolParser(ByteArrayInputStream(frameOfBody(prefix.copyOf(10)))).readNext()
            }
            assertThat(shortFailure).hasMessageThat()
                .isEqualTo(
                    "Malformed ${protocol.magic} message: expected at least 11 body bytes, got 10"
                )

            val incompleteLength = assertThrows(ProtocolException::class.java) {
                ProtocolParser(ByteArrayInputStream(frameOfBody(prefix + byteArrayOf(0)))).readNext()
            }
            assertThat(incompleteLength).hasMessageThat()
                .isEqualTo(
                    "Malformed ${protocol.magic} message: expected 11 or at least 15 body bytes, got 12"
                )

            val explicitEmptyName = prefix + u32(0)
            assertThat(ProtocolParser(ByteArrayInputStream(frameOfBody(explicitEmptyName))).readNext())
                .isEqualTo(InputLeapEvent.Hello(1, 8, "", protocol))
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
        assertThat(parse("ZZZZ", byteArrayOf(1, 2)))
            .isEqualTo(InputLeapEvent.Unhandled("ZZZZ"))
    }

    @Test fun `reads sequential frames without crossing their boundaries`() {
        val stream = frameOf("QINF") + frameOf("DMDN", byteArrayOf(2)) +
            frameOf("ZZZZ", byteArrayOf(7, 8))
        val parser = ProtocolParser(ByteArrayInputStream(stream))

        assertThat(parser.readNext()).isEqualTo(InputLeapEvent.QueryInfo())
        assertThat(parser.readNext()).isEqualTo(InputLeapEvent.MouseDown(2))
        assertThat(parser.readNext()).isEqualTo(InputLeapEvent.Unhandled("ZZZZ"))
    }

    @Test fun `rejects non-printable protocol tags`() {
        for (invalidByte in listOf(0x80.toByte(), '\n'.code.toByte())) {
            val body = byteArrayOf(
                invalidByte, 'A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte()
            )

            val failure = assertThrows(ProtocolException::class.java) {
                ProtocolParser(ByteArrayInputStream(frameOfBody(body))).readNext()
            }

            assertThat(failure).hasMessageThat()
                .isEqualTo("Malformed protocol tag: expected four printable ASCII bytes")
        }
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
            assertThrows("$tag accepted trailing data", ProtocolException::class.java) {
                parse(tag, payload + byteArrayOf(0))
            }
        }

        val helo = u16(1) + u16(6) + u32(0) + byteArrayOf(0)
        assertThrows(ProtocolException::class.java) { parse("HELO", helo) }
        for (tag in listOf("Barrier", "Synergy")) {
            val body = tag.toByteArray(Charsets.US_ASCII) + u16(1) + u16(8) + u32(0) + byteArrayOf(0)
            assertThrows(ProtocolException::class.java) {
                ProtocolParser(ByteArrayInputStream(frameOfBody(body))).readNext()
            }
        }
    }

    @Test fun `rejects truncated and malformed frames predictably`() {
        val truncatedHeader = assertThrows(ProtocolException::class.java) {
            ProtocolParser(ByteArrayInputStream(byteArrayOf(0, 0))).readNext()
        }
        assertThat(truncatedHeader).hasMessageThat().isEqualTo("Truncated protocol message")
        assertThat(truncatedHeader).hasCauseThat().isInstanceOf(EOFException::class.java)

        val truncatedBody = assertThrows(ProtocolException::class.java) {
            ProtocolParser(ByteArrayInputStream(u32(8) + "QINF".toByteArray())).readNext()
        }
        assertThat(truncatedBody).hasMessageThat().isEqualTo("Truncated protocol message")
        assertThat(truncatedBody).hasCauseThat().isInstanceOf(EOFException::class.java)

        val shortKey = assertThrows(ProtocolException::class.java) {
            ProtocolParser(ByteArrayInputStream(frameOf("DKDN", byteArrayOf(0)))).readNext()
        }
        assertThat(shortKey).hasMessageThat()
            .isEqualTo("Malformed DKDN message: expected 6 payload bytes, got 1")

        val shortHello = assertThrows(ProtocolException::class.java) {
            ProtocolParser(ByteArrayInputStream(
                frameOf("HELO", u16(1) + u16(6) + u32(2) + byteArrayOf(1))
            )).readNext()
        }
        assertThat(shortHello).hasMessageThat()
            .isEqualTo("Malformed HELO message: expected 10 payload bytes, got 9")
    }

    @Test fun `treats name lengths as unsigned 32-bit values`() {
        val failure = assertThrows(ProtocolException::class.java) {
            parse("HELO", u16(1) + u16(6) + u32(-1))
        }

        assertThat(failure).hasMessageThat()
            .isEqualTo("Malformed HELO message: expected 4294967303 payload bytes, got 8")
    }

    @Test fun `rejects invalid frame lengths`() {
        val oversized = u32(ProtocolConstants.MAX_MESSAGE_LEN + 1)
        assertThrows(ProtocolException::class.java) {
            ProtocolParser(ByteArrayInputStream(oversized)).readNext()
        }
        assertThrows(ProtocolException::class.java) {
            ProtocolParser(ByteArrayInputStream(u32(3) + byteArrayOf(1, 2, 3))).readNext()
        }
    }
}

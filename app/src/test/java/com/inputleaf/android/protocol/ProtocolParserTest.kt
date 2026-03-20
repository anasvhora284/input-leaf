package com.inputleaf.android.protocol

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.InputLeapEvent
import org.junit.Test
import java.io.ByteArrayInputStream

class ProtocolParserTest {
    private fun frameOf(tag: String, vararg payload: Byte): ByteArray {
        val body = tag.toByteArray(Charsets.US_ASCII) + payload
        val len = body.size
        return byteArrayOf(
            (len shr 24).toByte(), (len shr 16).toByte(),
            (len shr 8).toByte(), len.toByte()
        ) + body
    }

    @Test fun `parses Hello message`() {
        val name = "work-pc"
        val nameBytes = name.toByteArray(Charsets.US_ASCII)
        val payload = byteArrayOf(0,1, 0,6) +
            byteArrayOf(0,0,0,nameBytes.size.toByte()) + nameBytes
        val frame = frameOf("HELO", *payload)
        val parser = ProtocolParser(ByteArrayInputStream(frame))
        val event = parser.readNext()
        assertThat(event).isEqualTo(InputLeapEvent.Hello(1, 6, "work-pc"))
    }

    @Test fun `parses KeepAlive message`() {
        val frame = frameOf("CALV")
        val parser = ProtocolParser(ByteArrayInputStream(frame))
        assertThat(parser.readNext()).isEqualTo(InputLeapEvent.KeepAlive)
    }

    @Test fun `parses KeyDown message`() {
        val payload = byteArrayOf(0,65, 0,0, 0,30)
        val frame = frameOf("DKDN", *payload)
        val parser = ProtocolParser(ByteArrayInputStream(frame))
        assertThat(parser.readNext()).isEqualTo(InputLeapEvent.KeyDown(65, 0, 30))
    }

    @Test fun `parses MouseMoveRel`() {
        val payload = byteArrayOf(0,0,0,5, 0,0,0,(-3).toByte())
        val frame = frameOf("DMRM", *payload)
        val parser = ProtocolParser(ByteArrayInputStream(frame))
        assertThat(parser.readNext()).isEqualTo(InputLeapEvent.MouseMoveRel(5, -3))
    }

    @Test fun `parses QueryInfo message`() {
        val frame = frameOf("QINF")
        val parser = ProtocolParser(ByteArrayInputStream(frame))
        assertThat(parser.readNext()).isInstanceOf(InputLeapEvent.QueryInfo::class.java)
    }

    @Test fun `skips unknown message type`() {
        val frame = frameOf("ZZZZ")
        val parser = ProtocolParser(ByteArrayInputStream(frame))
        assertThat(parser.readNext()).isEqualTo(InputLeapEvent.Unhandled("ZZZZ"))
    }
}

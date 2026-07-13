package com.inputleaf.android.root

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlFrameTest {
    @Test
    fun roundtripClipboard() {
        val msg = ControlMessage.ClipboardText("hello from phone", "android")
        val encoded = ControlFrame.encode(msg)
        val decoded = ControlFrame.decode(encoded) as ControlMessage.ClipboardText
        assertEquals("hello from phone", decoded.text)
        assertEquals("android", decoded.source)
    }

    @Test
    fun roundtripPing() {
        val msg = ControlMessage.Ping(7)
        val decoded = ControlFrame.decode(ControlFrame.encode(msg)) as ControlMessage.Ping
        assertEquals(7, decoded.nonce)
    }
}

package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.inputleaf.android.inject.ProtocolScanCodeDecoder
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.protocol.ProtocolParser
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import org.junit.Test

class ProtocolKeyboardShortcutsTest {
    private class KeyboardSession : HidReportSink {
        val decoder = ProtocolScanCodeDecoder()
        private val keyboard = HidKeyboard(this)
        val reports = mutableListOf<ByteArray>()

        override fun sendReport(report: ByteArray): Boolean {
            reports += report.copyOf()
            return true
        }

        fun key(button: Int, keysym: Int, down: Boolean) {
            val evdev = decoder.decodeKeyEvent(button, keysym, down)
            assertWithMessage("button=%s evdev=%s down=%s", button, evdev, down)
                .that(keyboard.key(evdev, down)).isTrue()
        }

        fun assertHeld(modifiers: Int, usage: Int) {
            val report = reports.last()
            assertThat(report[0].toInt() and 0xff).isEqualTo(modifiers)
            assertThat(report.drop(2).map { it.toInt() and 0xff })
                .containsExactly(usage, 0, 0, 0, 0, 0).inOrder()
        }

        fun assertReleased() {
            assertThat(reports.last().all { it == 0.toByte() }).isTrue()
        }

        fun leave() {
            keyboard.releaseAll()
            decoder.clearPressedKeys()
        }
    }

    @Test fun `Windows Win slash keeps working after the first shortcut learns the encoding`() {
        val session = KeyboardSession()
        repeat(3) {
            session.key(0x15b, 0xefeb, true)
            session.key(53, 0x2f, true)
            session.assertHeld(0x08, 0x38)
            session.key(53, 0, false)
            session.key(0x15b, 0, false)
            session.assertReleased()
            session.key(30, 0x61, true)
            session.key(30, 0, false)
        }
    }

    @Test fun `Windows Win Tab and Win I retain Meta after ordinary typing`() {
        val session = KeyboardSession()
        session.key(30, 0x61, true)
        session.key(30, 0, false)
        for ((button, keysym, usage) in listOf(Triple(15, 0xef09, 0x2b), Triple(23, 0x69, 0x0c))) {
            session.key(0x15b, 0xefeb, true)
            session.key(button, keysym, true)
            session.assertHeld(0x08, usage)
            session.key(button, 0, false)
            session.key(0x15b, 0, false)
            session.assertReleased()
        }
    }

    @Test fun `Windows right Win and Shift combine on the same HID keyboard`() {
        val session = KeyboardSession()
        session.key(42, 0xefe1, true)
        session.key(0x15c, 0xefec, true)
        session.key(31, 0x53, true)
        session.assertHeld(0x82, 0x16)
        session.key(31, 0, false)
        session.key(0x15c, 0, false)
        session.key(42, 0, false)
        session.assertReleased()
    }

    @Test fun `Windows held Win is released when leaving and works again after returning`() {
        val session = KeyboardSession()
        session.key(30, 0x61, true)
        session.key(30, 0, false)
        session.key(0x15b, 0xefeb, true)
        session.key(15, 0xef09, true)
        session.leave()
        session.assertReleased()
        session.key(0x15b, 0xefeb, true)
        session.key(23, 0x69, true)
        session.assertHeld(0x08, 0x0c)
        session.key(23, 0, false)
        session.key(0x15b, 0, false)
        session.assertReleased()
    }

    @Test fun `Linux evdev Super shortcuts keep the existing physical mapping`() {
        val session = KeyboardSession()
        session.key(30, 0x61, true)
        session.key(30, 0, false)
        repeat(2) {
            session.key(125, 0xefeb, true)
            session.key(53, 0x2f, true)
            session.assertHeld(0x08, 0x38)
            session.key(53, 0, false)
            session.key(125, 0, false)
            session.assertReleased()
        }
    }

    @Test fun `Linux X11 Super shortcuts retain the eight code offset`() {
        val session = KeyboardSession()
        session.key(133, 0xefeb, true)
        session.key(61, 0x2f, true)
        session.assertHeld(0x08, 0x38)
        session.key(61, 0, false)
        session.key(133, 0, false)
        session.key(38, 0x61, true)
        session.key(38, 0, false)
        session.key(134, 0xefec, true)
        session.key(31, 0x69, true)
        session.assertHeld(0x80, 0x0c)
        session.key(31, 0, false)
        session.key(134, 0, false)
        session.assertReleased()
    }

    @Test fun `Mac Command as the first key still learns Carbon for later non Latin input`() {
        val session = KeyboardSession()
        session.key(56, 0xefeb, true)
        session.key(45, 0x2f, true)
        session.assertHeld(0x08, 0x38)
        session.key(45, 0, false)
        session.key(56, 0, false)
        session.key(1, 0x0a85, true)
        session.assertHeld(0, 0x04)
        session.key(1, 0, false)
        session.key(55, 0xefec, true)
        session.key(35, 0x69, true)
        session.assertHeld(0x80, 0x0c)
        session.key(35, 0, false)
        session.key(55, 0, false)
        session.assertReleased()
    }

    @Test fun `Windows keypad Enter as first key identifies Set-1 and sends keypad HID usage`() {
        val session = KeyboardSession()
        session.key(0x11c, 0xef8d, true)
        session.assertHeld(0, 0x58)
        session.key(0x11c, 0, false)
        session.assertReleased()
    }

    @Test fun `Windows keypad slash after ordinary typing identifies Set-1 without Win`() {
        val session = KeyboardSession()
        session.key(30, 0x61, true)
        session.key(30, 0, false)
        session.key(0x135, 0xefaf, true)
        session.assertHeld(0, 0x54)
        session.key(0x135, 0, false)
        session.assertReleased()
    }

    @Test fun `Num Lock off keypad Home presses the real Home key on Windows and evdev (82)`() {
        for (windows in listOf(false, true)) {
            val session = KeyboardSession()
            if (windows) {
                session.key(0x15b, 0xefeb, true)
                session.key(0x15b, 0, false)
            } else {
                session.key(30, 0x61, true)
                session.key(30, 0, false)
            }
            // Deskflow uses KP_Home (ef95), not ordinary Home (ef50). Many text boxes
            // ignore Android's keypad-7 to Home fallback, so the real Home key is pressed.
            session.key(71, 0xef95, true)
            session.assertHeld(0, 0x4a)
            session.key(71, 0, false)
            session.assertReleased()
        }
    }


    @Test fun `wire keypad key down preserves the extended Windows button through HID injection`() {
        for ((keysym, button, usage) in listOf(Triple(0xef8d, 0x11c, 0x58), Triple(0xefaf, 0x135, 0x54))) {
            val packet = ByteBuffer.allocate(14).putInt(10)
                .put("DKDN".toByteArray(Charsets.US_ASCII))
                .putShort(keysym.toShort()).putShort(0).putShort(button.toShort()).array()
            val event = ProtocolParser(ByteArrayInputStream(packet)).readNext() as InputLeapEvent.KeyDown
            assertThat(event.scancode).isEqualTo(button)
            assertThat(event.keyId).isEqualTo(keysym)
            val session = KeyboardSession()
            session.key(event.scancode, event.keyId, true)
            session.assertHeld(0, usage)
            session.key(event.scancode, 0, false)
            session.assertReleased()
        }
    }
}

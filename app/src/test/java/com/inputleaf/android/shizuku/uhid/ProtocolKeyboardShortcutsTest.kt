package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.inputleaf.android.inject.ProtocolScanCodeDecoder
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
}

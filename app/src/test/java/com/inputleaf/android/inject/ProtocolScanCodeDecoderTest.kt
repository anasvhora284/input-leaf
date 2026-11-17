package com.inputleaf.android.inject

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProtocolScanCodeDecoderTest {
    @Test fun `learning Mac encoding while a layout-specific key is held preserves its release`() {
        val decoder = ProtocolScanCodeDecoder()
        // QWERTZ Y occupies the ANSI Z position. Until Space identifies the Mac,
        // use the keysym fallback, and release the same key even after learning.
        assertThat(decoder.decodeKeyEvent(7, 0x79, true)).isEqualTo(21)
        assertThat(decoder.decodeKeyEvent(50, 0x20, true)).isEqualTo(57)
        assertThat(decoder.decodeKeyEvent(7, 0x79, true)).isEqualTo(21) // repeat
        assertThat(decoder.decodeKeyEvent(7, 0x79, false)).isEqualTo(21)
        assertThat(decoder.decodeKeyEvent(7, 0x79, true)).isEqualTo(44) // next press
        decoder.clearPressedKeys()
        assertThat(decoder.decodeKeyEvent(7, 0x79, true)).isEqualTo(44)
    }

    @Test fun `Mac A does not inject Escape and identifies Carbon buttons`() {
        val decoder = ProtocolScanCodeDecoder()
        assertThat(decoder.toEvdev(1, 0x61)).isEqualTo(30)
        assertThat(decoder.toEvdev(9, 0x63)).isEqualTo(46)
        assertThat(decoder.toEvdev(1, 0)).isEqualTo(30)
    }

    @Test fun `Mac modifiers identify the encoding before non Latin typing`() {
        val decoder = ProtocolScanCodeDecoder()
        assertThat(decoder.toEvdev(60, 0xefe3)).isEqualTo(29) // Control_L
        assertThat(decoder.toEvdev(1, 0x0a85)).isEqualTo(30)
        assertThat(decoder.toEvdev(55, 0xefec)).isEqualTo(126) // Right Command
        assertThat(decoder.toEvdev(56, 0xefeb)).isEqualTo(125) // Command
        assertThat(decoder.toEvdev(57, 0xefe1)).isEqualTo(42) // Shift
        assertThat(decoder.toEvdev(63, 0xefe4)).isEqualTo(97) // Right Control
        assertThat(decoder.toEvdev(59, 0xefe9)).isEqualTo(56) // Option
        assertThat(decoder.toEvdev(62, 0xefea)).isEqualTo(100) // Right Option
    }

    @Test fun `Mac navigation function and keypad keys map physically`() {
        val decoder = ProtocolScanCodeDecoder()
        decoder.toEvdev(50, 0x20) // Space
        for ((button, evdev) in listOf(
            124 to 105, 125 to 106, 126 to 108, 127 to 103,
            37 to 28, 52 to 14, 118 to 111, 123 to 59, 112 to 88, 77 to 96,
        )) {
            assertThat(decoder.toEvdev(button, 0)).isEqualTo(evdev)
        }
        assertThat(decoder.toEvdev(64, 0)).isEqualTo(0) // Fn is not an evdev key
        assertThat(decoder.toEvdev(500, 0)).isEqualTo(0)
    }

    @Test fun `unknown encoding never guesses a physical key for non Latin text`() {
        assertThat(ProtocolScanCodeDecoder().toEvdev(1, 0x0a85)).isEqualTo(0)
        assertThat(ProtocolScanCodeDecoder().toEvdev(0, 0x61)).isEqualTo(0)
    }

    @Test fun `windows evdev buttons pass through`() {
        val decoder = ProtocolScanCodeDecoder()
        assertThat(decoder.toEvdev(30, keysym = 0x61)).isEqualTo(30)
        assertThat(decoder.toEvdev(46, keysym = 0x0441)).isEqualTo(46)
    }

    @Test fun `linux X11 buttons convert to evdev after learning from a known key`() {
        val decoder = ProtocolScanCodeDecoder()
        // Ctrl_L: evdev 29, X11 37
        assertThat(decoder.toEvdev(37, keysym = 0xefe3)).isEqualTo(29)
        // Physical A key while composing Gujarati: X11 38 -> evdev 30
        assertThat(decoder.toEvdev(38, keysym = 0x0a85)).isEqualTo(30)
    }

    @Test fun `space on X11 learns the same offset`() {
        val decoder = ProtocolScanCodeDecoder()
        assertThat(decoder.toEvdev(65, keysym = 0x20)).isEqualTo(57)
        assertThat(decoder.toEvdev(38, keysym = 0x0a85)).isEqualTo(30)
    }

    @Test fun `Gujarati Ctrl plus physical A uses evdev A after X11 learn`() {
        val decoder = ProtocolScanCodeDecoder()
        decoder.toEvdev(37, keysym = 0xefe3)
        val evdev = decoder.toEvdev(38, keysym = 0x0a85)
        val action = KeysymResolver.resolve(
            0x0a85,
            scancode = evdev,
            isDown = true,
            shortcutModifiers = true,
        )
        assertThat(action).isInstanceOf(KeysymAction.KeyEventAction::class.java)
        val keyEvent = action as KeysymAction.KeyEventAction
        assertThat(keyEvent.keyCode).isEqualTo(KeyEvent.KEYCODE_A)
        assertThat(keyEvent.scanCode).isEqualTo(30)
    }

    @Test fun `NoSymbol with a physical button still maps after X11 learn`() {
        val decoder = ProtocolScanCodeDecoder()
        decoder.toEvdev(37, keysym = 0xefe3)
        val evdev = decoder.toEvdev(38, keysym = 0)
        val action = KeysymResolver.resolve(0, scancode = evdev, isDown = true)
        assertThat(action).isInstanceOf(KeysymAction.KeyEventAction::class.java)
        assertThat((action as KeysymAction.KeyEventAction).keyCode).isEqualTo(KeyEvent.KEYCODE_A)
    }

    @Test fun `Windows extended Win upgrades the shared evdev prefix after Latin typing`() {
        val decoder = ProtocolScanCodeDecoder()
        assertThat(decoder.toEvdev(30, 0x61)).isEqualTo(30)
        assertThat(decoder.toEvdev(0x15b, 0xefeb)).isEqualTo(125)
        assertThat(decoder.toEvdev(0x15c, 0)).isEqualTo(126)
        assertThat(decoder.toEvdev(0x14b, 0)).isEqualTo(105)
        assertThat(decoder.toEvdev(23, 0x0a85)).isEqualTo(23)
        assertThat(decoder.toEvdev(0x1ff, 0)).isEqualTo(0)
    }

    @Test fun `Windows right Ctrl can identify Set-1 before Win is pressed`() {
        val decoder = ProtocolScanCodeDecoder()
        assertThat(decoder.toEvdev(0x11d, 0xefe4)).isEqualTo(97)
        assertThat(decoder.toEvdev(0x15b, 0)).isEqualTo(125)
        assertThat(decoder.toEvdev(0x138, 0)).isEqualTo(100)
    }

    @Test fun `Windows Win release and repeat stay paired when symbols are absent`() {
        val decoder = ProtocolScanCodeDecoder()
        decoder.toEvdev(30, 0x61)
        repeat(2) {
            assertThat(decoder.decodeKeyEvent(0x15b, 0xefeb, true)).isEqualTo(125)
            assertThat(decoder.decodeKeyEvent(0x15b, 0, true)).isEqualTo(125)
            assertThat(decoder.decodeKeyEvent(0x15b, 0, false)).isEqualTo(125)
        }
    }

    @Test fun `Linux evdev Meta remains evdev and does not select Windows`() {
        val decoder = ProtocolScanCodeDecoder()
        decoder.toEvdev(30, 0x61)
        assertThat(decoder.toEvdev(125, 0xefeb)).isEqualTo(125)
        assertThat(decoder.toEvdev(126, 0xefec)).isEqualTo(126)
        assertThat(decoder.toEvdev(133, 0)).isEqualTo(133)
    }

    @Test fun `established X11 and Mac encodings are not reclassified by Windows shaped events`() {
        val x11 = ProtocolScanCodeDecoder()
        x11.toEvdev(38, 0x61)
        assertThat(x11.toEvdev(0x15b, 0xefeb)).isEqualTo(0x15b - 8)
        assertThat(x11.toEvdev(31, 0)).isEqualTo(23)
        val mac = ProtocolScanCodeDecoder()
        mac.toEvdev(56, 0xefeb)
        assertThat(mac.toEvdev(0x15b, 0xefeb)).isEqualTo(0)
        assertThat(mac.toEvdev(35, 0)).isEqualTo(23)
    }

    @Test fun `unknown extended codes do not invent a physical fallback before or after Windows detection`() {
        val decoder = ProtocolScanCodeDecoder()
        decoder.toEvdev(30, 0x61) // The original implementation passes this prefix through as evdev.
        val before = KeysymResolver.resolve(0, decoder.toEvdev(0x122, 0), true)
        decoder.toEvdev(0x15b, 0xefeb)
        val after = KeysymResolver.resolve(0, decoder.toEvdev(0x122, 0), true)
        assertThat(before).isSameInstanceAs(KeysymAction.Ignore)
        assertThat(after).isSameInstanceAs(KeysymAction.Ignore)
    }


    @Test fun `connection reset relearns X11 Mac and evdev after Windows`() {
        for ((first, symbol, expected, latinA) in listOf(
            listOf(37, 0xefe3, 29, 38),
            listOf(56, 0xefeb, 125, 1),
            listOf(29, 0xefe3, 29, 30),
        )) {
            val decoder = ProtocolScanCodeDecoder()
            decoder.decodeKeyEvent(0x15b, 0xefeb, true)
            decoder.resetForConnection()
            assertThat(decoder.decodeKeyEvent(first, symbol, true)).isEqualTo(expected)
            assertThat(decoder.decodeKeyEvent(latinA, 0x61, true)).isEqualTo(30)
        }
    }

    @Test fun `Leave clears held keys but preserves the Windows format for NoSymbol events`() {
        val decoder = ProtocolScanCodeDecoder()
        decoder.decodeKeyEvent(0x15b, 0xefeb, true)
        decoder.clearPressedKeys()
        assertThat(decoder.decodeKeyEvent(0x15c, 0, true)).isEqualTo(126)
        assertThat(decoder.decodeKeyEvent(0x15c, 0, false)).isEqualTo(126)
    }

    @Test fun `keypad Enter and divide identify the appropriate Windows X11 and Mac encoding`() {
        for ((button, symbol, expected, latinA) in listOf(
            listOf(0x11c, 0xef8d, 96, 30),
            listOf(0x135, 0xefaf, 98, 30),
            listOf(104, 0xef8d, 96, 38),
            listOf(106, 0xefaf, 98, 38),
            listOf(77, 0xef8d, 96, 1),
            listOf(76, 0xefaf, 98, 1),
        )) {
            val decoder = ProtocolScanCodeDecoder()
            assertThat(decoder.decodeKeyEvent(button, symbol, true)).isEqualTo(expected)
            assertThat(decoder.decodeKeyEvent(button, 0, true)).isEqualTo(expected)
            assertThat(decoder.decodeKeyEvent(button, 0, false)).isEqualTo(expected)
            assertThat(decoder.decodeKeyEvent(latinA, 0x61, true)).isEqualTo(30)
        }
    }

}

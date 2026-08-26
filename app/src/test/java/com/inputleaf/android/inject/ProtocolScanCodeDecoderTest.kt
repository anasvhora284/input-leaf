package com.inputleaf.android.inject

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProtocolScanCodeDecoderTest {
    @Test fun `windows evdev buttons pass through`() {
        val decoder = ProtocolScanCodeDecoder()
        assertThat(decoder.toEvdev(30, keysym = 0x61)).isEqualTo(30)
        assertThat(decoder.toEvdev(46, keysym = 0x0441)).isEqualTo(46)
    }

    @Test fun `linux X11 buttons convert to evdev after learning from a known key`() {
        val decoder = ProtocolScanCodeDecoder()
        // Ctrl_L: evdev 29, X11 37
        assertThat(decoder.toEvdev(37, keysym = 0xefe3)).isEqualTo(29)
        // Physical A key while composing Gujarati: X11 38 → evdev 30
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
}

package com.inputleaf.android.inject

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeysymResolverTest {
    @Test fun `English Latin letters use KeyEvent path`() {
        val action = KeysymResolver.resolve(0x61, scancode = 30, isDown = true)
        assertThat(action).isInstanceOf(KeysymAction.KeyEventAction::class.java)
        val keyEvent = action as KeysymAction.KeyEventAction
        assertThat(keyEvent.keyCode).isEqualTo(KeyEvent.KEYCODE_A)
        assertThat(keyEvent.scanCode).isEqualTo(30)
    }

    @Test fun `Russian Cyrillic letters use text path on key down`() {
        val action = KeysymResolver.resolve(0x06e1, scancode = 0, isDown = true)
        assertThat(action).isEqualTo(KeysymAction.Text("А"))
    }

    @Test fun `Russian Cyrillic letters are ignored on key up`() {
        val action = KeysymResolver.resolve(0x06e1, scancode = 0, isDown = false)
        assertThat(action).isEqualTo(KeysymAction.Ignore)
    }

    @Test fun `Arabic letters use text path on key down`() {
        val action = KeysymResolver.resolve(0x05c7, scancode = 0, isDown = true)
        assertThat(action).isEqualTo(KeysymAction.Text("ا"))
    }

    @Test fun `Chinese characters use text path via XK_Unicode`() {
        val action = KeysymResolver.resolve(0x01004e2d, scancode = 0, isDown = true)
        assertThat(action).isEqualTo(KeysymAction.Text("中"))
    }

    @Test fun `Hindi Devanagari uses text path via direct Unicode codepoint`() {
        val action = KeysymResolver.resolve(0x0905, scancode = 0, isDown = true)
        assertThat(action).isEqualTo(KeysymAction.Text("अ"))
    }

    @Test fun `Spanish accented Latin-1 uses text path`() {
        val action = KeysymResolver.resolve(0x00e1, scancode = 0, isDown = true)
        assertThat(action).isEqualTo(KeysymAction.Text("á"))
    }

    @Test fun `French accented Latin-1 uses text path`() {
        val action = KeysymResolver.resolve(0x00e9, scancode = 0, isDown = true)
        assertThat(action).isEqualTo(KeysymAction.Text("é"))
    }

    @Test fun `German umlaut uses text path`() {
        val action = KeysymResolver.resolve(0x00fc, scancode = 0, isDown = true)
        assertThat(action).isEqualTo(KeysymAction.Text("ü"))
    }

    @Test fun `Japanese kana uses text path`() {
        val action = KeysymResolver.resolve(0x04b1, scancode = 0, isDown = true)
        assertThat(action).isEqualTo(KeysymAction.Text("ア"))
    }

    @Test fun `Korean syllables use text path via XK_Unicode`() {
        val action = KeysymResolver.resolve(0x0100d55c, scancode = 0, isDown = true)
        assertThat(action).isEqualTo(KeysymAction.Text("한"))
    }

    @Test fun `Portuguese accented Latin-1 uses text path`() {
        val action = KeysymResolver.resolve(0x00e3, scancode = 0, isDown = true)
        assertThat(action).isEqualTo(KeysymAction.Text("ã"))
    }

    @Test fun `Gujarati uses text path via direct Unicode codepoint`() {
        val action = KeysymResolver.resolve(0x0a85, scancode = 0, isDown = true)
        assertThat(action).isEqualTo(KeysymAction.Text("અ"))
    }

    @Test fun `Cyrillic wire KeyID uses text path`() {
        val action = KeysymResolver.resolve(0x0410, scancode = 0, isDown = true)
        assertThat(action).isEqualTo(KeysymAction.Text("А"))
    }

    @Test fun `Input Leap control KeyIDs stay on KeyEvent path`() {
        val action = KeysymResolver.resolve(0xEF08, scancode = 0, isDown = true)
        assertThat(action).isInstanceOf(KeysymAction.KeyEventAction::class.java)
        assertThat((action as KeysymAction.KeyEventAction).keyCode).isEqualTo(KeyEvent.KEYCODE_DEL)
    }

    @Test fun `XK_Unicode uses text path`() {
        val action = KeysymResolver.resolve(0x01000430, scancode = 0, isDown = true)
        assertThat(action).isEqualTo(KeysymAction.Text("а"))
    }

    @Test fun `modifiers use KeyEvent path`() {
        val action = KeysymResolver.resolve(0xffe1, scancode = 0, isDown = true)
        assertThat(action).isInstanceOf(KeysymAction.KeyEventAction::class.java)
        assertThat((action as KeysymAction.KeyEventAction).keyCode).isEqualTo(KeyEvent.KEYCODE_SHIFT_LEFT)
    }

    @Test fun `falls back to scancode when keysym is unknown`() {
        val action = KeysymResolver.resolve(0x123456, scancode = 30, isDown = true)
        assertThat(action).isInstanceOf(KeysymAction.KeyEventAction::class.java)
        assertThat((action as KeysymAction.KeyEventAction).keyCode).isEqualTo(KeyEvent.KEYCODE_A)
        assertThat(action.scanCode).isEqualTo(30)
    }

    @Test fun `unknown keysym without scancode is ignored`() {
        val action = KeysymResolver.resolve(0x123456, scancode = 0, isDown = true)
        assertThat(action).isEqualTo(KeysymAction.Ignore)
    }
}

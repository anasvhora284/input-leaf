package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EvdevToHidTest {

    @Test
    fun `letters map from qwerty order to alphabetical HID order`() {
        assertThat(EvdevToHid.usage(30)).isEqualTo(0x04) // KEY_A
        assertThat(EvdevToHid.usage(48)).isEqualTo(0x05) // KEY_B
        assertThat(EvdevToHid.usage(46)).isEqualTo(0x06) // KEY_C
        assertThat(EvdevToHid.usage(44)).isEqualTo(0x1D) // KEY_Z
        assertThat(EvdevToHid.usage(16)).isEqualTo(0x14) // KEY_Q
        assertThat(EvdevToHid.usage(25)).isEqualTo(0x13) // KEY_P
    }

    @Test
    fun `digits map with zero at the end of the HID range`() {
        assertThat(EvdevToHid.usage(2)).isEqualTo(0x1E)  // KEY_1
        assertThat(EvdevToHid.usage(10)).isEqualTo(0x26) // KEY_9
        assertThat(EvdevToHid.usage(11)).isEqualTo(0x27) // KEY_0
    }

    @Test
    fun `punctuation is mapped - the old KeysymToHid dropped all of it`() {
        assertThat(EvdevToHid.usage(12)).isEqualTo(0x2D) // MINUS
        assertThat(EvdevToHid.usage(13)).isEqualTo(0x2E) // EQUAL
        assertThat(EvdevToHid.usage(39)).isEqualTo(0x33) // SEMICOLON
        assertThat(EvdevToHid.usage(51)).isEqualTo(0x36) // COMMA
        assertThat(EvdevToHid.usage(53)).isEqualTo(0x38) // SLASH
        assertThat(EvdevToHid.usage(43)).isEqualTo(0x31) // BACKSLASH
    }

    @Test
    fun `function and navigation keys are mapped`() {
        assertThat(EvdevToHid.usage(59)).isEqualTo(0x3A)  // F1
        assertThat(EvdevToHid.usage(68)).isEqualTo(0x43)  // F10
        assertThat(EvdevToHid.usage(87)).isEqualTo(0x44)  // F11
        assertThat(EvdevToHid.usage(88)).isEqualTo(0x45)  // F12
        assertThat(EvdevToHid.usage(103)).isEqualTo(0x52) // UP
        assertThat(EvdevToHid.usage(108)).isEqualTo(0x51) // DOWN
        assertThat(EvdevToHid.usage(102)).isEqualTo(0x4A) // HOME
    }

    @Test
    fun `keypad digits map from evdev layout order`() {
        assertThat(EvdevToHid.usage(79)).isEqualTo(0x59) // KP1
        assertThat(EvdevToHid.usage(75)).isEqualTo(0x5C) // KP4
        assertThat(EvdevToHid.usage(71)).isEqualTo(0x5F) // KP7
        assertThat(EvdevToHid.usage(73)).isEqualTo(0x61) // KP9
        assertThat(EvdevToHid.usage(82)).isEqualTo(0x62) // KP0
    }

    @Test
    fun `modifiers are flagged and carry distinct bits`() {
        assertThat(EvdevToHid.isModifier(29)).isTrue()  // LEFTCTRL
        assertThat(EvdevToHid.isModifier(42)).isTrue()  // LEFTSHIFT
        assertThat(EvdevToHid.isModifier(30)).isFalse() // KEY_A

        val bits = listOf(29, 42, 56, 125, 97, 54, 100, 126).map { EvdevToHid.modifierBit(it) }
        assertThat(bits).containsExactly(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80)
        assertThat(bits.toSet()).hasSize(8)
    }

    @Test
    fun `unmapped codes return null so the caller can fall back`() {
        assertThat(EvdevToHid.usage(0)).isNull()
        assertThat(EvdevToHid.usage(9999)).isNull()
    }

    @Test
    fun `every usage is unique`() {
        val codes = (1..255).mapNotNull { EvdevToHid.usage(it) }
        assertThat(codes).hasSize(codes.toSet().size)
    }
}

class HidKeyboardTest {

    private class RecordingSink : HidReportSink {
        val reports = mutableListOf<ByteArray>()
        override fun sendReport(report: ByteArray) {
            reports += report.copyOf()
        }
    }

    @Test
    fun `descriptor is a Generic Desktop Keyboard application collection`() {
        assertThat(HidKeyboard.DESCRIPTOR.take(6).toList()).containsExactly(
            0x05.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x06.toByte(),
            0xA1.toByte(), 0x01.toByte(),
        ).inOrder()
    }

    @Test
    fun `a key press occupies a slot and release frees it`() {
        val sink = RecordingSink()
        val keyboard = HidKeyboard(sink)

        assertThat(keyboard.key(30, isDown = true)).isTrue()  // KEY_A
        assertThat(keyboard.key(30, isDown = false)).isTrue()

        assertThat(sink.reports).hasSize(2)
        assertThat(sink.reports[0].size).isEqualTo(HidKeyboard.REPORT_SIZE)
        assertThat(sink.reports[0][2].toInt()).isEqualTo(0x04)
        assertThat(sink.reports[1][2].toInt()).isEqualTo(0)
    }

    @Test
    fun `modifiers set bits in byte zero, not a key slot`() {
        val sink = RecordingSink()
        val keyboard = HidKeyboard(sink)

        keyboard.key(42, isDown = true) // LEFTSHIFT
        keyboard.key(30, isDown = true) // KEY_A

        val last = sink.reports.last()
        assertThat(last[0].toInt()).isEqualTo(0x02)
        assertThat(last[2].toInt()).isEqualTo(0x04)
    }

    @Test
    fun `the reserved byte stays zero`() {
        val sink = RecordingSink()

        HidKeyboard(sink).key(30, isDown = true)

        assertThat(sink.reports.single()[1].toInt()).isEqualTo(0)
    }

    @Test
    fun `six keys fit and a seventh is refused rather than corrupting the report`() {
        val sink = RecordingSink()
        val keyboard = HidKeyboard(sink)
        val keys = listOf(30, 48, 46, 32, 18, 33) // a b c d e f

        keys.forEach { assertThat(keyboard.key(it, isDown = true)).isTrue() }
        assertThat(keyboard.key(34, isDown = true)).isFalse() // KEY_G, 7th

        val last = sink.reports.last()
        assertThat(last.drop(2).count { it.toInt() != 0 }).isEqualTo(HidKeyboard.MAX_KEYS)
    }

    @Test
    fun `a repeated press does not double-occupy a slot`() {
        val sink = RecordingSink()
        val keyboard = HidKeyboard(sink)

        keyboard.key(30, isDown = true)
        keyboard.key(30, isDown = true)

        assertThat(sink.reports.last().drop(2).count { it.toInt() != 0 }).isEqualTo(1)
    }

    @Test
    fun `unmapped keys are refused so the caller falls back`() {
        val sink = RecordingSink()

        assertThat(HidKeyboard(sink).key(9999, isDown = true)).isFalse()
        assertThat(sink.reports).isEmpty()
    }

    @Test
    fun `releaseAll clears modifiers and keys so nothing sticks down`() {
        val sink = RecordingSink()
        val keyboard = HidKeyboard(sink)
        keyboard.key(42, isDown = true) // LEFTSHIFT
        keyboard.key(30, isDown = true) // KEY_A
        sink.reports.clear()

        keyboard.releaseAll()

        val report = sink.reports.single()
        assertThat(report.all { it.toInt() == 0 }).isTrue()
    }

    @Test
    fun `releaseAll on an idle keyboard sends nothing`() {
        val sink = RecordingSink()

        HidKeyboard(sink).releaseAll()

        assertThat(sink.reports).isEmpty()
    }
}

class SoftKeyboardToggleTest {

    @Test
    fun `toggle flips the secure setting`() {
        val commands = mutableListOf<List<String>>()
        val toggle = SoftKeyboardToggle(exec = { command ->
            commands += command.toList()
            if (command.contains("get")) "0\n" else ""
        }, log = {})

        assertThat(toggle.toggle()).isTrue()
        assertThat(commands.last())
            .containsExactly("settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
            .inOrder()
    }

    @Test
    fun `restore puts the user's original value back`() {
        val commands = mutableListOf<List<String>>()
        val toggle = SoftKeyboardToggle(exec = { command ->
            commands += command.toList()
            if (command.contains("get")) "1\n" else ""
        }, log = {})

        toggle.remember()
        toggle.setShown(false)
        commands.clear()
        toggle.restore()

        assertThat(commands.single())
            .containsExactly("settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
            .inOrder()
    }

    @Test
    fun `restore without remember does nothing`() {
        val commands = mutableListOf<List<String>>()
        val toggle = SoftKeyboardToggle(exec = { commands += it.toList(); "" }, log = {})

        toggle.restore()

        assertThat(commands).isEmpty()
    }
}

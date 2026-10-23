package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HidMouseTest {

    private class RecordingSink : HidReportSink {
        val reports = mutableListOf<ByteArray>()
        var failNext = false
        override fun sendReport(report: ByteArray): Boolean {
            if (failNext) return false
            reports += report.copyOf()
            return true
        }
    }

    @Test
    fun `descriptor is a Generic Desktop Mouse application collection`() {
        assertThat(HidMouse.DESCRIPTOR.take(6).toList()).containsExactly(
            0x05.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x02.toByte(),
            0xA1.toByte(), 0x01.toByte(),
        ).inOrder()
    }

    @Test
    fun `descriptor includes five buttons, relative X Y and both wheels`() {
        val hex = HidMouse.DESCRIPTOR.joinToString("") { "%02X".format(it) }
        assertThat(hex).contains("0509") // Usage Page (Button)
        assertThat(hex).contains("1901") // Usage Minimum (1)
        assertThat(hex).contains("2905") // Usage Maximum (5): left, right, middle, back, forward
        assertThat(hex).contains("9505") // Report Count (5) one-bit buttons
        assertThat(hex).contains("7503") // ...padded with 3 bits to a byte
        assertThat(hex).contains("050C") // Usage Page (Consumer)
        assertThat(hex).contains("0A3802") // Usage (AC Pan): the horizontal wheel
        assertThat(hex).contains("0930") // Usage (X)
        assertThat(hex).contains("0931") // Usage (Y)
        assertThat(hex).contains("0938") // Usage (Wheel)
        assertThat(hex).contains("8106") // Input (Data,Var,Rel)
    }

    @Test
    fun `move encodes buttons dx dy and both wheels as a seven byte report`() {
        val sink = RecordingSink()
        val mouse = HidMouse(sink)

        assertThat(mouse.move(dx = 100, dy = -50, buttons = 0b101, wheel = 1, hWheel = -2)).isTrue()

        assertThat(sink.reports).hasSize(1)
        assertThat(sink.reports[0].size).isEqualTo(HidMouse.REPORT_SIZE)
        assertThat(sink.reports[0].toList()).containsExactly(
            0x05.toByte(),
            0x64.toByte(), 0x00.toByte(),
            0xCE.toByte(), 0xFF.toByte(),
            0x01.toByte(),
            0xFE.toByte(),
        ).inOrder()
    }

    @Test
    fun `side buttons pass through and undeclared bits are masked off`() {
        val sink = RecordingSink()
        val mouse = HidMouse(sink)

        mouse.move(dx = 0, dy = 0, buttons = HidMouse.BUTTON_BACK or HidMouse.BUTTON_FORWARD or 0x20, wheel = 0)

        assertThat(sink.reports.single()[0].toInt()).isEqualTo(HidMouse.BUTTON_BACK or HidMouse.BUTTON_FORWARD)
    }

    @Test
    fun `horizontal wheel clamps like the vertical one`() {
        val sink = RecordingSink()
        val mouse = HidMouse(sink)

        mouse.move(dx = 0, dy = 0, buttons = 0, wheel = 0, hWheel = 500)
        mouse.move(dx = 0, dy = 0, buttons = 0, wheel = 0, hWheel = -500)

        assertThat(sink.reports[0][6].toInt()).isEqualTo(127)
        assertThat(sink.reports[1][6].toInt()).isEqualTo(-127)
    }

    @Test
    fun `move encodes negative wheel as signed byte`() {
        val sink = RecordingSink()
        val mouse = HidMouse(sink)

        mouse.move(dx = 0, dy = 0, buttons = 0, wheel = -1)

        assertThat(sink.reports.single()[5].toInt() and 0xFF).isEqualTo(0xFF)
    }

    @Test
    fun `move clamps wheel between minus 127 and 127`() {
        val sink = RecordingSink()
        val mouse = HidMouse(sink)

        mouse.move(dx = 0, dy = 0, buttons = 0, wheel = 500)
        assertThat(sink.reports[0][5].toInt()).isEqualTo(127)

        mouse.move(dx = 0, dy = 0, buttons = 0, wheel = -500)
        assertThat(sink.reports[1][5].toInt()).isEqualTo(-127)
    }

    @Test
    fun `move returns false when sink write fails`() {
        val sink = RecordingSink()
        sink.failNext = true
        val mouse = HidMouse(sink)
        assertThat(mouse.move(dx = 1, dy = 0, buttons = 0, wheel = 0)).isFalse()
        assertThat(sink.reports).isEmpty()
    }

    @Test
    fun `releaseAll sends a zero report`() {
        val sink = RecordingSink()
        val mouse = HidMouse(sink)

        mouse.releaseAll()

        val report = sink.reports.single()
        assertThat(report.all { it.toInt() == 0 }).isTrue()
    }
}

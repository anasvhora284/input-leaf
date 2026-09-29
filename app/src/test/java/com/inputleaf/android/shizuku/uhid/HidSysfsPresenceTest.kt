package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HidSysfsPresenceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `matches uniq under an event node`() {
        val event = File(tmp.root, "event3").apply { mkdir() }
        val device = File(event, "device").apply { mkdir() }
        File(device, "name").writeText("Input Leaf Keyboard HID\n")
        File(device, "uniq").writeText("inputleaf-kbd\n")

        assertThat(
            HidSysfsPresence.present(
                name = "Input Leaf Keyboard HID",
                uniq = "inputleaf-kbd",
                root = tmp.root,
                procDevices = File(tmp.root, "missing-proc"),
            ),
        ).isTrue()
        assertThat(
            HidSysfsPresence.present(
                name = "other",
                uniq = "inputleaf-mouse",
                root = tmp.root,
                procDevices = File(tmp.root, "missing-proc"),
            ),
        ).isFalse()
    }

    @Test
    fun `matches uniq in proc bus input devices`() {
        val proc = File(tmp.root, "devices")
        proc.writeText(
            """
            I: Bus=0003 Vendor=1209 Product=0002 Version=0000
            N: Name="Input Leaf Mouse HID"
            P: Phys=
            S: Sysfs=/devices/virtual/misc/uhid/0003:1209:0002.0001/input/input12
            U: Uniq=inputleaf-mouse
            H: Handlers=mouse0 event12

            """.trimIndent(),
        )

        assertThat(
            HidSysfsPresence.present(
                name = "Input Leaf Mouse HID",
                uniq = "inputleaf-mouse",
                root = File(tmp.root, "empty-sysfs").apply { mkdir() },
                procDevices = proc,
            ),
        ).isTrue()
        assertThat(
            HidSysfsPresence.presentInProc(
                name = "other",
                uniq = "missing",
                procDevices = proc,
            ),
        ).isFalse()
    }
}

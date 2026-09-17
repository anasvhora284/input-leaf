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
            ),
        ).isTrue()
        assertThat(
            HidSysfsPresence.present(
                name = "other",
                uniq = "inputleaf-mouse",
                root = tmp.root,
            ),
        ).isFalse()
    }
}

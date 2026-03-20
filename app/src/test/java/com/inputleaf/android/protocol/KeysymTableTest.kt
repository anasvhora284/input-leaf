package com.inputleaf.android.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeysymTableTest {
    @Test fun `maps letter a to HID usage 0x04`() {
        assertThat(KeysymTable.toHidUsage(0x0061)).isEqualTo(0x04)
    }
    @Test fun `maps Space to HID usage 0x2C`() {
        assertThat(KeysymTable.toHidUsage(0x0020)).isEqualTo(0x2C)
    }
    @Test fun `maps Return to HID usage 0x28`() {
        assertThat(KeysymTable.toHidUsage(0xFF0D)).isEqualTo(0x28)
    }
    @Test fun `maps F1 to HID usage 0x3A`() {
        assertThat(KeysymTable.toHidUsage(0xFFBE)).isEqualTo(0x3A)
    }
    @Test fun `returns null for unmapped keysym`() {
        assertThat(KeysymTable.toHidUsage(0x1FFFFFF)).isNull()
    }
}

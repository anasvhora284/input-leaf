package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CursorScaleReaderTest {
    // Trimmed from `dumpsys input` on a OnePlus Nord 4, Android 16.
    private val dump = """
  Device 12: Logitech Mouse
    Cursor Input Mapper:
      XScale: 1.000
  Device 44: Input Leaf Mouse HID
    Sources: MOUSE
    Cursor Input Mapper:
      Parameters:
        Mode: POINTER
      XScale: 1.750
      YScale: 1.750
      ViewportDensityDpi: 560
  Device -1: Virtual
    Keyboard Input Mapper:
"""

    @Test
    fun `reads the XScale of the named device only`() {
        assertThat(CursorScaleReader.parse(dump, "Input Leaf Mouse HID")).isEqualTo(1.75)
        assertThat(CursorScaleReader.parse(dump, "Logitech Mouse")).isEqualTo(1.0)
    }

    @Test
    fun `an unlisted device or a section without XScale gives null`() {
        assertThat(CursorScaleReader.parse(dump, "Input Leaf Pen")).isNull()
        assertThat(CursorScaleReader.parse(dump, "Virtual")).isNull()
        assertThat(CursorScaleReader.parse("", "Input Leaf Mouse HID")).isNull()
    }
}

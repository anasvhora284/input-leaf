package com.inputleaf.android.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CursorLookTest {
    @Test
    fun storedValuesRoundTripAndUnknownOnesFallBack() {
        CursorSize.entries.forEach { assertThat(CursorSize.fromStorage(it.storageValue)).isEqualTo(it) }
        CursorColor.entries.forEach {
            assertThat(CursorColor.fromStorage(it.storageValue, CursorColor.BLACK)).isEqualTo(it)
        }
        assertThat(CursorSize.fromStorage(null)).isEqualTo(CursorSize.NORMAL)
        assertThat(CursorColor.fromStorage("teal", CursorColor.WHITE)).isEqualTo(CursorColor.WHITE)
    }

    @Test
    fun sizeScalesTheWindowAndKeepsTheTipOnThePoint() {
        val normal = CursorLook()
        val large = CursorLook(style = "leaf", size = CursorSize.EXTRA_LARGE)
        assertThat(normal.windowSizePx(2f)).isEqualTo(80)
        assertThat(large.windowSizePx(2f)).isEqualTo(160)
        assertThat(large.hotspot).isEqualTo(CursorHotspot.LEAF)

        val size = large.windowSizePx(2f)
        val (wx, wy) = large.hotspot.windowOrigin(500f, 300f, size)
        val (tx, ty) = large.hotspot.tipInWindow(size)
        assertThat(wx + tx).isWithin(1f).of(500f)
        assertThat(wy + ty).isWithin(1f).of(300f)
    }
}

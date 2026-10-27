package com.inputleaf.android.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CursorHotspotTest {
    private val size = CursorHotspot.windowSizePx(2f)

    /** Where the tip really is on screen once the window sits at [windowOrigin]. */
    private fun tipOnScreen(shape: CursorHotspot, x: Float, y: Float): Pair<Float, Float> {
        val (ox, oy) = shape.windowOrigin(x, y, size)
        val (tx, ty) = shape.tipInWindow(size)
        return (ox + tx) to (oy + ty)
    }

    @Test
    fun `default tip lands on the touch point at every position`() {
        for ((x, y) in listOf(0f to 0f, 540f to 1200f, 1919.5f to 33.3f)) {
            val (sx, sy) = tipOnScreen(CursorHotspot.DEFAULT, x, y)
            assertThat(sx).isWithin(0.5f).of(x)
            assertThat(sy).isWithin(0.5f).of(y)
        }
    }

    @Test
    fun `leaf tip lands on the touch point at every position`() {
        for ((x, y) in listOf(0f to 0f, 540f to 1200f, 1919.5f to 33.3f)) {
            val (sx, sy) = tipOnScreen(CursorHotspot.LEAF, x, y)
            assertThat(sx).isWithin(0.5f).of(x)
            assertThat(sy).isWithin(0.5f).of(y)
        }
    }

    @Test
    fun `default tip is near the top-left of the window, not the centre`() {
        val (tx, ty) = CursorHotspot.DEFAULT.tipInWindow(size)
        assertThat(tx).isWithin(0.01f).of(13f)
        assertThat(ty).isWithin(0.01f).of(3.7f / 24f * 80f)
    }

    @Test
    fun `leaf tip is mirrored so it is left of centre`() {
        val (tx, ty) = CursorHotspot.LEAF.tipInWindow(size)
        // image tip x = 1.48 (fit-centre pad) + 425/493*77.03 = 67.89, flipped: 80 - 67.89
        assertThat(tx).isWithin(0.05f).of(12.11f)
        assertThat(ty).isWithin(0.05f).of(68f / 512f * 80f)
    }

    @Test
    fun `mirroring only affects the leaf`() {
        assertThat(CursorHotspot.LEAF.mirrored).isTrue()
        assertThat(CursorHotspot.DEFAULT.mirrored).isFalse()
    }

    @Test
    fun `style string picks the shape`() {
        assertThat(CursorHotspot.forStyle("leaf")).isEqualTo(CursorHotspot.LEAF)
        assertThat(CursorHotspot.forStyle("default")).isEqualTo(CursorHotspot.DEFAULT)
        assertThat(CursorHotspot.forStyle("anything")).isEqualTo(CursorHotspot.DEFAULT)
    }

    @Test
    fun `the window is 40 dp, so the tip stays exact at every density`() {
        assertThat(CursorHotspot.windowSizePx(2f)).isEqualTo(80)
        assertThat(CursorHotspot.windowSizePx(2.75f)).isEqualTo(110)
        for (density in listOf(0.75f, 1f, 1.5f, 2.75f, 4f)) {
            val px = CursorHotspot.windowSizePx(density)
            for (shape in CursorHotspot.entries) {
                val (ox, oy) = shape.windowOrigin(300f, 200f, px)
                val (tx, ty) = shape.tipInWindow(px)
                assertThat(ox + tx).isWithin(0.5f).of(300f)
                assertThat(oy + ty).isWithin(0.5f).of(200f)
            }
        }
    }
}

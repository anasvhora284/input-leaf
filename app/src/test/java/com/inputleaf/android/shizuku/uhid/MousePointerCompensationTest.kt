package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class MousePointerCompensationTest {

    @Before
    @After
    fun resetCursorScale() {
        MousePointerCompensation.cursorScale = 1.0
    }

    @Test
    fun `first segment gain at settings speed zero matches AOSP formula`() {
        // 0.64 * SENSITIVITY[0+7=10] / 10 * 3.19
        assertThat(MousePointerCompensation.firstSegmentGain(0)).isWithin(0.0001).of(2.0416)
    }

    @Test
    fun `cursor scale multiplies the gain`() {
        // Measured on a OnePlus Nord 4, Android 16: XScale 1.75, pointer_speed -1, one
        // isolated move of 190 counts went 610 px (3.21 px per count).
        MousePointerCompensation.cursorScale = 1.75
        assertThat(MousePointerCompensation.firstSegmentGain(-1)).isWithin(0.01).of(3.21)
        MousePointerCompensation.cursorScale = 0.0
        assertThat(MousePointerCompensation.cursorScale).isEqualTo(1.0)
    }

    @Test
    fun `gainForSpeedMmPerS at zero, NaN or negative speed is the first segment gain`() {
        val first = MousePointerCompensation.firstSegmentGain(3)
        for (speed in doubleArrayOf(0.0, Double.NaN, -5.0)) {
            assertThat(MousePointerCompensation.gainForSpeedMmPerS(speed, 3)).isWithin(0.0001).of(first)
        }
    }

    @Test
    fun `a move from rest uses the base gain`() {
        val move = MousePointerCompensation.plan(-100.0, 0.0, PointerVelocityModel(), 0L, settingsSpeed = 0)
        assertThat(move.hidX).isEqualTo(-49)
        assertThat(move.dx).isWithin(0.01).of(-49 * 2.0416)
    }

    @Test
    fun `less than half a count is no report`() {
        val model = PointerVelocityModel()
        assertThat(MousePointerCompensation.plan(0.0, 0.0, model, 0L, 0).isNoOp).isTrue()
        assertThat(MousePointerCompensation.plan(0.9, -0.9, model, 0L, 0).isNoOp).isTrue()
    }

    @Test
    fun `a huge error is clamped to one report`() {
        val move = MousePointerCompensation.plan(150_000.0, 0.0, PointerVelocityModel(), 0L, 0)
        assertThat(move.hidX).isEqualTo(HidMouse.MAX_DELTA)
    }

    @Test
    fun `slow and fast moves land where Deskflow put the pointer (88)`() {
        // Before, 5 px steps overshot 7-14% (whole-pixel rounding) and 40 px steps every
        // 12 ms undershot 11% (speed guessed from the counts before choosing them).
        MousePointerCompensation.cursorScale = 1.75
        for ((step, gapMs) in listOf(5 to 30L, 15 to 20L, 40 to 12L, 120 to 8L)) {
            val model = PointerVelocityModel()
            var target = 0.0
            var pointer = 0.0
            repeat(40) { i ->
                target += step
                val t = i * gapMs * 1_000_000
                val move = MousePointerCompensation.plan(target - pointer, 0.0, model, t, -1)
                pointer += model.predict(t, move.hidX.toDouble(), 0.0, -1).first
                model.record(t, move.hidX, 0)
            }
            assertThat(pointer).isWithin(MousePointerCompensation.firstSegmentGain(-1)).of(target)
        }
    }

    @Test
    fun `scaleCoord preserves proportional position on rotation resize`() {
        assertThat(MousePointerCompensation.scaleCoord(540, 1079, 2413)).isEqualTo(1207)
        assertThat(MousePointerCompensation.scaleCoord(0, 1079, 2413)).isEqualTo(0)
    }
}

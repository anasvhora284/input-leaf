package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Before
import org.junit.Test

class MousePointerCompensationTest {

    @Before
    fun disableOplusRemapUnlessTestEnablesIt() {
        MousePointerCompensation.setOplusFamilyRemapForTest(false)
    }

    @After
    fun resetVendorPolicy() {
        MousePointerCompensation.resetVendorPolicyForTest()
    }

    @Test
    fun `first segment gain at settings speed zero matches AOSP formula`() {
        // 0.64 * SENSITIVITY[0+7=10] / 10 * 3.19
        assertThat(MousePointerCompensation.firstSegmentGain(0)).isWithin(0.0001).of(2.0416)
    }

    @Test
    fun `oplus remap applied once for firstSegmentGain and gainForSpeedMmPerS`() {
        MousePointerCompensation.setOplusFamilyRemapForTest(true)
        val settingsSpeed = 0
        val effective = MousePointerCompensation.effectivePointerSpeedForGain(settingsSpeed)
        assertThat(effective).isEqualTo(-1)

        val expected = MousePointerCompensation.segmentBaseGainFromEffectiveSpeed(effective)
        // 0.64 * SENSITIVITY[-1+7=6=9] / 10 * 3.19 = 1.83744
        assertThat(expected).isWithin(0.0001).of(1.83744)

        assertThat(MousePointerCompensation.firstSegmentGain(settingsSpeed))
            .isWithin(0.0001)
            .of(expected)
        assertThat(MousePointerCompensation.gainForSpeedMmPerS(0.0, settingsSpeed))
            .isWithin(0.0001)
            .of(expected)
    }

    @Test
    fun `gainForSpeedMmPerS at zero mm per s equals first segment gain`() {
        val speed = 3
        val first = MousePointerCompensation.firstSegmentGain(speed)
        val fromCurve = MousePointerCompensation.gainForSpeedMmPerS(0.0, speed)
        assertThat(fromCurve).isWithin(0.0001).of(first)
    }

    @Test
    fun `planHidMove uses single gain for hid and cooked prediction`() {
        val plan = MousePointerCompensation.planHidMove(-100, 0, settingsSpeed = 0, dtMs = 0L)
        assertThat(plan.gain).isWithin(0.0001).of(2.0416)
        assertThat(plan.hidX).isEqualTo(-49)
        assertThat(plan.cookedDelta().first).isEqualTo(-100)
    }

    @Test
    fun `STOP_TIME forces first segment gain even with short prior interval`() {
        val continuous = MousePointerCompensation.planHidMove(50, 0, 0, dtMs = 16L)
        val afterStop = MousePointerCompensation.planHidMove(50, 0, 0, dtMs = MousePointerCompensation.STOP_TIME_MS)
        assertThat(afterStop.gain).isWithin(0.0001).of(2.0416)
        assertThat(continuous.gain).isNotWithin(0.0001).of(afterStop.gain)
    }

    @Test
    fun `enter from center to top edge round trips under open loop model`() {
        val centerY = 1200
        val targetY = 0
        val errY = targetY - centerY
        val plan = MousePointerCompensation.planHidMove(0, errY, settingsSpeed = 0, dtMs = 0L)
        val cookedDy = plan.cookedDelta().second
        assertThat(centerY + cookedDy).isEqualTo(targetY)
    }

    @Test
    fun `planHidMove returns no-op for zero error`() {
        val plan = MousePointerCompensation.planHidMove(0, 0, 0, dtMs = 0L)
        assertThat(plan.isNoOp).isTrue()
    }

    @Test
    fun `scaleCoord preserves proportional position on rotation resize`() {
        assertThat(MousePointerCompensation.scaleCoord(540, 1079, 2413)).isEqualTo(1207)
        assertThat(MousePointerCompensation.scaleCoord(0, 1079, 2413)).isEqualTo(0)
    }

    @Test
    fun `speed change keeps continuous gain when dt is within STOP_TIME`() {
        val continuousAtOldSpeed = MousePointerCompensation.planHidMove(80, 0, settingsSpeed = 0, dtMs = 16L)
        val continuousAtNewSpeed = MousePointerCompensation.planHidMove(80, 0, settingsSpeed = 3, dtMs = 16L)
        assertThat(continuousAtNewSpeed.gain).isNotWithin(0.0001).of(MousePointerCompensation.firstSegmentGain(3))
        assertThat(continuousAtNewSpeed.gain).isNotWithin(0.0001).of(continuousAtOldSpeed.gain)
    }

    @Test
    fun `sub gain odd residuals produce no hid report and no jitter on repeat`() {
        val highGain = 11.0
        for (residual in intArrayOf(1, -1, 3, -3, 5, -5)) {
            assertThat(MousePointerCompensation.screenDeltaToHid(residual, highGain)).isEqualTo(0)
        }
        for (residual in intArrayOf(1, -1)) {
            val first = MousePointerCompensation.planHidMove(residual, 0, settingsSpeed = 0, dtMs = 16L)
            val second = MousePointerCompensation.planHidMove(residual, 0, settingsSpeed = 0, dtMs = 16L)
            assertThat(first.isNoOp).isTrue()
            assertThat(second.isNoOp).isTrue()
            assertThat(first.hidX).isEqualTo(second.hidX)
            assertThat(first.hidY).isEqualTo(second.hidY)
        }
    }

    @Test
    fun `single plan emits one hid report worth of movement`() {
        val plan = MousePointerCompensation.planHidMove(500, 0, settingsSpeed = 0, dtMs = 0L)
        assertThat(plan.hidX).isEqualTo(245)
        assertThat(plan.hidY).isEqualTo(0)
        assertThat(plan.cookedDelta().first).isEqualTo(500)
    }

    @Test
    fun `large error clamp leaves residual for a later event not a burst`() {
        val errX = 150_000
        val plan = MousePointerCompensation.planHidMove(errX, 0, settingsSpeed = 0, dtMs = 0L)
        assertThat(plan.hidX).isEqualTo(32767)
        val cookedDx = plan.cookedDelta().first
        assertThat(cookedDx).isLessThan(errX)
        assertThat(cookedDx).isEqualTo((32767 * plan.gain).roundToInt())
    }
}

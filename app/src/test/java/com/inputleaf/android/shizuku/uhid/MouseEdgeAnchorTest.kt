package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Before
import org.junit.Test

class MouseEdgeAnchorTest {

    @Before
    fun disableOplusRemap() {
        MousePointerCompensation.setOplusFamilyRemapForTest(false)
    }

    @After
    fun resetVendorPolicy() {
        MousePointerCompensation.resetVendorPolicyForTest()
    }

    @Test
    fun `bounds treat width-1 and height-1 as inclusive maxima`() {
        assertThat(MouseEdgeAnchor.atMin(0)).isTrue()
        assertThat(MouseEdgeAnchor.atMin(1)).isFalse()
        assertThat(MouseEdgeAnchor.atMax(1079, 1079)).isTrue()
        assertThat(MouseEdgeAnchor.atMax(1078, 1079)).isFalse()
        assertThat(MouseEdgeAnchor.atMax(2413, 2413)).isTrue()
        assertThat(MouseEdgeAnchor.atMax(2412, 2413)).isFalse()
    }

    @Test
    fun `gain mismatch left to right still clamps physical x via edge pulse`() {
        val maxX = 1079
        val maxY = 2413
        val predictedGain = MousePointerCompensation.firstSegmentGain(0)
        val actualGain = predictedGain * 0.7
        val sim = Sim(cookedX = 0, cookedY = 200, physicalX = 0, physicalY = 200, maxX = maxX, maxY = maxY)
        sim.flags = MouseEdgeAnchor.Flags(minX = true)

        var sawRightPulse = false
        for (x in 0..maxX step 40) {
            val target = x.coerceAtMost(maxX)
            val hidX = step(sim, target, 200, actualGain, dtMs = if (x == 0) 0L else 16L).hidX
            if (hidX == HidMouse.MAX_DELTA) sawRightPulse = true
        }
        val finalHidX = step(sim, maxX, 200, actualGain, dtMs = 16L).hidX
        if (finalHidX == HidMouse.MAX_DELTA) sawRightPulse = true

        assertThat(sawRightPulse).isTrue()
        assertThat(sim.cookedX).isEqualTo(maxX)
        assertThat(sim.physicalX).isEqualTo(maxX)
        assertThat(sim.physicalX).isGreaterThan(((maxX * 0.7).toInt()))
    }

    @Test
    fun `gain mismatch top to bottom still clamps physical y via edge pulse`() {
        val maxX = 1079
        val maxY = 2413
        val predictedGain = MousePointerCompensation.firstSegmentGain(0)
        val actualGain = predictedGain * 0.7
        val sim = Sim(cookedX = 400, cookedY = 0, physicalX = 400, physicalY = 0, maxX = maxX, maxY = maxY)
        sim.flags = MouseEdgeAnchor.Flags(minY = true)

        var sawBottomPulse = false
        for (y in 0..maxY step 80) {
            val target = y.coerceAtMost(maxY)
            val hidY = step(sim, 400, target, actualGain, dtMs = if (y == 0) 0L else 16L).hidY
            if (hidY == HidMouse.MAX_DELTA) sawBottomPulse = true
        }
        val finalHidY = step(sim, 400, maxY, actualGain, dtMs = 16L).hidY
        if (finalHidY == HidMouse.MAX_DELTA) sawBottomPulse = true

        assertThat(sawBottomPulse).isTrue()
        assertThat(sim.cookedY).isEqualTo(maxY)
        assertThat(sim.physicalY).isEqualTo(maxY)
    }

    @Test
    fun `without edge pulse predicted max hides physical undershoot`() {
        val maxX = 1079
        val predictedGain = MousePointerCompensation.firstSegmentGain(0)
        val actualGain = predictedGain * 0.7
        var cooked = 0
        var physical = 0
        for (x in 40..maxX step 40) {
            val err = x - cooked
            val plan = MousePointerCompensation.planHidMove(err, 0, 0, dtMs = 0L)
            if (plan.isNoOp) continue
            val (dx, _) = plan.cookedDelta()
            cooked = (cooked + dx).coerceIn(0, maxX)
            physical = (physical + (plan.hidX * actualGain).roundToInt()).coerceIn(0, maxX)
        }
        while (cooked < maxX) {
            val plan = MousePointerCompensation.planHidMove(maxX - cooked, 0, 0, dtMs = 0L)
            if (plan.isNoOp) break
            val (dx, _) = plan.cookedDelta()
            cooked = (cooked + dx).coerceIn(0, maxX)
            physical = (physical + (plan.hidX * actualGain).roundToInt()).coerceIn(0, maxX)
        }
        val stuck = MousePointerCompensation.planHidMove(maxX - cooked, 0, 0, dtMs = 0L)
        assertThat(cooked).isAtLeast(maxX - 5)
        assertThat(stuck.isNoOp).isTrue()
        assertThat(physical).isLessThan(cooked)
        assertThat(physical).isLessThan(maxX)
    }

    @Test
    fun `boundary pulse emits once then re-arms after moving inward`() {
        val reports = mutableListOf<Int>()
        var flags = MouseEdgeAnchor.Flags()
        var cookedX = 1000
        val maxX = 1079

        fun emit(targetX: Int) {
            val plan = MouseEdgeAnchor.plan(input(targetX, 200, cookedX, 200, maxX, 2413, flags), 0)
            reports += plan.hidX
            flags = plan.flagsAfter
            cookedX = plan.cookedXAfter
        }

        emit(1079)
        assertThat(reports.last()).isEqualTo(HidMouse.MAX_DELTA)
        emit(1079)
        assertThat(reports.last()).isEqualTo(0)
        emit(1079)
        assertThat(reports.last()).isEqualTo(0)
        emit(1000)
        assertThat(reports.last()).isNotEqualTo(HidMouse.MAX_DELTA)
        emit(1079)
        assertThat(reports.last()).isEqualTo(HidMouse.MAX_DELTA)
        assertThat(reports.count { it == HidMouse.MAX_DELTA }).isEqualTo(2)
    }

    @Test
    fun `edge pulse does not include tangential hid`() {
        val plan = MouseEdgeAnchor.plan(
            input(
                targetX = 1079,
                targetY = 800,
                cookedX = 500,
                cookedY = 100,
                maxX = 1079,
                maxY = 2413,
            ),
            0,
        )
        assertThat(plan.isEdgePulse).isTrue()
        assertThat(plan.hidX).isEqualTo(HidMouse.MAX_DELTA)
        assertThat(plan.hidY).isEqualTo(0)
        assertThat(plan.cookedYAfter).isEqualTo(100)
        assertThat(plan.cookedXAfter).isEqualTo(1079)
    }

    @Test
    fun `left edge pulse keeps tangential y unchanged`() {
        val plan = MouseEdgeAnchor.plan(
            input(targetX = 0, targetY = 400, cookedX = 80, cookedY = 350, maxX = 1079, maxY = 2413),
            0,
        )
        assertThat(plan.hidX).isEqualTo(HidMouse.MIN_DELTA)
        assertThat(plan.hidY).isEqualTo(0)
        assertThat(plan.cookedYAfter).isEqualTo(350)
        assertThat(plan.cookedXAfter).isEqualTo(0)
    }

    @Test
    fun `corner targeting pulses both axes in one report`() {
        val plan = MouseEdgeAnchor.plan(
            input(targetX = 1079, targetY = 2413, cookedX = 500, cookedY = 1200, maxX = 1079, maxY = 2413),
            0,
        )
        assertThat(plan.isEdgePulse).isTrue()
        assertThat(plan.hidX).isEqualTo(HidMouse.MAX_DELTA)
        assertThat(plan.hidY).isEqualTo(HidMouse.MAX_DELTA)
        assertThat(plan.cookedXAfter).isEqualTo(1079)
        assertThat(plan.cookedYAfter).isEqualTo(2413)
        assertThat(plan.flagsAfter.maxX).isTrue()
        assertThat(plan.flagsAfter.maxY).isTrue()
    }

    @Test
    fun `top-left corner pulses both negative axes`() {
        val plan = MouseEdgeAnchor.plan(
            input(targetX = 0, targetY = 0, cookedX = 200, cookedY = 200, maxX = 1079, maxY = 2413),
            0,
        )
        assertThat(plan.hidX).isEqualTo(HidMouse.MIN_DELTA)
        assertThat(plan.hidY).isEqualTo(HidMouse.MIN_DELTA)
        assertThat(plan.cookedXAfter).isEqualTo(0)
        assertThat(plan.cookedYAfter).isEqualTo(0)
    }

    @Test
    fun `interior snap is a single compensated report`() {
        val reports = MouseEdgeAnchor.planSnap(
            input(
                targetX = 400,
                targetY = 500,
                cookedX = 539,
                cookedY = 1206,
                maxX = 1079,
                maxY = 2413,
                preferTangentialFirst = true,
            ),
            0,
        )
        assertThat(reports).hasSize(1)
        assertThat(reports[0].isEdgePulse).isFalse()
        assertThat(reports[0].isNoOp).isFalse()
    }

    @Test
    fun `interior snap does not emit a second compensation burst`() {
        val reports = MouseEdgeAnchor.planSnap(
            input(
                targetX = 400,
                targetY = 500,
                cookedX = 0,
                cookedY = 0,
                maxX = 1079,
                maxY = 2413,
                preferTangentialFirst = true,
            ),
            0,
        )
        assertThat(reports).hasSize(1)
        assertThat(reports[0].isEdgePulse).isFalse()
    }

    @Test
    fun `corner snap from center saturates both max axes`() {
        val reports = MouseEdgeAnchor.planSnap(
            input(
                targetX = 1079,
                targetY = 2413,
                cookedX = 539,
                cookedY = 1206,
                maxX = 1079,
                maxY = 2413,
                preferTangentialFirst = true,
            ),
            0,
        )
        assertThat(reports.any { it.isEdgePulse && it.hidX == HidMouse.MAX_DELTA }).isTrue()
        assertThat(reports.any { it.isEdgePulse && it.hidY == HidMouse.MAX_DELTA }).isTrue()
        assertThat(reports.last().cookedXAfter).isEqualTo(1079)
        assertThat(reports.last().cookedYAfter).isEqualTo(2413)
    }

    @Test
    fun `attach at top edge compensates X then pulses Y`() {
        val maxX = 1079
        val maxY = 2413
        val centerX = maxX / 2
        val centerY = maxY / 2
        val reports = MouseEdgeAnchor.planSnap(
            input(
                targetX = 100,
                targetY = 0,
                cookedX = centerX,
                cookedY = centerY,
                maxX = maxX,
                maxY = maxY,
                preferTangentialFirst = true,
            ),
            0,
        )
        assertThat(reports).hasSize(2)
        assertThat(reports[0].isEdgePulse).isFalse()
        assertThat(reports[0].hidY).isEqualTo(0)
        assertThat(reports[0].hidX).isNotEqualTo(0)
        assertThat(reports[1].isEdgePulse).isTrue()
        assertThat(reports[1].hidX).isEqualTo(0)
        assertThat(reports[1].hidY).isEqualTo(HidMouse.MIN_DELTA)
        assertThat(reports[1].cookedYAfter).isEqualTo(0)
        assertThat(reports[1].cookedXAfter).isEqualTo(reports[0].cookedXAfter)
    }

    @Test
    fun `attach at left edge compensates Y then pulses X`() {
        val maxX = 1079
        val maxY = 2413
        val reports = MouseEdgeAnchor.planSnap(
            input(
                targetX = 0,
                targetY = 200,
                cookedX = maxX / 2,
                cookedY = maxY / 2,
                maxX = maxX,
                maxY = maxY,
                preferTangentialFirst = true,
            ),
            0,
        )
        assertThat(reports).hasSize(2)
        assertThat(reports[0].hidX).isEqualTo(0)
        assertThat(reports[0].hidY).isNotEqualTo(0)
        assertThat(reports[1].hidX).isEqualTo(HidMouse.MIN_DELTA)
        assertThat(reports[1].hidY).isEqualTo(0)
    }

    @Test
    fun `relative move uses protocol target not the relative delta for edges`() {
        val flags = MouseEdgeAnchor.Flags(maxX = true)
        val plan = MouseEdgeAnchor.plan(
            input(
                targetX = 1070,
                targetY = 200,
                cookedX = 1079,
                cookedY = 200,
                maxX = 1079,
                maxY = 2413,
                flags = flags,
            ),
            0,
        )
        assertThat(plan.isEdgePulse).isFalse()
        assertThat(plan.hidX).isNotEqualTo(HidMouse.MAX_DELTA)
        assertThat(plan.flagsAfter.maxX).isFalse()
    }

    @Test
    fun `failed write must not be modeled as success`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.completeAttach(newDevice = true)
        state.clearCenterSeed()
        state.updatePointerTarget(1079, 200)
        val before = state.cookedPosition()
        val flagsBefore = state.edgeFlags
        val plan = MouseEdgeAnchor.plan(state.plannerInput(nowMs = 10L), 0)
        assertThat(plan.isEdgePulse).isTrue()
        // Write fails: do not apply.
        assertThat(state.cookedPosition()).isEqualTo(before)
        assertThat(state.edgeFlags).isEqualTo(flagsBefore)
        assertThat(state.lastWasEdgePulse).isFalse()
    }

    @Test
    fun `successful plan re-anchors only after apply`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.completeAttach(newDevice = true)
        state.clearCenterSeed()
        state.updatePointerTarget(1079, 200)
        val plan = MouseEdgeAnchor.plan(state.plannerInput(nowMs = 10L), 0)
        state.applySuccessfulPlan(plan, nowMs = 10L)
        assertThat(state.cookedX).isEqualTo(1079)
        assertThat(state.edgeFlags.maxX).isTrue()
        assertThat(state.lastWasEdgePulse).isTrue()
        assertThat(state.needsCenterSeed()).isFalse()
    }

    @Test
    fun `resize resets edge-anchor flags so the new bounds can re-pulse`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.completeAttach(newDevice = true)
        state.clearCenterSeed()
        state.updatePointerTarget(1079, 200)
        state.applySuccessfulPlan(MouseEdgeAnchor.plan(state.plannerInput(1L), 0), 1L)
        assertThat(state.edgeFlags.maxX).isTrue()
        state.resizeDisplay(newMaxX = 1919, newMaxY = 1079)
        assertThat(state.edgeFlags).isEqualTo(MouseEdgeAnchor.Flags())
        assertThat(state.lastWasEdgePulse).isFalse()
    }

    @Test
    fun `post-pulse tangential uses last-segment gain inside the velocity horizon`() {
        val afterPulse = MouseEdgeAnchor.plan(
            input(
                targetX = 1079,
                targetY = 400,
                cookedX = 1079,
                cookedY = 200,
                maxX = 1079,
                maxY = 2413,
                flags = MouseEdgeAnchor.Flags(maxX = true),
                lastWasEdgePulse = true,
                dtMs = 16L,
            ),
            0,
        )
        val lastSeg = MousePointerCompensation.gainForHidSpeed(1_000_000.0, 0)
        assertThat(afterPulse.isEdgePulse).isFalse()
        assertThat(afterPulse.gain).isWithin(0.0001).of(lastSeg)
        assertThat(afterPulse.hidX).isEqualTo(0)
        assertThat(afterPulse.hidY).isNotEqualTo(0)

        val afterHorizon = MouseEdgeAnchor.plan(
            input(
                targetX = 1079,
                targetY = 400,
                cookedX = 1079,
                cookedY = 200,
                maxX = 1079,
                maxY = 2413,
                flags = MouseEdgeAnchor.Flags(maxX = true),
                lastWasEdgePulse = true,
                dtMs = MouseEdgeAnchor.EDGE_PULSE_GAIN_HORIZON_MS,
            ),
            0,
        )
        assertThat(afterHorizon.gain).isNotWithin(0.0001).of(lastSeg)
    }

    @Test
    fun `one-pixel display pulses min only`() {
        val plan = MouseEdgeAnchor.plan(
            input(targetX = 0, targetY = 0, cookedX = 0, cookedY = 0, maxX = 0, maxY = 0),
            0,
        )
        assertThat(plan.hidX).isEqualTo(HidMouse.MIN_DELTA)
        assertThat(plan.hidY).isEqualTo(HidMouse.MIN_DELTA)
        assertThat(plan.flagsAfter.minX).isTrue()
        assertThat(plan.flagsAfter.maxX).isFalse()
    }

    @Test
    fun `planner no-op when already anchored at the same edge`() {
        val plan = MouseEdgeAnchor.plan(
            input(
                targetX = 0,
                targetY = 200,
                cookedX = 0,
                cookedY = 200,
                maxX = 1079,
                maxY = 2413,
                flags = MouseEdgeAnchor.Flags(minX = true),
            ),
            0,
        )
        assertThat(plan.isNoOp).isTrue()
        assertThat(plan.isEdgePulse).isFalse()
    }

    private fun input(
        targetX: Int,
        targetY: Int,
        cookedX: Int,
        cookedY: Int,
        maxX: Int,
        maxY: Int,
        flags: MouseEdgeAnchor.Flags = MouseEdgeAnchor.Flags(),
        preferTangentialFirst: Boolean = false,
        lastWasEdgePulse: Boolean = false,
        dtMs: Long = 0L,
    ) = MouseEdgeAnchor.Input(
        targetX = targetX,
        targetY = targetY,
        cookedX = cookedX,
        cookedY = cookedY,
        maxX = maxX,
        maxY = maxY,
        flags = flags,
        preferTangentialFirst = preferTangentialFirst,
        lastWasEdgePulse = lastWasEdgePulse,
        dtMs = dtMs,
    )

    private data class Sim(
        var cookedX: Int,
        var cookedY: Int,
        var physicalX: Int,
        var physicalY: Int,
        val maxX: Int,
        val maxY: Int,
        var flags: MouseEdgeAnchor.Flags = MouseEdgeAnchor.Flags(),
        var lastWasEdgePulse: Boolean = false,
    )

    private fun step(
        sim: Sim,
        targetX: Int,
        targetY: Int,
        actualGain: Double,
        dtMs: Long,
    ): MouseEdgeAnchor.Plan {
        val plan = MouseEdgeAnchor.plan(
            input(
                targetX, targetY, sim.cookedX, sim.cookedY, sim.maxX, sim.maxY,
                flags = sim.flags,
                lastWasEdgePulse = sim.lastWasEdgePulse,
                dtMs = dtMs,
            ),
            0,
        )
        if (plan.isNoOp) return plan
        sim.cookedX = plan.cookedXAfter
        sim.cookedY = plan.cookedYAfter
        sim.flags = plan.flagsAfter
        sim.lastWasEdgePulse = plan.isEdgePulse
        sim.physicalX = (sim.physicalX + (plan.hidX * actualGain).roundToInt()).coerceIn(0, sim.maxX)
        sim.physicalY = (sim.physicalY + (plan.hidY * actualGain).roundToInt()).coerceIn(0, sim.maxY)
        return plan
    }
}

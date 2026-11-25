package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HidMouseStateRelativeTest {

    private fun attached() = HidMouseState(maxX = 1079, maxY = 2413).also {
        it.completeAttach(newDevice = true)
        it.clearCenterSeed()
    }

    @Test
    fun `unit scale passes the delta through as counts`() {
        val plan = attached().planRelative(5, -3, 1.0)!!
        assertThat(plan.hidX).isEqualTo(5)
        assertThat(plan.hidY).isEqualTo(-3)
        assertThat(plan.isEdgePulse).isFalse()
        assertThat(plan.flagsAfter).isEqualTo(MouseEdgeAnchor.Flags())
    }

    @Test
    fun `scale above one carries the fraction to the next report`() {
        val state = attached()
        val total = (1..7).sumOf { state.planRelative(1, 0, 1.75)?.hidX ?: 0 }
        assertThat(total).isEqualTo(4)
    }

    @Test
    fun `a delta below half a count sends nothing`() {
        assertThat(attached().planRelative(1, 1, 4.0)).isNull()
    }

    @Test
    fun `no clamp at the screen edge and cooked stays on screen`() {
        val state = attached()
        state.updatePointerTarget(1079, 1206)
        val edge = MouseEdgeAnchor.plan(state.plannerInput(0L), 0)
        state.applySuccessfulPlan(edge, 0L)
        val plan = state.planRelative(400, 0, 1.0)!!
        assertThat(plan.hidX).isEqualTo(400)
        state.applySuccessfulPlan(plan, 10_000_000L, 0)
        assertThat(state.cookedX).isAtMost(1079.0)
    }

    @Test
    fun `applied relative plan moves cooked and clears edge flags for the next DMMV`() {
        val state = attached()
        state.onEnter(0, 1206)
        state.completeAttach(newDevice = false, warpedToEnter = true)
        state.applySuccessfulPlan(state.planRelative(300, 0, 1.0)!!, 0L, 0)
        val before = state.cookedX
        val plan = state.planRelative(-100, 0, 1.0)!!
        state.applySuccessfulPlan(plan, 0L, 0)
        assertThat(state.cookedX).isLessThan(before)
        assertThat(state.edgeFlags).isEqualTo(MouseEdgeAnchor.Flags())
        state.updatePointerTarget(700, 1206)
        val next = MouseEdgeAnchor.plan(state.plannerInput(50_000_000L), 0)
        assertThat(next.isEdgePulse).isFalse()
    }

    @Test
    fun `detach drops the carried fraction`() {
        val state = attached()
        assertThat(state.planRelative(1, 0, 4.0)).isNull()
        state.detach()
        assertThat(state.planRelative(1, 0, 4.0)).isNull()
    }

    @Test
    fun `reset on disconnect drops the carried fraction`() {
        val state = attached()
        assertThat(state.planRelative(1, 0, 4.0)).isNull()
        state.resetOnDisconnect()
        assertThat(state.planRelative(1, 0, 4.0)).isNull()
    }

    @Test
    fun `a new device drops the carried fraction`() {
        val state = attached()
        assertThat(state.planRelative(1, 0, 4.0)).isNull()
        state.completeAttach(newDevice = true)
        assertThat(state.planRelative(1, 0, 4.0)).isNull()
    }
}

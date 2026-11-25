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
    fun `no clamp at the screen edge`() {
        assertThat(attached().planRelative(4000, -4000, 1.0)!!.hidX).isEqualTo(4000)
    }

    @Test
    fun `a relative move leaves cooked and the edge flags alone`() {
        val state = attached()
        state.onEnter(0, 1206)
        state.completeAttach(newDevice = false, warpedToEnter = true)
        val flags = state.edgeFlags
        state.applySuccessfulPlan(state.planRelative(800, 0, 1.0)!!, 0L, 0)
        assertThat(state.cookedPosition()).isEqualTo(Pair(0.0, 1206.0))
        assertThat(state.edgeFlags).isEqualTo(flags)
        // Still anchored on the left edge, so a DMMV back there needs no pulse.
        state.updatePointerTarget(0, 1206)
        assertThat(MouseEdgeAnchor.plan(state.plannerInput(50_000_000L), 0).isNoOp).isTrue()
    }

    @Test
    fun `a relative move keeps a pending centre seed`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413).also { it.completeAttach(newDevice = true) }
        state.applySuccessfulPlan(state.planRelative(5, 0, 1.0)!!, 0L, 0)
        assertThat(state.needsCenterSeed()).isTrue()
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

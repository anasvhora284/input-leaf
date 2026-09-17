package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HidMouseStateTest {

    @Test
    fun `attaching is not yet attached but is attached-or-attaching`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.beginAttach()
        assertThat(state.attached).isFalse()
        assertThat(state.isAttachedOrAttaching()).isTrue()
        assertThat(state.phase).isEqualTo(HidMouseState.AttachPhase.ATTACHING)
    }

    @Test
    fun `pending enter is retained and consumed after attach completes`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.updatePointerTarget(520, 0)
        state.beginAttach()
        assertThat(state.phase).isEqualTo(HidMouseState.AttachPhase.ATTACHING)
        assertThat(state.peekPendingSnap()).isEqualTo(Pair(520, 0))

        state.completeAttach(newDevice = true)
        assertThat(state.attached).isTrue()
        assertThat(state.consumePendingSnap()).isEqualTo(Pair(520, 0))
    }

    @Test
    fun `DMMV during attach updates latest pending target`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.updatePointerTarget(520, 0)
        state.beginAttach()
        assertThat(state.queueTargetWhileAttaching(600, 100)).isTrue()
        assertThat(state.peekPendingSnap()).isEqualTo(Pair(600, 100))
    }

    @Test
    fun `idempotent attach does not reseed cooked estimate`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.completeAttach(newDevice = true)
        val before = state.cookedPosition()
        state.applySuccessfulMove(
            MousePointerCompensation.planHidMove(100, 0, 0, dtMs = 0L),
            nowMs = 1L,
        )
        val afterMove = state.cookedPosition()
        state.completeAttach(newDevice = false)
        assertThat(state.cookedPosition()).isEqualTo(afterMove)
        assertThat(state.cookedPosition()).isNotEqualTo(before)
    }

    @Test
    fun `resize while attached scales cooked and protocol coords`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.completeAttach(newDevice = true)
        state.updatePointerTarget(540, 1200)
        state.resizeDisplay(newMaxX = 1919, newMaxY = 1079)
        assertThat(state.protocolX).isEqualTo(960)
        assertThat(state.cookedX).isEqualTo(958)
        assertThat(state.edgeFlags).isEqualTo(MouseEdgeAnchor.Flags())
    }

    @Test
    fun `movement timing preserved across pointer speed updates`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.completeAttach(newDevice = true)
        state.applySuccessfulMove(
            MousePointerCompensation.planHidMove(100, 50, 0, dtMs = 0L),
            nowMs = 50L,
        )
        val before = state.cookedPosition()
        assertThat(state.movementDtMs(nowMs = 100L)).isEqualTo(50L)
        assertThat(state.cookedPosition()).isEqualTo(before)
    }

    @Test
    fun `successful move advances cooked estimate only when applied`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.completeAttach(newDevice = true)
        val plan = MousePointerCompensation.planHidMove(0, -200, 0, dtMs = 0L)
        state.applySuccessfulMove(plan, nowMs = 100L)
        val (_, cookedY) = state.cookedPosition()
        assertThat(cookedY).isEqualTo(1206 - 200)
    }

    @Test
    fun `applySuccessfulPlan updates cooked flags and last pulse on success only`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.completeAttach(newDevice = true)
        state.clearCenterSeed()
        state.updatePointerTarget(0, 200)
        val plan = MouseEdgeAnchor.plan(state.plannerInput(nowMs = 5L), 0)
        assertThat(plan.isEdgePulse).isTrue()
        assertThat(state.edgeFlags.minX).isFalse()
        state.applySuccessfulPlan(plan, nowMs = 5L)
        assertThat(state.cookedX).isEqualTo(0)
        assertThat(state.edgeFlags.minX).isTrue()
        assertThat(state.lastWasEdgePulse).isTrue()
    }

    @Test
    fun `stable leave discards pending so a later attach cannot reuse it`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.onEnter(0, 0)
        state.completeAttach(newDevice = true)
        state.snap()
        assertThat(state.cookedX).isEqualTo(0)
        assertThat(state.edgeFlags.minX).isTrue()

        state.markLeave()
        state.detach()
        assertThat(state.phase).isEqualTo(HidMouseState.AttachPhase.DETACHED)
        assertThat(state.peekPendingSnap()).isNull()
        assertThat(state.edgeFlags).isEqualTo(MouseEdgeAnchor.Flags())
        assertThat(state.needsCenterSeed()).isFalse()
    }

    @Test
    fun `stable leave then enter at a different edge snaps from fresh center`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.onEnter(0, 0)
        state.completeAttach(newDevice = true)
        state.snap()
        assertThat(state.cookedPosition()).isEqualTo(Pair(0, 0))

        state.markLeave()
        state.detach()
        assertThat(state.peekPendingSnap()).isNull()

        state.onEnter(1079, 2413)
        state.beginAttach()
        state.completeAttach(newDevice = true)
        assertThat(state.cookedPosition()).isEqualTo(Pair(state.centerX(), state.centerY()))
        assertThat(state.peekPendingSnap()).isEqualTo(Pair(1079, 2413))
        assertThat(state.edgeFlags).isEqualTo(MouseEdgeAnchor.Flags())
        assertThat(state.needsCenterSeed()).isTrue()

        val plans = state.snap()
        assertThat(plans.any { it.isEdgePulse && it.hidX == HidMouse.MAX_DELTA }).isTrue()
        assertThat(plans.any { it.isEdgePulse && it.hidY == HidMouse.MAX_DELTA }).isTrue()
        assertThat(state.cookedPosition()).isEqualTo(Pair(1079, 2413))
        assertThat(state.peekPendingSnap()).isNull()
    }

    @Test
    fun `enter after leave but before detach keeps the new target`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.onEnter(0, 50)
        state.completeAttach(newDevice = true)
        state.snap()

        state.markLeave()
        state.onEnter(1079, 2000)
        state.detach()
        assertThat(state.peekPendingSnap()).isEqualTo(Pair(1079, 2000))
        assertThat(state.protocolX).isEqualTo(1079)
        assertThat(state.protocolY).isEqualTo(2000)

        state.beginAttach()
        state.completeAttach(newDevice = true)
        assertThat(state.cookedPosition()).isEqualTo(Pair(state.centerX(), state.centerY()))
        assertThat(state.peekPendingSnap()).isEqualTo(Pair(1079, 2000))
        state.snap()
        assertThat(state.cookedX).isEqualTo(1079)
        assertThat(state.cookedY).isAtLeast(1500)
    }

    @Test
    fun `rapid re-enter while attached plans from current cooked not center`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.onEnter(0, 0)
        state.completeAttach(newDevice = true)
        state.snap()
        val leftTop = state.cookedPosition()
        assertThat(leftTop).isEqualTo(Pair(0, 0))

        state.markLeave()
        state.onEnter(1079, 1800)
        assertThat(state.attached).isTrue()
        assertThat(state.cookedPosition()).isEqualTo(leftTop)
        assertThat(state.needsCenterSeed()).isFalse()
        assertThat(state.edgeFlags).isEqualTo(MouseEdgeAnchor.Flags())

        val plans = MouseEdgeAnchor.planSnap(state.plannerInput(nowMs = 20L), 0)
        assertThat(plans.any { it.hidX == HidMouse.MAX_DELTA }).isTrue()
        plans.filter { !it.isNoOp }.forEach { state.applySuccessfulPlan(it, 20L) }
        assertThat(state.cookedX).isEqualTo(1079)
        assertThat(state.cookedY).isAtLeast(1000)
        assertThat(state.cookedY).isNotEqualTo(0)
    }

    @Test
    fun `DMMV during attach replaces a stale enter target`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.onEnter(0, 0)
        state.completeAttach(newDevice = true)
        state.snap()
        state.markLeave()
        state.detach()

        state.onEnter(100, 0)
        state.beginAttach()
        assertThat(state.queueTargetWhileAttaching(900, 2200)).isTrue()
        assertThat(state.peekPendingSnap()).isEqualTo(Pair(900, 2200))
        state.completeAttach(newDevice = true)
        assertThat(state.peekPendingSnap()).isEqualTo(Pair(900, 2200))
        assertThat(state.protocolX).isEqualTo(900)
        assertThat(state.protocolY).isEqualTo(2200)
    }

    @Test
    fun `left top anchors rearm for a new right bottom session`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.onEnter(0, 0)
        state.completeAttach(newDevice = true)
        state.snap()
        assertThat(state.edgeFlags.minX).isTrue()
        assertThat(state.edgeFlags.minY).isTrue()
        assertThat(state.edgeFlags.maxX).isFalse()
        assertThat(state.edgeFlags.maxY).isFalse()

        state.markLeave()
        state.detach()
        state.onEnter(1079, 2413)
        state.beginAttach()
        state.completeAttach(newDevice = true)
        assertThat(state.edgeFlags).isEqualTo(MouseEdgeAnchor.Flags())
        val plans = MouseEdgeAnchor.planSnap(state.plannerInput(nowMs = 1L), 0)
        assertThat(plans.any { it.hidX == HidMouse.MAX_DELTA }).isTrue()
        assertThat(plans.any { it.hidY == HidMouse.MAX_DELTA }).isTrue()
        assertThat(plans.none { it.hidX == HidMouse.MIN_DELTA }).isTrue()
        assertThat(plans.none { it.hidY == HidMouse.MIN_DELTA }).isTrue()
    }

    @Test
    fun `rotation between sessions uses new bounds for the next enter`() {
        val state = HidMouseState(maxX = 1079, maxY = 2413)
        state.onEnter(0, 0)
        state.completeAttach(newDevice = true)
        state.snap()
        state.markLeave()
        state.detach()

        state.resizeDisplay(newMaxX = 2413, newMaxY = 1079)
        assertThat(state.cookedPosition()).isEqualTo(Pair(1206, 539))
        assertThat(state.edgeFlags).isEqualTo(MouseEdgeAnchor.Flags())
        assertThat(state.peekPendingSnap()).isNull()

        state.onEnter(2413, 1079)
        state.beginAttach()
        state.completeAttach(newDevice = true)
        assertThat(state.cookedPosition()).isEqualTo(Pair(1206, 539))
        assertThat(state.peekPendingSnap()).isEqualTo(Pair(2413, 1079))
        state.snap()
        assertThat(state.cookedPosition()).isEqualTo(Pair(2413, 1079))
    }

    private fun HidMouseState.snap(nowMs: Long = 1L): List<MouseEdgeAnchor.Plan> {
        val plans = MouseEdgeAnchor.planSnap(plannerInput(nowMs), 0)
        for (plan in plans) {
            if (!plan.isNoOp) applySuccessfulPlan(plan, nowMs)
        }
        val target = peekPendingSnap() ?: return plans
        val (errX, errY) = errorToTarget(target.first, target.second)
        val stillEdge = MouseEdgeAnchor.needsEdgePulse(
            edgeFlags,
            target.first,
            target.second,
            maxXExclusive(),
            maxYExclusive(),
        )
        if (errX == 0 && errY == 0 && !stillEdge) {
            consumePendingSnap()
        }
        return plans
    }
}

package com.inputleaf.android.shizuku.uhid

/**
 * Hard edge anchoring for the relative UHID mouse.
 *
 * AOSP [MouseCursorController] clamps the cooked pointer to
 * `[0, width-1] × [0, height-1]`. Open-loop compensation can reach that max in
 * the predicted coordinate while the physical pointer still undershoots; later
 * DMMVs then become no-ops. A one-shot outward HID pulse on the boundary axis
 * lets Android's own clamp place the real pointer on that edge.
 *
 * Tangential correction is never combined with the pulse: CurvedVelocityControl
 * uses hypot(vx, vy), so a ±32767 edge delta would distort the other axis. Moves
 * right after a pulse get Android's top gain; [PointerVelocityModel] predicts that.
 */
internal object MouseEdgeAnchor {

    data class Flags(
        val minX: Boolean = false,
        val maxX: Boolean = false,
        val minY: Boolean = false,
        val maxY: Boolean = false,
    )

    data class Input(
        val targetX: Int,
        val targetY: Int,
        val cookedX: Double,
        val cookedY: Double,
        val maxX: Int,
        val maxY: Int,
        val flags: Flags,
        val preferTangentialFirst: Boolean,
        /** Android's acceleration state; only read while planning. */
        val velocity: PointerVelocityModel,
        val nowNs: Long,
    )

    data class Plan(
        val hidX: Int,
        val hidY: Int,
        val flagsAfter: Flags,
        val cookedXAfter: Double,
        val cookedYAfter: Double,
        val isEdgePulse: Boolean,
    ) {
        val isNoOp: Boolean get() = hidX == 0 && hidY == 0
    }

    fun atMin(value: Int): Boolean = value <= 0

    fun atMax(value: Int, max: Int): Boolean = value >= max

    fun rearm(flags: Flags, targetX: Int, targetY: Int, maxX: Int, maxY: Int): Flags = Flags(
        minX = flags.minX && atMin(targetX),
        maxX = flags.maxX && atMax(targetX, maxX),
        minY = flags.minY && atMin(targetY),
        maxY = flags.maxY && atMax(targetY, maxY),
    )

    fun needsEdgePulse(flags: Flags, targetX: Int, targetY: Int, maxX: Int, maxY: Int): Boolean {
        val armed = rearm(flags, targetX, targetY, maxX, maxY)
        return pendingPulses(targetX, targetY, maxX, maxY, armed).any
    }

    fun plan(input: Input, settingsSpeed: Int): Plan {
        val armed = rearm(input.flags, input.targetX, input.targetY, input.maxX, input.maxY)
        val pending = pendingPulses(input.targetX, input.targetY, input.maxX, input.maxY, armed)
        val errX = input.targetX - input.cookedX
        val errY = input.targetY - input.cookedY

        if (pending.any) {
            if (input.preferTangentialFirst) {
                val tangential = normalPlan(
                    errX = if (pending.x) 0.0 else errX,
                    errY = if (pending.y) 0.0 else errY,
                    settingsSpeed = settingsSpeed,
                    input = input,
                    flagsAfter = armed,
                )
                if (!tangential.isNoOp) return tangential
            }
            return edgePulse(pending, armed, input)
        }

        return normalPlan(
            errX = errX,
            errY = errY,
            settingsSpeed = settingsSpeed,
            input = input,
            flagsAfter = armed,
        )
    }

    /**
     * Attach/Enter may emit tangential compensation first, then remaining edge
     * pulses (one report per axis). Never pulse-then-tangential in this burst
     * (that was the park-then-snap bug).
     */
    fun planSnap(input: Input, settingsSpeed: Int): List<Plan> {
        val first = plan(input.copy(preferTangentialFirst = true), settingsSpeed)
        if (first.isNoOp) return listOf(first)
        val plans = mutableListOf(first)
        var current = followUpInput(input, first)
        if (!first.isEdgePulse) {
            val second = plan(current, settingsSpeed)
            if (second.isEdgePulse) {
                plans += second
                current = followUpInput(input, second)
            }
        }
        // Remaining boundary axes only. Never emit tangential after a pulse in this
        // burst (that reintroduces the park-then-snap velocity-tracker jump).
        while (
            plans.size < 3 &&
            needsEdgePulse(current.flags, current.targetX, current.targetY, current.maxX, current.maxY)
        ) {
            val pulse = plan(current.copy(preferTangentialFirst = false), settingsSpeed)
            if (!pulse.isEdgePulse) break
            plans += pulse
            current = followUpInput(input, pulse)
        }
        return plans
    }

    private fun followUpInput(original: Input, plan: Plan): Input = Input(
        targetX = original.targetX,
        targetY = original.targetY,
        cookedX = plan.cookedXAfter,
        cookedY = plan.cookedYAfter,
        maxX = original.maxX,
        maxY = original.maxY,
        flags = plan.flagsAfter,
        preferTangentialFirst = false,
        velocity = original.velocity,
        nowNs = original.nowNs,
    )

    private data class PendingPulses(
        val minX: Boolean,
        val maxX: Boolean,
        val minY: Boolean,
        val maxY: Boolean,
    ) {
        val x: Boolean get() = minX || maxX
        val y: Boolean get() = minY || maxY
        val any: Boolean get() = x || y
    }

    private fun pendingPulses(
        targetX: Int,
        targetY: Int,
        maxX: Int,
        maxY: Int,
        armed: Flags,
    ): PendingPulses {
        val minX = atMin(targetX) && !armed.minX
        // A 1-px axis is both min and max; pulse min only.
        val maxXPulse = maxX > 0 && atMax(targetX, maxX) && !armed.maxX && !minX
        val minY = atMin(targetY) && !armed.minY
        val maxYPulse = maxY > 0 && atMax(targetY, maxY) && !armed.maxY && !minY
        return PendingPulses(minX, maxXPulse, minY, maxYPulse)
    }

    private fun edgePulse(
        pending: PendingPulses,
        armed: Flags,
        input: Input,
    ): Plan {
        val hidX = when {
            pending.minX -> HidMouse.MIN_DELTA
            pending.maxX -> HidMouse.MAX_DELTA
            else -> 0
        }
        val hidY = when {
            pending.minY -> HidMouse.MIN_DELTA
            pending.maxY -> HidMouse.MAX_DELTA
            else -> 0
        }
        return Plan(
            hidX = hidX,
            hidY = hidY,
            flagsAfter = Flags(
                minX = armed.minX || pending.minX,
                maxX = armed.maxX || pending.maxX,
                minY = armed.minY || pending.minY,
                maxY = armed.maxY || pending.maxY,
            ),
            cookedXAfter = when {
                pending.minX -> 0.0
                pending.maxX -> input.maxX.toDouble()
                else -> input.cookedX
            },
            cookedYAfter = when {
                pending.minY -> 0.0
                pending.maxY -> input.maxY.toDouble()
                else -> input.cookedY
            },
            isEdgePulse = true,
        )
    }

    private fun normalPlan(
        errX: Double,
        errY: Double,
        settingsSpeed: Int,
        input: Input,
        flagsAfter: Flags,
    ): Plan {
        val move = MousePointerCompensation.plan(errX, errY, input.velocity, input.nowNs, settingsSpeed)
        return Plan(
            hidX = move.hidX,
            hidY = move.hidY,
            flagsAfter = flagsAfter,
            cookedXAfter = (input.cookedX + move.dx).coerceIn(0.0, input.maxX.toDouble()),
            cookedYAfter = (input.cookedY + move.dy).coerceIn(0.0, input.maxY.toDouble()),
            isEdgePulse = false,
        )
    }
}

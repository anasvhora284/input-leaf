package com.inputleaf.android.shizuku.uhid

/**
 * Enter compensation that must run on the process that owns the UHID fd.
 *
 * AOSP [PointerController] seeds a new mouse at display center. The Shizuku-era
 * mapper ([HidMouseState] + [MouseEdgeAnchor.planSnap]) warps from that center to
 * the last InputLeap Enter. Live DMMV tracking stays in the app process; this
 * only stores Enter until CREATE2 is START-ready, then emits the same snap.
 */
internal class HidMouseEnterWarp(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    data class Pending(
        val x: Int,
        val y: Int,
        val maxX: Int,
        val maxY: Int,
        val pointerSpeed: Int,
    )

    @Volatile
    var pending: Pending? = null
        private set

    fun onEnter(x: Int, y: Int, maxX: Int, maxY: Int, pointerSpeed: Int) {
        val width = maxX.coerceAtLeast(0)
        val height = maxY.coerceAtLeast(0)
        pending = Pending(
            x = x.coerceIn(0, width),
            y = y.coerceIn(0, height),
            maxX = width,
            maxY = height,
            pointerSpeed = pointerSpeed,
        )
    }

    fun onLeave() {
        pending = null
    }

    /**
     * Warp a newly CREATE2'd mouse from AOSP center to [pending].
     *
     * Cooked origin is always display center, never the stored Enter. Seeding
     * cooked at (x,y) makes [MouseEdgeAnchor.planSnap] a no-op and leaves the
     * native sprite at center. Keeps Enter coords if a HID write fails.
     */
    fun applyIfPending(mouse: HidMouse): List<MouseEdgeAnchor.Plan> {
        val target = pending ?: return emptyList()
        val state = HidMouseState(target.maxX, target.maxY)
        state.completeAttach(newDevice = true)
        state.onEnter(target.x, target.y)
        val now = nowMs()
        val input = state.plannerInput(now)
        val plans = MouseEdgeAnchor.planSnap(input, target.pointerSpeed)
        for (plan in plans) {
            if (plan.isNoOp) continue
            if (!mouse.move(plan.hidX, plan.hidY, 0, 0)) {
                return plans
            }
            state.applySuccessfulPlan(plan, now)
        }
        pending = null
        return plans
    }
}

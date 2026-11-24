package com.inputleaf.android.shizuku.uhid

/**
 * Moves a newly created virtual mouse to the last InputLeap Enter. Runs on the process
 * that owns the UHID fd, right after Android has registered the device.
 *
 * Where a new pointer starts isn't reliable (display centre on a fresh controller, the
 * old position when another mouse kept it), and a relative move is only predictable
 * when Android's velocity tracker has no recent samples. So the warp is a pulse into
 * the corner nearest the target (position now known whatever it was), a pause that
 * clears the tracker, then one move off the corner at the base gain (#88), and another
 * pause so the app's first move also starts on a clear tracker.
 */
internal class HidMouseEnterWarp(
    private val sleepMs: (Long) -> Unit = { Thread.sleep(it) },
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
     * Sends the warp for [pending], if any, and clears it either way so the app never
     * replays half of it. Returns the number of reports written, and whether all were.
     */
    fun applyIfPending(mouse: HidMouse): Result {
        val target = pending ?: return Result(written = 0, complete = false)
        pending = null
        val reports = plan(target)
        for ((index, report) in reports.withIndex()) {
            if (!mouse.move(report.first, report.second, 0, 0)) {
                return Result(written = index, complete = false)
            }
            sleepMs(SETTLE_MS)
        }
        return Result(written = reports.size, complete = true)
    }

    data class Result(val written: Int, val complete: Boolean)

    companion object {
        /**
         * Longer than VelocityTracker's 40 ms ASSUME_POINTER_STOPPED_TIME, after which
         * Android forgets the pulse and gives the next move the base gain. (The ~2x
         * overshoot once blamed on a short pause was the OnePlus cursor scale, #88.)
         */
        const val SETTLE_MS = PointerVelocityModel.ASSUME_STOPPED_NS / 1_000_000 + 10L

        /** HID deltas: a pulse into the nearest corner, then the move off it (if any). */
        fun plan(target: Pending): List<Pair<Int, Int>> {
            val left = target.x <= target.maxX / 2
            val top = target.y <= target.maxY / 2
            val pulse = Pair(
                if (left) HidMouse.MIN_DELTA else HidMouse.MAX_DELTA,
                if (top) HidMouse.MIN_DELTA else HidMouse.MAX_DELTA,
            )
            val gain = MousePointerCompensation.firstSegmentGain(target.pointerSpeed)
            val dx = MousePointerCompensation.screenDeltaToHid(
                if (left) target.x else target.x - target.maxX,
                gain,
            )
            val dy = MousePointerCompensation.screenDeltaToHid(
                if (top) target.y else target.y - target.maxY,
                gain,
            )
            return if (dx == 0 && dy == 0) listOf(pulse) else listOf(pulse, dx to dy)
        }
    }
}

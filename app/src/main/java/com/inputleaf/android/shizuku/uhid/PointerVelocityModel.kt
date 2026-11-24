package com.inputleaf.android.shizuku.uhid

import kotlin.math.hypot

/**
 * Android's mouse acceleration, copied from AOSP so a report's effect can be predicted
 * exactly (#88). CursorInputMapper multiplies counts by XScale; VelocityControl feeds the
 * running position to an LSQ2 VelocityTracker (100 ms / 20 samples, cleared after a 40 ms
 * gap, everything reset after 500 ms idle) and scales the move by the curve's gain at that
 * speed. Replaying a OnePlus capture through this matched the real pointer to 0.1 px.
 */
internal class PointerVelocityModel {
    private class Sample(val tNs: Long, val x: Double, val y: Double)

    private val samples = ArrayDeque<Sample>()
    private var rawX = 0.0
    private var rawY = 0.0
    private var lastMoveNs: Long? = null

    @Synchronized
    fun reset() {
        samples.clear()
        rawX = 0.0
        rawY = 0.0
        lastMoveNs = null
    }

    /** Pixels Android would move the pointer for [dx],[dy] counts sent at [tNs]. */
    @Synchronized
    fun predict(tNs: Long, dx: Double, dy: Double, settingsSpeed: Int): Pair<Double, Double> {
        if (dx == 0.0 && dy == 0.0) return 0.0 to 0.0
        val list = samplesAfter(tNs, dx, dy)
        val vx = velocity(list) { it.x }
        val vy = velocity(list) { it.y }
        val speedMmPerS = if (vx == null || vy == null) 0.0 else hypot(vx, vy) / MOUSE_CPI * MM_PER_INCH
        val gain = MousePointerCompensation.gainForSpeedMmPerS(speedMmPerS, settingsSpeed)
        return dx * gain to dy * gain
    }

    /** Records a report that was sent at [tNs]. */
    @Synchronized
    fun record(tNs: Long, dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        val list = samplesAfter(tNs, dx.toDouble(), dy.toDouble())
        samples.clear()
        samples.addAll(list)
        rawX = list.last().x
        rawY = list.last().y
        lastMoveNs = tNs
    }

    /** Android's tracker samples once a report of [dx],[dy] counts arrives at [tNs]. */
    private fun samplesAfter(tNs: Long, dx: Double, dy: Double): List<Sample> {
        val scale = MousePointerCompensation.cursorScale
        val last = lastMoveNs
        val idle = last == null || tNs - last >= STOP_TIME_NS
        val list = ArrayList<Sample>(HISTORY_SIZE + 1)
        if (!idle && tNs - last!! <= ASSUME_STOPPED_NS) list.addAll(samples)
        val x = (if (idle) 0.0 else rawX) + dx * scale
        val y = (if (idle) 0.0 else rawY) + dy * scale
        if (list.isNotEmpty() && list.last().tNs == tNs) list.removeAt(list.lastIndex)
        list.add(Sample(tNs, x, y))
        while (tNs - list.first().tNs > HORIZON_NS || list.size > HISTORY_SIZE) list.removeAt(0)
        return list
    }

    /** AOSP LeastSquaresVelocityTrackerStrategy(degree 2, unweighted); a line for 2 samples. */
    private inline fun velocity(list: List<Sample>, pos: (Sample) -> Double): Double? {
        val n = list.size
        if (n < 2) return null
        val newest = list.last().tNs
        if (n == 2) return (pos(list[1]) - pos(list[0])) / ((list[1].tNs - list[0].tNs) * 1e-9)
        var sx = 0.0; var sy = 0.0; var sx2 = 0.0; var sx3 = 0.0; var sx4 = 0.0; var sxy = 0.0; var sx2y = 0.0
        for (s in list) {
            val xi = -(newest - s.tNs) * 1e-9
            val yi = pos(s)
            sx += xi; sy += yi; sx2 += xi * xi; sx3 += xi * xi * xi; sx4 += xi * xi * xi * xi
            sxy += xi * yi; sx2y += xi * xi * yi
        }
        val sXX = sx2 - sx * sx / n
        val sXY = sxy - sx * sy / n
        val sXX2 = sx3 - sx * sx2 / n
        val sX2Y = sx2y - sx2 * sy / n
        val sX2X2 = sx4 - sx2 * sx2 / n
        val denominator = sXX * sX2X2 - sXX2 * sXX2
        return if (denominator == 0.0) null else (sXY * sX2X2 - sX2Y * sXX2) / denominator
    }

    companion object {
        const val STOP_TIME_NS = 500_000_000L
        const val ASSUME_STOPPED_NS = 40_000_000L
        private const val HORIZON_NS = 100_000_000L
        private const val HISTORY_SIZE = 20
        private const val MOUSE_CPI = 800.0
        private const val MM_PER_INCH = 25.4
    }
}

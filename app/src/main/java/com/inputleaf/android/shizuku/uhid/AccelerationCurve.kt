package com.inputleaf.android.shizuku.uhid

/**
 * Android's mouse acceleration curve, from AOSP `libs/input/AccelerationCurve.cpp`.
 *
 * The `PointerVelocityControlParameters` printed by `dumpsys input` are NOT this curve —
 * AOSP's own comment says those are "ignored" for mice. The real gain is a piecewise
 * function of pointer speed:
 *
 *     gain(v) = commonFactor * (baseGain + reciprocal / v)      // v in mm/s
 *
 * Verified against measurements on a CPH2661 (Android 16, pointer_speed 0):
 *
 * | how the counts were emitted        | measured gain | segment |
 * |------------------------------------|---------------|---------|
 * | isolated report, any size          | 2.042         | 0       |
 * | 100 reports back to back (~2.6k/s) | 3.267         | 2       |
 * | 20 reports back to back (~55k/s)   | 9.220         | 3       |
 *
 * Segment 0 has a zero reciprocal, which is why slow or isolated movement has a constant
 * gain of 2.0416 — the value an earlier build hard-coded for everything. That constant is
 * right only at low speed; at speed the cursor travels up to 4.5x further per count.
 */
internal class AccelerationCurve(private val commonFactor: Double = commonFactorFor(0)) {

    /** Gain (screen px travelled per count) for a device moving at [countsPerSecond]. */
    fun gainFor(countsPerSecond: Double): Double {
        val speed = countsPerSecond * MM_PER_COUNT
        if (speed <= 0.0) return commonFactor * SEGMENTS[0].baseGain
        val segment = SEGMENTS.first { speed < it.maxSpeedMmPerSecond }
        return commonFactor * (segment.baseGain + segment.reciprocal / speed)
    }

    /**
     * Counts to emit so the cursor travels [pixels], given that [recentCounts] were
     * already emitted in the last [windowSeconds].
     *
     * Circular — the counts emitted change the speed, which changes the gain — so it is
     * solved by iteration. The curve is smooth and monotonic, and a few passes settle well
     * inside one count.
     */
    fun countsFor(pixels: Double, recentCounts: Double, windowSeconds: Double): Double {
        if (pixels == 0.0) return 0.0
        val window = windowSeconds.coerceAtLeast(MIN_WINDOW_SECONDS)
        val target = kotlin.math.abs(pixels)

        // travel(counts) is monotonic — more counts means both more counts and a higher
        // gain — so bisect rather than iterate a fixed point, which oscillated and left
        // large moves ~8% short.
        fun travel(counts: Double) = counts * gainFor((recentCounts + counts) / window)

        // The gain is bounded by the curve, so the answer is bracketed.
        var lo = target / maxGain()
        var hi = target / minGain()
        repeat(BISECT_PASSES) {
            val mid = (lo + hi) / 2
            if (travel(mid) < target) lo = mid else hi = mid
        }
        val magnitude = (lo + hi) / 2
        return if (pixels < 0) -magnitude else magnitude
    }

    private fun minGain(): Double = commonFactor * SEGMENTS[0].baseGain

    private fun maxGain(): Double = commonFactor * SEGMENTS.last().baseGain

    private data class Segment(
        val maxSpeedMmPerSecond: Double,
        val baseGain: Double,
        val reciprocal: Double,
    )

    companion object {
        /** `MOUSE_CPI` is hardcoded to 800 in AOSP, so one count is 25.4/800 mm. */
        private const val MM_PER_COUNT = 25.4 / 800.0

        private val SEGMENTS = listOf(
            Segment(32.002, 3.19, 0.0),
            Segment(52.83, 4.79, -51.254),
            Segment(119.124, 7.28, -182.737),
            Segment(Double.MAX_VALUE, 15.04, -1107.556),
        )

        /** `kSensitivityFactors` from AccelerationCurve.cpp, indexed by pointer_speed + 7. */
        private val SENSITIVITY_FACTORS =
            intArrayOf(1, 2, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 16, 18, 20)

        fun commonFactorFor(pointerSpeed: Int): Double {
            val index = (pointerSpeed + 7).coerceIn(0, SENSITIVITY_FACTORS.lastIndex)
            return 0.64 * SENSITIVITY_FACTORS[index] / 10.0
        }

        /** Gain when nothing has moved recently; equals segment 0 and so is constant. */
        const val RESTING_GAIN = 2.0416

        private const val BISECT_PASSES = 40
        private const val MIN_WINDOW_SECONDS = 0.001
    }
}

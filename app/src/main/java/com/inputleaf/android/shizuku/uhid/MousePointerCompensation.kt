package com.inputleaf.android.shizuku.uhid

import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Open-loop inverse of AOSP Android 16 [CurvedVelocityControl], using [PointerVelocityModel]
 * to predict each report. There is no feedback from the real pointer.
 */
internal object MousePointerCompensation {

    private val SENSITIVITY_FACTORS =
        intArrayOf(1, 2, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 16, 18, 20)

    private const val FIRST_SEGMENT_BASE_GAIN = 3.19
    private const val SEGMENT0_MAX_SPEED_MM_PER_S = 32.002

    private val CURVE_SEGMENTS = arrayOf(
        CurveSegment(SEGMENT0_MAX_SPEED_MM_PER_S, FIRST_SEGMENT_BASE_GAIN, 0.0),
        CurveSegment(52.83, 4.79, -51.254),
        CurveSegment(119.124, 7.28, -182.737),
        CurveSegment(Double.POSITIVE_INFINITY, 15.04, -1107.556),
    )

    /**
     * The XScale Android's CursorInputMapper applies to our mouse before acceleration.
     * AOSP uses 1.0; OnePlus's Android 16 uses 1.75 on a 560 dpi screen (#88). Read from
     * `dumpsys input` by the injector process ([CursorScaleReader]); 1.0 until known.
     */
    @Volatile
    var cursorScale: Double = 1.0
        set(value) {
            field = if (value.isFinite() && value > 0.0) value else 1.0
        }

    private data class CurveSegment(
        val maxSpeedMmPerS: Double,
        val baseGain: Double,
        val reciprocal: Double,
    )

    /** Pixels per HID count at low speed, for a settings pointer speed (-7..7). */
    fun firstSegmentGain(settingsSpeed: Int): Double =
        cursorScale * segmentBaseGainFromEffectiveSpeed(settingsSpeed)

    /** Sensitivity scale factor for a pointer speed (-7..7). */
    internal fun commonFactorFromEffectiveSpeed(effectiveSpeed: Int): Double {
        val speed = effectiveSpeed.coerceIn(-7, 7)
        return 0.64 * SENSITIVITY_FACTORS[speed + 7] / 10.0
    }

    /** First-segment base gain of AOSP's curve, without [cursorScale]. */
    internal fun segmentBaseGainFromEffectiveSpeed(effectiveSpeed: Int): Double {
        return commonFactorFromEffectiveSpeed(effectiveSpeed) * FIRST_SEGMENT_BASE_GAIN
    }

    fun gainForSpeedMmPerS(speedMmPerS: Double, settingsSpeed: Int): Double {
        if (speedMmPerS.isNaN() || speedMmPerS <= 0.0) {
            return firstSegmentGain(settingsSpeed)
        }
        val factor = cursorScale * commonFactorFromEffectiveSpeed(settingsSpeed)
        val segment = CURVE_SEGMENTS.firstOrNull { speedMmPerS <= it.maxSpeedMmPerS } ?: CURVE_SEGMENTS.last()
        val base = factor * segment.baseGain
        val reciprocal = factor * segment.reciprocal
        return if (reciprocal == 0.0) base else base + reciprocal / speedMmPerS
    }

    /** One report: HID counts and the pixels Android will move the pointer for them. */
    data class Move(val hidX: Int, val hidY: Int, val dx: Double, val dy: Double) {
        val isNoOp: Boolean get() = hidX == 0 && hidY == 0
    }

    /**
     * Counts that move Android's pointer by about [errX],[errY] px when sent at [nowNs].
     * More counts mean more speed and so more gain, so it searches the counts per pixel.
     * ponytail: assumes the move grows with the counts, which holds unless the pointer
     * reverses inside Android's 100 ms window; the next report corrects any miss.
     */
    fun plan(errX: Double, errY: Double, model: PointerVelocityModel, nowNs: Long, settingsSpeed: Int): Move {
        val want = hypot(errX, errY)
        if (want < 0.5) return Move(0, 0, 0.0, 0.0)
        var lo = 0.0
        var hi = 1.0 / firstSegmentGain(settingsSpeed) // the curve never goes below this gain
        repeat(BISECT_STEPS) {
            val k = (lo + hi) / 2
            val (mx, my) = model.predict(nowNs, errX * k, errY * k, settingsSpeed)
            if (hypot(mx, my) < want) lo = k else hi = k
        }
        val k = (lo + hi) / 2
        val hidX = (errX * k).roundToInt().coerceIn(HidMouse.MIN_DELTA, HidMouse.MAX_DELTA)
        val hidY = (errY * k).roundToInt().coerceIn(HidMouse.MIN_DELTA, HidMouse.MAX_DELTA)
        if (hidX == 0 && hidY == 0) return Move(0, 0, 0.0, 0.0)
        val (dx, dy) = model.predict(nowNs, hidX.toDouble(), hidY.toDouble(), settingsSpeed)
        return Move(hidX, hidY, dx, dy)
    }

    private const val BISECT_STEPS = 30

    fun screenDeltaToHid(screenDelta: Int, gain: Double): Int {
        if (screenDelta == 0 || gain <= 0.0) return 0
        return (screenDelta / gain).roundToInt().coerceIn(HidMouse.MIN_DELTA, HidMouse.MAX_DELTA)
    }

    /** Scale a pointer coordinate when display size changes (same display, new bounds). */
    fun scaleCoord(value: Int, oldMax: Int, newMax: Int): Int {
        if (oldMax <= 0 || newMax <= 0) return value.coerceIn(0, newMax)
        return ((value.toLong() * newMax) / oldMax).toInt().coerceIn(0, newMax)
    }
}

package com.inputleaf.android.shizuku.uhid

import android.os.Build
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Open-loop inverse of AOSP Android 16 [CurvedVelocityControl].
 *
 * HID relative counts are not screen pixels. Predicted cooked position is model-based only —
 * there is no closed-loop correction from actual pointer feedback.
 */
internal object MousePointerCompensation {

    /** Mirrors [VelocityControl.h] STOP_TIME (500 ms). */
    const val STOP_TIME_MS = 500L

    private val SENSITIVITY_FACTORS =
        intArrayOf(1, 2, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 16, 18, 20)

    private const val FIRST_SEGMENT_BASE_GAIN = 3.19
    private const val SEGMENT0_MAX_SPEED_MM_PER_S = 32.002
    private const val MOUSE_CPI = 800.0
    private const val MM_PER_INCH = 25.4

    private val CURVE_SEGMENTS = arrayOf(
        CurveSegment(SEGMENT0_MAX_SPEED_MM_PER_S, FIRST_SEGMENT_BASE_GAIN, 0.0),
        CurveSegment(52.83, 4.79, -51.254),
        CurveSegment(119.124, 7.28, -182.737),
        CurveSegment(Double.POSITIVE_INFINITY, 15.04, -1107.556),
    )

    /** When true, [effectivePointerSpeedForGain] applies the OnePlus/OPPO -1 offset. */
    private var oplusFamilyRemapEnabled: Boolean = isOplusFamilyDevice()

    data class HidMovePlan(
        val hidX: Int,
        val hidY: Int,
        val gain: Double,
    ) {
        val isNoOp: Boolean get() = hidX == 0 && hidY == 0

        fun cookedDelta(): Pair<Int, Int> = cookedDeltaFromHid(hidX, hidY, gain)
    }

    private data class CurveSegment(
        val maxSpeedMmPerS: Double,
        val baseGain: Double,
        val reciprocal: Double,
    )

    /**
     * First-segment base gain for a **settings** pointer speed (-7..7).
     * OEM remap is applied exactly once.
     */
    fun firstSegmentGain(settingsSpeed: Int): Double {
        return segmentBaseGainFromEffectiveSpeed(effectivePointerSpeedForGain(settingsSpeed))
    }

    /**
     * Settings report pointer_speed=0 on OnePlus Nord 4 (Android 16), but measured
     * cooked_delta/hid_counts matches [firstSegmentGain] at speed -1 (1.838 vs 2.0416).
     *
     * @param settingsSpeed raw value from Settings.System pointer_speed
     * @return effective speed index for sensitivity lookup (-7..7)
     */
    fun effectivePointerSpeedForGain(settingsSpeed: Int): Int {
        val speed = settingsSpeed.coerceIn(-7, 7)
        if (oplusFamilyRemapEnabled) {
            return (speed - 1).coerceIn(-7, 7)
        }
        return speed
    }

    /** Sensitivity scale factor; [effectiveSpeed] must already be remapped. */
    internal fun commonFactorFromEffectiveSpeed(effectiveSpeed: Int): Double {
        val speed = effectiveSpeed.coerceIn(-7, 7)
        return 0.64 * SENSITIVITY_FACTORS[speed + 7] / 10.0
    }

    /** Sensitivity scale factor from a **settings** pointer speed (applies OEM remap once). */
    private fun commonFactorFromSettingsSpeed(settingsSpeed: Int): Double {
        return commonFactorFromEffectiveSpeed(effectivePointerSpeedForGain(settingsSpeed))
    }

    /** First-segment base gain; [effectiveSpeed] must already be remapped. */
    internal fun segmentBaseGainFromEffectiveSpeed(effectiveSpeed: Int): Double {
        return commonFactorFromEffectiveSpeed(effectiveSpeed) * FIRST_SEGMENT_BASE_GAIN
    }

    fun gainForHidSpeed(hidCountsPerSecond: Double, settingsSpeed: Int): Double {
        if (hidCountsPerSecond <= 0.0) {
            return firstSegmentGain(settingsSpeed)
        }
        val speedMmPerS = hidCountsPerSecond / MOUSE_CPI * MM_PER_INCH
        return gainForSpeedMmPerS(speedMmPerS, settingsSpeed)
    }

    fun gainForSpeedMmPerS(speedMmPerS: Double, settingsSpeed: Int): Double {
        val factor = commonFactorFromSettingsSpeed(settingsSpeed)
        // The last segment is terminated by +Inf, so some segment always matches and
        // there is no reachable fallback after this loop.
        val segment = CURVE_SEGMENTS.first { speedMmPerS <= it.maxSpeedMmPerS }
        val base = factor * segment.baseGain
        val reciprocal = factor * segment.reciprocal
        return if (reciprocal == 0.0) base else base + reciprocal / speedMmPerS
    }

    fun screenDeltaToHid(screenDelta: Int, gain: Double): Int {
        if (screenDelta == 0 || gain <= 0.0) return 0
        return (screenDelta / gain).roundToInt().coerceIn(HidMouse.MIN_DELTA, HidMouse.MAX_DELTA)
    }

    /**
     * Plan one HID report and the gain used to predict its cooked effect.
     * When [dtMs] is zero/unknown or >= [STOP_TIME_MS], AOSP resets velocity tracking
     * and the first-segment base gain applies.
     */
    fun planHidMove(
        errX: Int,
        errY: Int,
        settingsSpeed: Int,
        dtMs: Long,
    ): HidMovePlan {
        if (errX == 0 && errY == 0) {
            return HidMovePlan(0, 0, firstSegmentGain(settingsSpeed))
        }
        val gain = if (dtMs <= 0L || dtMs >= STOP_TIME_MS) {
            firstSegmentGain(settingsSpeed)
        } else {
            val prelimGain = firstSegmentGain(settingsSpeed)
            val prelimHidX = screenDeltaToHid(errX, prelimGain)
            val prelimHidY = screenDeltaToHid(errY, prelimGain)
            val hidPerSecond = hypot(prelimHidX.toDouble(), prelimHidY.toDouble()) * 1000.0 / dtMs
            gainForHidSpeed(hidPerSecond, settingsSpeed)
        }
        return HidMovePlan(
            hidX = screenDeltaToHid(errX, gain),
            hidY = screenDeltaToHid(errY, gain),
            gain = gain,
        )
    }

    fun cookedDeltaFromHid(hidX: Int, hidY: Int, gain: Double): Pair<Int, Int> {
        return Pair(
            (hidX * gain).roundToInt(),
            (hidY * gain).roundToInt(),
        )
    }

    /** Scale a pointer coordinate when display size changes (same display, new bounds). */
    fun scaleCoord(value: Int, oldMax: Int, newMax: Int): Int {
        if (oldMax <= 0 || newMax <= 0) return value.coerceIn(0, newMax)
        return ((value.toLong() * newMax) / oldMax).toInt().coerceIn(0, newMax)
    }

    internal fun setOplusFamilyRemapForTest(enabled: Boolean) {
        oplusFamilyRemapEnabled = enabled
    }

    internal fun resetVendorPolicyForTest() {
        oplusFamilyRemapEnabled = isOplusFamilyDevice()
    }

    private fun isOplusFamilyDevice(): Boolean {
        val vendor = Build.MANUFACTURER
        return vendor.equals("OnePlus", ignoreCase = true) || vendor.equals("OPPO", ignoreCase = true)
    }
}

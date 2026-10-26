package com.inputleaf.android.inject

/**
 * User scroll speed (#19), as a percentage of the wheel distance the server sends.
 * One setting covers both axes; 100% keeps the raw behaviour.
 */
object ScrollSpeed {
    const val DEFAULT = 100
    val OPTIONS = listOf(25, 50, 75, 100, 150, 200, 300, 400)

    /** Snaps a stored or out-of-range value to the nearest offered option. */
    fun fromStorage(value: Int?): Int =
        if (value == null) DEFAULT else OPTIONS.minBy { kotlin.math.abs(it - value) }

    fun multiplier(percent: Int): Double = fromStorage(percent) / 100.0

    fun label(percent: Int): String = when (val p = fromStorage(percent)) {
        DEFAULT -> "Normal (100%)"
        else -> "$p%"
    }
}

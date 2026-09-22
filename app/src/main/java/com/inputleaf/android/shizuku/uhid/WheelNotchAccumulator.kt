package com.inputleaf.android.shizuku.uhid

/**
 * Converts protocol wheel deltas into whole HID notches, carrying the remainder.
 *
 * HID reports scroll in notches, so `delta / 120` silently discards anything finer and
 * a 60-unit delta scrolls nothing at all. Input Leap normalises to 120 units, so a
 * conforming server never hits this, but a non-conforming one lost scroll input
 * entirely. Banking the remainder makes those deltas add up to a notch instead.
 *
 * Extracted rather than inlined into ShizukuInputInjector so the boundaries stay
 * reachable from the JVM suite -- that class needs a bound AIDL service to exercise.
 */
internal class WheelNotchAccumulator(private val unitsPerNotch: Int = 120) {

    private var remainder = 0

    /** @return whole notches to send now; 0 means the delta was banked, not dropped. */
    fun accept(delta: Int): Int {
        remainder += delta
        val notches = remainder / unitsPerNotch
        remainder -= notches * unitsPerNotch
        return notches
    }

    /** Drops any partial notch so it cannot leak into the next attach. */
    fun reset() {
        remainder = 0
    }
}

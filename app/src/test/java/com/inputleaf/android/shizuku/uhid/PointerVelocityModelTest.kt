package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class PointerVelocityModelTest {

    @Before
    fun onePlus() {
        MousePointerCompensation.cursorScale = 1.75
    }

    @After
    fun reset() {
        MousePointerCompensation.cursorScale = 1.0
    }

    /** Sends [counts] at [timesUs] and returns how far Android moved the pointer. */
    private fun replay(timesUs: LongArray, counts: IntArray): Double {
        val model = PointerVelocityModel()
        var moved = 0.0
        for (i in counts.indices) {
            val t = timesUs[i] * 1_000
            moved += model.predict(t, counts[i].toDouble(), 0.0, SPEED).first
            model.record(t, counts[i], 0)
        }
        return moved
    }

    @Test
    fun `replaying a OnePlus capture gives the measured pointer moves (88)`() {
        // getevent from a Nord 4 (Android 16, XScale 1.75, speed -1, acceleration on);
        // the pointer positions came from dumpsys input.
        val slowTimes = longArrayOf(
            0, 28728, 59892, 87596, 113141, 186223, 186955, 208802, 279262, 284129, 291352, 322571,
            352307, 381312, 408068, 435667, 462465, 492128, 523163, 548998, 572188, 598101, 625932,
            655636, 685541, 717900, 747815, 781204, 804766, 837993, 866394, 897031, 925418, 959345,
            988659, 1017569, 1049889, 1076034, 1096060, 1123654, 1150160, 1177717, 1204729, 1231464,
            1257318, 1288261, 1318135, 1397708, 1401641, 1408447, 1489199, 1490029, 1490720, 1520569,
            1545914, 1583663, 1605325, 1628927, 1648224, 1676142,
        )
        val slowCounts = intArrayOf(
            2, 2, 1, 2, 2, 1, 1, 2, 2, 1, 2, 2, 1, 2, 2, 1, 2, 2, 1, 2, 2, 1, 2, 2, 1, 2, 2, 1, 2, 2,
            1, 2, 2, 1, 2, 2, 1, 2, 2, 1, 2, 2, 1, 2, 2, 1, 2, 2, 1, 2, 1, 1, 2, 2, 2, 1, 2, 2, 1, 2,
        )
        assertThat(replay(slowTimes, slowCounts)).isWithin(0.5).of(341.1)

        val flickTimes = longArrayOf(
            0, 13828, 25951, 38266, 47331, 59839, 70899, 81024, 95784, 107528, 120493, 129474,
            140977, 152189, 162805, 174278, 187519, 196024, 206254, 222199,
        )
        val flickCounts = intArrayOf(
            -12, -11, -10, -10, -8, -10, -9, -9, -10, -10, -10, -8, -9, -10, -9, -10, -10, -8, -9, -11,
        )
        assertThat(replay(flickTimes, flickCounts)).isWithin(0.5).of(-708.1)
    }

    @Test
    fun `a move right after an edge pulse gets the top gain, and 40 ms later the base gain`() {
        val model = PointerVelocityModel()
        model.record(0L, 0, HidMouse.MIN_DELTA)
        model.record(1_000_000L, 0, HidMouse.MIN_DELTA)
        val base = MousePointerCompensation.firstSegmentGain(SPEED)
        val soon = model.predict(3_000_000L, 0.0, 10.0, SPEED).second / 10
        assertThat(soon).isGreaterThan(4 * base)
        val later = model.predict(42_000_000L, 0.0, 10.0, SPEED).second / 10
        assertThat(later).isWithin(1e-9).of(base)
    }

    private companion object {
        const val SPEED = -1
    }
}

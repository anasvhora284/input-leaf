package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccelerationCurveTest {

    private val curve = AccelerationCurve()

    @Test
    fun `a resting pointer gets the segment zero constant`() {
        // Measured: an isolated report of 200, 400 or 500 counts all travelled at 2.042.
        assertThat(curve.gainFor(0.0)).isWithin(0.001).of(2.0416)
    }

    @Test
    fun `the gain stays constant across all of segment zero`() {
        // Segment 0 ends at 32.002 mm/s = 1007.9 counts/s, and has no reciprocal term.
        assertThat(curve.gainFor(100.0)).isWithin(0.001).of(2.0416)
        assertThat(curve.gainFor(900.0)).isWithin(0.001).of(2.0416)
    }

    @Test
    fun `the measured mid-rate burst matches segment two`() {
        // 100 counts emitted as 100 back-to-back reports measured 3.267.
        // Solving the curve, that is ~84 mm/s = ~2646 counts/s.
        assertThat(curve.gainFor(2646.0)).isWithin(0.15).of(3.267)
    }

    @Test
    fun `the measured fast burst matches segment three`() {
        // 100 counts emitted as 20 back-to-back reports measured 9.220.
        assertThat(curve.gainFor(55_000.0)).isWithin(0.4).of(9.220)
    }

    @Test
    fun `the gain rises with speed and saturates`() {
        val slow = curve.gainFor(500.0)
        val mid = curve.gainFor(3000.0)
        val fast = curve.gainFor(50_000.0)
        assertThat(slow).isLessThan(mid)
        assertThat(mid).isLessThan(fast)
        // Segment 3 tends to commonFactor * 15.04 = 9.6256.
        assertThat(curve.gainFor(10_000_000.0)).isWithin(0.01).of(9.6256)
    }

    @Test
    fun `countsFor inverts the curve`() {
        // Whatever counts it returns, running them back through the gain must reproduce
        // the requested pixel travel.
        for (pixels in listOf(5.0, 40.0, 200.0, 900.0)) {
            for (recent in listOf(0.0, 50.0, 400.0)) {
                val window = 0.1
                val counts = curve.countsFor(pixels, recent, window)
                val rate = (recent + kotlin.math.abs(counts)) / window
                val travelled = counts * curve.gainFor(rate)
                assertThat(travelled).isWithin(pixels * 0.02 + 0.5).of(pixels)
            }
        }
    }

    @Test
    fun `countsFor needs fewer counts when the pointer is already moving fast`() {
        val atRest = curve.countsFor(200.0, recentCounts = 0.0, windowSeconds = 0.1)
        val moving = curve.countsFor(200.0, recentCounts = 800.0, windowSeconds = 0.1)
        assertThat(moving).isLessThan(atRest)
    }

    @Test
    fun `negative travel is symmetric`() {
        val forward = curve.countsFor(300.0, 0.0, 0.1)
        val back = curve.countsFor(-300.0, 0.0, 0.1)
        assertThat(back).isWithin(0.001).of(-forward)
    }

    @Test
    fun `zero travel needs no counts`() {
        assertThat(curve.countsFor(0.0, 100.0, 0.1)).isEqualTo(0.0)
    }

    @Test
    fun `pointer speed scales the whole curve`() {
        // kSensitivityFactors[7] = 10 at speed 0; [14] = 20 at speed 7, so exactly double.
        val fast = AccelerationCurve(AccelerationCurve.commonFactorFor(7))
        assertThat(fast.gainFor(0.0)).isWithin(0.001).of(2.0416 * 2)
    }
}

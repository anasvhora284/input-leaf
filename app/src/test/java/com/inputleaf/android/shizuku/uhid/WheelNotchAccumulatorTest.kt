package com.inputleaf.android.shizuku.uhid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WheelNotchAccumulatorTest {

    @Test
    fun `a conforming 120-unit delta is one notch`() {
        val acc = WheelNotchAccumulator()
        assertThat(acc.accept(120)).isEqualTo(1)
        assertThat(acc.accept(-120)).isEqualTo(-1)
    }

    @Test
    fun `sub-notch deltas are banked rather than dropped`() {
        val acc = WheelNotchAccumulator()
        // The regression: each of these used to truncate to 0 and scroll nothing.
        assertThat(acc.accept(60)).isEqualTo(0)
        assertThat(acc.accept(60)).isEqualTo(1)
    }

    @Test
    fun `banking works symmetrically for negative deltas`() {
        val acc = WheelNotchAccumulator()
        assertThat(acc.accept(-60)).isEqualTo(0)
        assertThat(acc.accept(-60)).isEqualTo(-1)
    }

    @Test
    fun `opposite partial deltas cancel instead of scrolling`() {
        val acc = WheelNotchAccumulator()
        assertThat(acc.accept(60)).isEqualTo(0)
        assertThat(acc.accept(-60)).isEqualTo(0)
        assertThat(acc.accept(120)).isEqualTo(1)
    }

    @Test
    fun `a large delta yields every whole notch at once and keeps the rest`() {
        val acc = WheelNotchAccumulator()
        assertThat(acc.accept(370)).isEqualTo(3)
        assertThat(acc.accept(110)).isEqualTo(1)
    }

    @Test
    fun `reset drops a partial notch so it cannot leak into the next attach`() {
        val acc = WheelNotchAccumulator()
        assertThat(acc.accept(60)).isEqualTo(0)
        acc.reset()
        assertThat(acc.accept(60)).isEqualTo(0)
    }

    @Test
    fun `no accumulated drift over a long conforming stream`() {
        val acc = WheelNotchAccumulator()
        var total = 0
        repeat(1000) { total += acc.accept(120) }
        assertThat(total).isEqualTo(1000)
    }
}

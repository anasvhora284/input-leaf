package com.inputleaf.android.inject

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScrollSpeedTest {
    @Test
    fun `default is raw speed`() {
        assertThat(ScrollSpeed.fromStorage(null)).isEqualTo(100)
        assertThat(ScrollSpeed.multiplier(ScrollSpeed.DEFAULT)).isEqualTo(1.0)
        assertThat(ScrollSpeed.label(100)).isEqualTo("Normal (100%)")
    }

    @Test
    fun `stored values snap to the nearest option, out of range included`() {
        assertThat(ScrollSpeed.fromStorage(160)).isEqualTo(150)
        assertThat(ScrollSpeed.fromStorage(0)).isEqualTo(25)
        assertThat(ScrollSpeed.fromStorage(10_000)).isEqualTo(400)
        assertThat(ScrollSpeed.multiplier(50)).isEqualTo(0.5)
        assertThat(ScrollSpeed.label(300)).isEqualTo("300%")
    }
}

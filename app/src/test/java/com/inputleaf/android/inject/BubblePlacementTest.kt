package com.inputleaf.android.inject

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BubblePlacementTest {
    @Test
    fun `snaps to the nearer side edge`() {
        assertThat(BubblePlacement.snapX(x = 100, size = 120, screenWidth = 1240)).isEqualTo(0)
        assertThat(BubblePlacement.snapX(x = 700, size = 120, screenWidth = 1240)).isEqualTo(1120)
        assertThat(BubblePlacement.snapX(x = -50, size = 120, screenWidth = 1240)).isEqualTo(0)
        assertThat(BubblePlacement.snapX(x = 2000, size = 120, screenWidth = 1240)).isEqualTo(1120)
    }

    @Test
    fun `stays fully on screen vertically`() {
        assertThat(BubblePlacement.clampY(y = -30, size = 120, screenHeight = 2772)).isEqualTo(0)
        assertThat(BubblePlacement.clampY(y = 5000, size = 120, screenHeight = 2772)).isEqualTo(2652)
        assertThat(BubblePlacement.clampY(y = 900, size = 120, screenHeight = 2772)).isEqualTo(900)
        assertThat(BubblePlacement.defaultY(size = 120, screenHeight = 2772)).isEqualTo(1524)
    }
}

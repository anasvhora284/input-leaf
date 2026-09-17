package com.inputleaf.android.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PointerSpeedTest {

    @Test
    fun `clampPointerSpeed enforces Android range`() {
        assertThat(clampPointerSpeed(-10)).isEqualTo(POINTER_SPEED_MIN)
        assertThat(clampPointerSpeed(0)).isEqualTo(0)
        assertThat(clampPointerSpeed(10)).isEqualTo(POINTER_SPEED_MAX)
    }
}

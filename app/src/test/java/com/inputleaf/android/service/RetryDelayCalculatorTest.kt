package com.inputleaf.android.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RetryDelayCalculatorTest {

    @Test
    fun `first attempt has 5 seconds delay`() {
        val delay = RetryDelayCalculator.getDelay(0)
        assertThat(delay).isEqualTo(5_000L)
    }

    @Test
    fun `subsequent attempts scale delays correctly`() {
        assertThat(RetryDelayCalculator.getDelay(1)).isEqualTo(10_000L)
        assertThat(RetryDelayCalculator.getDelay(2)).isEqualTo(20_000L)
        assertThat(RetryDelayCalculator.getDelay(3)).isEqualTo(40_000L)
        assertThat(RetryDelayCalculator.getDelay(4)).isEqualTo(60_000L)
    }

    @Test
    fun `attempts exceeding delays list max out at 60 seconds`() {
        assertThat(RetryDelayCalculator.getDelay(5)).isEqualTo(60_000L)
        assertThat(RetryDelayCalculator.getDelay(10)).isEqualTo(60_000L)
    }

    @Test
    fun `negative attempts default to 5 seconds`() {
        assertThat(RetryDelayCalculator.getDelay(-1)).isEqualTo(5_000L)
    }
}

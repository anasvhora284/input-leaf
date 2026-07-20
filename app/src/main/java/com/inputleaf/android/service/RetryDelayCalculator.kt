package com.inputleaf.android.service

object RetryDelayCalculator {
    private val DELAYS = listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)

    fun getDelay(attempt: Int): Long {
        if (attempt < 0) return DELAYS[0]
        return DELAYS[minOf(attempt, DELAYS.lastIndex)]
    }
}

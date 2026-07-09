package com.inputleaf.android.service

object RetryDelayCalculator {
    private val DELAYS = listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L)

    fun getDelay(attempt: Int): Long {
        if (attempt < 0) return DELAYS[0]
        return DELAYS[minOf(attempt, DELAYS.lastIndex)]
    }
}

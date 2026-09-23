package com.inputleaf.android.service

/**
 * Reconnect backoff, per attempt.
 *
 * Kept as a standalone object rather than inlined into [ConnectionService] so the
 * table and its boundaries stay reachable from the JVM suite: logic inside a Service
 * method is unmeasurable, so a patch-coverage gate passes over it without asserting
 * anything.
 */
internal object RetryDelayCalculator {
    private val DELAYS = listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)

    /** Clamps both ends: a negative attempt takes the first delay, overflow the last. */
    fun getDelay(attempt: Int): Long = DELAYS[attempt.coerceIn(0, DELAYS.lastIndex)]
}

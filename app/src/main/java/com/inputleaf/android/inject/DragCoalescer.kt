package com.inputleaf.android.inject

/**
 * Keeps an Accessibility drag in step with the mouse (#70).
 *
 * A drag is a chain of gesture segments, and Android plays each one for its whole
 * duration. Mice report far more often than that, so sending a segment per move queued
 * them up and the drag replayed every frame, falling further behind. Instead only one
 * segment is in flight at a time: moves that arrive meanwhile just update the target,
 * and the next segment jumps straight from where the last one ended to the newest
 * position.
 */
internal class DragCoalescer {
    data class Segment(val fromX: Float, val fromY: Float, val toX: Float, val toY: Float, val last: Boolean)

    private var active = false
    private var inFlight = false
    private var releasePending = false
    private var endX = 0f
    private var endY = 0f
    private var targetX = 0f
    private var targetY = 0f

    /** The press: the caller starts the stroke itself; returns nothing to send. */
    @Synchronized
    fun onDown(x: Float, y: Float) {
        active = true
        inFlight = true // the opening touch-down stroke is playing
        releasePending = false
        endX = x; endY = y
        targetX = x; targetY = y
    }

    /** @return a segment to send now, or null when one is already playing or nothing moved. */
    @Synchronized
    fun onMove(x: Float, y: Float): Segment? {
        if (!active) return null
        targetX = x; targetY = y
        return if (inFlight) null else next(last = false)
    }

    /** @return the closing segment now, or null if it must wait for the playing one. */
    @Synchronized
    fun onUp(x: Float, y: Float): Segment? {
        if (!active) return null
        targetX = x; targetY = y
        if (inFlight) {
            releasePending = true
            return null
        }
        return next(last = true)
    }

    /** The playing segment finished: @return the next one to send, if any. */
    @Synchronized
    fun onSegmentDone(): Segment? {
        if (!active) return null
        inFlight = false
        if (releasePending) return next(last = true)
        return if (moved()) next(last = false) else null
    }

    /** The system cancelled the gesture (for example a real touch); the drag is over. */
    @Synchronized
    fun onCancelled() {
        active = false
        inFlight = false
        releasePending = false
    }

    private fun moved() = kotlin.math.abs(targetX - endX) >= MIN_STEP || kotlin.math.abs(targetY - endY) >= MIN_STEP

    private fun next(last: Boolean): Segment? {
        // A stroke cannot continue with an empty path; there is nothing to send mid-drag.
        if (!last && !moved()) return null
        val segment = Segment(endX, endY, targetX, targetY, last)
        endX = targetX; endY = targetY
        inFlight = !last
        if (last) {
            active = false
            releasePending = false
        }
        return segment
    }

    private companion object {
        const val MIN_STEP = 0.1f
    }
}

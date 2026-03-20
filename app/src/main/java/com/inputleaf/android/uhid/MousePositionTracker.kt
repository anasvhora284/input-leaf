package com.inputleaf.android.uhid

class MousePositionTracker {
    @Volatile private var lastX: Int? = null
    @Volatile private var lastY: Int? = null

    @Synchronized fun updateAbsolute(x: Int, y: Int): Pair<Int, Int> {
        val dx = x - (lastX ?: 0)
        val dy = y - (lastY ?: 0)
        lastX = x; lastY = y
        return dx to dy
    }

    fun updateRelative(dx: Int, dy: Int): Pair<Int, Int> = dx to dy

    @Synchronized fun reset() { lastX = null; lastY = null }
}

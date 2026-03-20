package com.inputleaf.android.uhid

class MousePositionTracker {
    private var lastX: Int? = null
    private var lastY: Int? = null

    fun updateAbsolute(x: Int, y: Int): Pair<Int, Int> {
        val dx = x - (lastX ?: 0)
        val dy = y - (lastY ?: 0)
        lastX = x; lastY = y
        return dx to dy
    }

    fun updateRelative(dx: Int, dy: Int): Pair<Int, Int> = dx to dy

    fun reset() { lastX = null; lastY = null }
}

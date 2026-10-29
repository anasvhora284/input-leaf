package com.inputleaf.android.inject

/** Where the keyboard bubble rests: against the nearer side edge, fully on screen. */
internal object BubblePlacement {
    /** Left edge (0) or right edge, whichever the bubble's centre is closer to. */
    fun snapX(x: Int, size: Int, screenWidth: Int): Int =
        if (x + size / 2 < screenWidth / 2) 0 else (screenWidth - size).coerceAtLeast(0)

    fun clampY(y: Int, size: Int, screenHeight: Int): Int = y.coerceIn(0, (screenHeight - size).coerceAtLeast(0))

    /** First placement: right edge, a little below the middle, clear of most keyboards' tops. */
    fun defaultY(size: Int, screenHeight: Int): Int = clampY(screenHeight * 55 / 100, size, screenHeight)
}

package com.inputleaf.android.service

import kotlin.math.roundToInt

/**
 * Where the arrow tip sits inside the cursor overlay, so the tip (not the view's centre or
 * top-left) lands on the injected touch point.
 *
 * Tips are fractions of the drawable's own width and height, so they do not depend on screen
 * density or on the overlay size. The overlay is an ImageView with FIT_CENTER, which scales the
 * drawable to fit the square window and centres it; [tipInWindow] reproduces that, then applies
 * the horizontal flip used for [mirrored] shapes (scaleX = -1 about the view centre).
 */
enum class CursorHotspot(
    /** drawable width / height */
    val aspect: Float,
    val tipX: Float,
    val tipY: Float,
    val mirrored: Boolean,
) {
    /** ic_cursor_aosp_fill.xml / _outline.xml: 24x24 viewport, tip = top-left corner of the black body at (3.9, 3.7). */
    DEFAULT(aspect = 1f, tipX = 3.9f / 24f, tipY = 3.7f / 24f, mirrored = false),

    /**
     * cursor.xml: 493x512 viewport, outer tip of the leaf at (425, 68); drawn flipped so it
     * points up-left. The leaf, not the white arrow inside it, is what reads as the pointer.
     */
    LEAF(aspect = 493f / 512f, tipX = 425f / 493f, tipY = 68f / 512f, mirrored = true);

    /** Tip position in window pixels, measured from the window's top-left, for a square window. */
    fun tipInWindow(windowSize: Int): Pair<Float, Float> {
        val h = windowSize.toFloat() // aspect <= 1, so height is the fitting side
        val w = h * aspect
        val x = (windowSize - w) / 2f + tipX * w
        val y = (windowSize - h) / 2f + tipY * h
        return (if (mirrored) windowSize - x else x) to y
    }

    /** Window top-left (WindowManager x, y) that puts the tip exactly on ([x], [y]). */
    fun windowOrigin(x: Float, y: Float, windowSize: Int): Pair<Int, Int> {
        val (tx, ty) = tipInWindow(windowSize)
        return (x - tx).roundToInt() to (y - ty).roundToInt()
    }

    companion object {
        /**
         * Overlay size in dp, matching both cursor drawables' intrinsic size so they are drawn
         * 1:1 and stay sharp. Being dp, the cursor is the same physical size on every screen
         * (#71); it equals the old fixed 80 px on an xhdpi (2.0) screen.
         */
        const val WINDOW_SIZE_DP = 40

        fun windowSizePx(density: Float): Int = (WINDOW_SIZE_DP * density).roundToInt()

        fun forStyle(style: String): CursorHotspot = if (style == "leaf") LEAF else DEFAULT
    }
}

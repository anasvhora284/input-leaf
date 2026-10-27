package com.inputleaf.android.service

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import com.inputleaf.android.R

/** Shared view/window set-up for the two overlay hosts (CursorOverlayService, AccessibilityInputService). */
object CursorOverlayView {
    fun create(context: Context, style: String): ImageView =
        ImageView(context).apply {
            visibility = android.view.View.INVISIBLE
            applyStyle(this, style)
        }

    fun applyStyle(view: ImageView, style: String) {
        val shape = CursorHotspot.forStyle(style)
        view.setImageResource(if (shape == CursorHotspot.LEAF) R.drawable.cursor else R.drawable.ic_cursor_aosp)
        view.scaleX = if (shape.mirrored) -1f else 1f
        // The leaf PNG is ~6x larger than the overlay; mipmaps keep the downscale smooth.
        (view.drawable as? BitmapDrawable)?.setMipMap(true)
    }

    fun layoutParams(type: Int, style: String, x: Float, y: Float): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            CursorHotspot.WINDOW_SIZE_PX,
            CursorHotspot.WINDOW_SIZE_PX,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            // LEFT, not START: START mirrors to the right edge on RTL locales and shifts every position.
            gravity = Gravity.TOP or Gravity.LEFT
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                // Otherwise a display cutout can move the window origin away from the screen's (0, 0).
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            place(this, style, x, y)
        }

    /** Position [params] so the arrow tip is at ([x], [y]). Used for both the first show and every move. */
    fun place(params: WindowManager.LayoutParams, style: String, x: Float, y: Float) {
        val (wx, wy) = CursorHotspot.forStyle(style).windowOrigin(x, y, CursorHotspot.WINDOW_SIZE_PX)
        params.x = wx
        params.y = wy
    }
}

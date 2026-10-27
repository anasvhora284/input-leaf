package com.inputleaf.android.service

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import com.inputleaf.android.R

/** Shared view/window set-up for the two overlay hosts (CursorOverlayService, AccessibilityInputService). */
object CursorOverlayView {
    fun create(context: Context, look: CursorLook): ImageView =
        ImageView(context).apply {
            visibility = android.view.View.INVISIBLE
            applyLook(this, look)
        }

    fun applyLook(view: ImageView, look: CursorLook) {
        view.setImageDrawable(drawable(view.context, look))
        view.scaleX = if (look.hotspot.mirrored) -1f else 1f
    }

    private fun drawable(context: Context, look: CursorLook): Drawable {
        if (look.hotspot == CursorHotspot.LEAF) return checkNotNull(context.getDrawable(R.drawable.cursor))
        fun layer(res: Int, color: CursorColor) =
            checkNotNull(context.getDrawable(res)).mutate().apply { setTint(color.argb) }
        return LayerDrawable(
            arrayOf(
                layer(R.drawable.ic_cursor_aosp_fill, look.fill),
                layer(R.drawable.ic_cursor_aosp_outline, look.outline),
            ),
        )
    }

    fun layoutParams(context: Context, type: Int, look: CursorLook, x: Float, y: Float): WindowManager.LayoutParams {
        val size = look.windowSizePx(context.resources.displayMetrics.density)
        return WindowManager.LayoutParams(
            size,
            size,
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
            place(this, look, x, y)
        }
    }

    /** Match the window to [look]'s size; the caller then places and updates the layout. */
    fun resize(params: WindowManager.LayoutParams, context: Context, look: CursorLook) {
        val size = look.windowSizePx(context.resources.displayMetrics.density)
        params.width = size
        params.height = size
    }

    /** Position [params] so the arrow tip is at ([x], [y]). Used for both the first show and every move. */
    fun place(params: WindowManager.LayoutParams, look: CursorLook, x: Float, y: Float) {
        val (wx, wy) = look.hotspot.windowOrigin(x, y, params.width)
        params.x = wx
        params.y = wy
    }
}

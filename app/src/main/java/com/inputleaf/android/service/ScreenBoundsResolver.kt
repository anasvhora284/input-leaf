package com.inputleaf.android.service

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.WindowManager

object ScreenBoundsResolver {
    private const val TAG = "ScreenBoundsResolver"

    @Suppress("DEPRECATION")
    fun resolveFromContext(context: Context): Rect {
        // Tier 1: WindowMetrics from WindowManager (API 30+)
        val wmBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val wm = context.getSystemService(WindowManager::class.java)
                wm?.currentWindowMetrics?.bounds
            }.onFailure { e ->
                Log.w(TAG, "Tier 1: WindowManager.currentWindowMetrics failed: ${e.message}")
            }.getOrNull()
        } else null

        // Tier 2: DisplayManager default display real size
        val displayRealSize = runCatching {
            val dm = context.getSystemService(DisplayManager::class.java)
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            if (display != null) {
                val pt = Point()
                display.getRealSize(pt)
                pt
            } else null
        }.onFailure { e ->
            Log.w(TAG, "Tier 2: DisplayManager.getRealSize failed: ${e.message}")
        }.getOrNull()

        // Tier 2b: WindowManager defaultDisplay (legacy)
        val legacyDisplaySize = runCatching {
            val wm = context.getSystemService(WindowManager::class.java)
            val display = wm?.defaultDisplay
            if (display != null) {
                val pt = Point()
                display.getSize(pt)
                pt
            } else null
        }.onFailure { e ->
            Log.w(TAG, "Tier 2b: WindowManager.defaultDisplay.getSize failed: ${e.message}")
        }.getOrNull()

        // Tier 3: Resources displayMetrics
        val fallbackMetrics = runCatching {
            context.resources?.displayMetrics
        }.onFailure { e ->
            Log.w(TAG, "Tier 3: context.resources.displayMetrics failed: ${e.message}")
        }.getOrNull()

        return resolveBounds(
            windowMetricsBounds = wmBounds,
            displayRealSize = displayRealSize,
            legacyDisplaySize = legacyDisplaySize,
            fallbackMetricsWidth = fallbackMetrics?.widthPixels,
            fallbackMetricsHeight = fallbackMetrics?.heightPixels,
        )
    }

    fun resolveBounds(
        windowMetricsBounds: Rect?,
        displayRealSize: Point?,
        legacyDisplaySize: Point?,
        fallbackMetricsWidth: Int?,
        fallbackMetricsHeight: Int?,
    ): Rect {
        if (windowMetricsBounds != null && windowMetricsBounds.width() > 0 && windowMetricsBounds.height() > 0) {
            return Rect(0, 0, windowMetricsBounds.width(), windowMetricsBounds.height())
        }
        if (displayRealSize != null && displayRealSize.x > 0 && displayRealSize.y > 0) {
            return Rect(0, 0, displayRealSize.x, displayRealSize.y)
        }
        if (legacyDisplaySize != null && legacyDisplaySize.x > 0 && legacyDisplaySize.y > 0) {
            return Rect(0, 0, legacyDisplaySize.x, legacyDisplaySize.y)
        }
        val w = maxOf(1, fallbackMetricsWidth ?: 1)
        val h = maxOf(1, fallbackMetricsHeight ?: 1)
        return Rect(0, 0, w, h)
    }
}

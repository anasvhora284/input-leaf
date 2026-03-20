package com.inputleaf.android.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "CursorOverlayService"

/**
 * Service that displays a floating cursor overlay.
 * Requires SYSTEM_ALERT_WINDOW permission (draw over other apps).
 */
class CursorOverlayService : Service() {
    
    private var windowManager: WindowManager? = null
    private var cursorView: CursorView? = null
    private var isShowing = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var statusBarHeight = 0
    
    companion object {
        private var instance: CursorOverlayService? = null
        private val mainHandler = Handler(Looper.getMainLooper())
        
        // Cursor position updated by ConnectionService
        private val _cursorX = MutableStateFlow(0f)
        private val _cursorY = MutableStateFlow(0f)
        private val _isVisible = MutableStateFlow(false)
        
        val cursorX: StateFlow<Float> = _cursorX
        val cursorY: StateFlow<Float> = _cursorY
        val isVisible: StateFlow<Boolean> = _isVisible
        
        fun updatePosition(x: Float, y: Float) {
            _cursorX.value = x
            _cursorY.value = y
            // Post to main thread
            mainHandler.post {
                instance?.moveCursorInternal(x, y)
            }
        }
        
        fun show() {
            Log.d(TAG, "show() called - instance=$instance")
            _isVisible.value = true
            // Post to main thread
            mainHandler.post {
                instance?.showCursorInternal()
            }
        }
        
        fun hide() {
            Log.d(TAG, "hide() called")
            _isVisible.value = false
            // Post to main thread
            mainHandler.post {
                instance?.hideCursorInternal()
            }
        }
        
        fun canDrawOverlays(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Get status bar height for Y offset correction
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = resources.getDimensionPixelSize(resourceId)
        }
        Log.d(TAG, "CursorOverlayService created, statusBarHeight=$statusBarHeight")
        
        // If show was called before service was created, show now
        if (_isVisible.value) {
            Log.d(TAG, "isVisible was true on create, showing cursor")
            showCursorInternal()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showCursorInternal()
            ACTION_HIDE -> hideCursorInternal()
            ACTION_MOVE -> {
                val x = intent.getFloatExtra(EXTRA_X, 0f)
                val y = intent.getFloatExtra(EXTRA_Y, 0f)
                moveCursorInternal(x, y)
            }
        }
        return START_STICKY
    }
    
    private fun showCursorInternal() {
        Log.d(TAG, "showCursorInternal() called - isShowing=$isShowing, canDrawOverlays=${Settings.canDrawOverlays(this)}")
        if (isShowing || !Settings.canDrawOverlays(this)) return
        
        cursorView = CursorView(this)
        
        val params = WindowManager.LayoutParams(
            CURSOR_SIZE,
            CURSOR_SIZE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = _cursorX.value.toInt() - CURSOR_SIZE / 2
            y = _cursorY.value.toInt() - CURSOR_SIZE / 2
        }
        
        try {
            windowManager?.addView(cursorView, params)
            isShowing = true
            Log.d(TAG, "Cursor overlay added to window manager successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add cursor overlay", e)
            e.printStackTrace()
        }
    }
    
    private fun hideCursorInternal() {
        Log.d(TAG, "hideCursorInternal() called - isShowing=$isShowing")
        if (!isShowing) return
        try {
            cursorView?.let { view ->
                (view as? CursorView)?.cleanup()
                windowManager?.removeView(view)
            }
            cursorView = null
            isShowing = false
            Log.d(TAG, "Cursor overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide cursor overlay", e)
            e.printStackTrace()
        }
    }
    
    private fun moveCursorInternal(x: Float, y: Float) {
        if (!isShowing) return
        cursorView?.let { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return
            params.x = x.toInt() - CURSOR_SIZE / 2
            params.y = y.toInt() - CURSOR_SIZE / 2
            try {
                windowManager?.updateViewLayout(view, params)
            } catch (e: Exception) {
                // View might not be attached
            }
        }
    }
    
    override fun onDestroy() {
        hideCursorInternal()
        instance = null
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * Custom view that draws a cursor pointer.
     */
    private class CursorView(context: Context) : View(context) {
        
        // Purple color for Material You theme
        private val purpleLight = Color.parseColor("#A78BFA")
        private val purpleMedium = Color.parseColor("#8B5CF6")
        
        // Ripple paint with RadialGradient
        private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                0f, 0f,
                120f,
                intArrayOf(
                    Color.argb(51, 139, 92, 246),   // 20% alpha
                    Color.argb(26, 139, 92, 246),   // 10% alpha
                    Color.argb(0, 139, 92, 246)     // 0% alpha
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        
        // Middle ring paint
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(102, 167, 139, 250)  // 40% alpha purple
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        
        // Center dot paint
        private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = purpleMedium
            style = Paint.Style.FILL
        }
        
        // White border paint
        private val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        
        // Ripple animation state
        private var rippleScale = 0.5f
        private var rippleAlpha = 1f
        
        private var rippleAnimator: ValueAnimator? = null
        
        init {
            // Setup ripple animation
            rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    val progress = animation.animatedValue as Float
                    rippleScale = 0.5f + (progress * 1.0f)
                    rippleAlpha = 1f - progress
                    invalidate()
                }
            }
            rippleAnimator?.start()
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            
            // Draw outer ripple
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(rippleScale, rippleScale)
            ripplePaint.alpha = (255 * rippleAlpha).toInt()
            canvas.drawCircle(0f, 0f, OUTER_RADIUS, ripplePaint)
            canvas.restore()
            
            // Draw middle ring
            canvas.drawCircle(cx, cy, RING_RADIUS, ringPaint)
            
            // Draw center dot
            canvas.drawCircle(cx, cy, CENTER_RADIUS, centerPaint)
            
            // Draw white border around center dot
            canvas.drawCircle(cx, cy, CENTER_BORDER_RADIUS, whiteBorderPaint)
        }
        
        fun cleanup() {
            rippleAnimator?.cancel()
            rippleAnimator = null
        }
        
        companion object {
            private const val OUTER_RADIUS = 120f  // 40dp (80dp diameter)
            private const val RING_RADIUS = 72f    // 24dp (48dp diameter)
            private const val CENTER_RADIUS = 24f  // 8dp (16dp diameter)
            private const val CENTER_BORDER_RADIUS = 18f  // 6dp (12dp diameter)
        }
    }
}

// Constants
private const val CURSOR_SIZE = 80  // Increased from 48 to accommodate ripple
const val ACTION_SHOW = "com.inputleaf.android.CURSOR_SHOW"
const val ACTION_HIDE = "com.inputleaf.android.CURSOR_HIDE"
const val ACTION_MOVE = "com.inputleaf.android.CURSOR_MOVE"
const val EXTRA_X = "x"
const val EXTRA_Y = "y"

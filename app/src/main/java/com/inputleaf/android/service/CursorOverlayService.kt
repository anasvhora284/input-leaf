package com.inputleaf.android.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
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
            cursorView?.let { windowManager?.removeView(it) }
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
        
        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        
        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        
        private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.FILL
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            
            // Draw cursor: black outline, white fill, red center dot
            canvas.drawCircle(cx, cy, OUTER_RADIUS, outerPaint)
            canvas.drawCircle(cx, cy, INNER_RADIUS, innerPaint)
            canvas.drawCircle(cx, cy, CENTER_RADIUS, centerPaint)
        }
        
        companion object {
            private const val OUTER_RADIUS = 12f
            private const val INNER_RADIUS = 10f
            private const val CENTER_RADIUS = 4f
        }
    }
}

// Constants
private const val CURSOR_SIZE = 48
const val ACTION_SHOW = "com.inputleaf.android.CURSOR_SHOW"
const val ACTION_HIDE = "com.inputleaf.android.CURSOR_HIDE"
const val ACTION_MOVE = "com.inputleaf.android.CURSOR_MOVE"
const val EXTRA_X = "x"
const val EXTRA_Y = "y"

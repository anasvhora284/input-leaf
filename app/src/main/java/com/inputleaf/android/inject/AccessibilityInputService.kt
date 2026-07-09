package com.inputleaf.android.inject

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

private const val TAG = "AccessibilityInputService"

class AccessibilityInputService : AccessibilityService() {

    companion object {
        private var instance: AccessibilityInputService? = null

        fun getInstance(): AccessibilityInputService? = instance

        fun isServiceRunning(): Boolean = instance != null
    }

    private var isDragging = false
    private var lastDragX = 0f
    private var lastDragY = 0f
    private var lastStroke: GestureDescription.StrokeDescription? = null
    
    private var windowManager: android.view.WindowManager? = null
    private var cursorView: android.view.View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        Log.d(TAG, "AccessibilityInputService connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.d(TAG, "AccessibilityInputService disconnected")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hideCursorInternal()
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: we inject only, don't process events
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
        hideCursorInternal()
        instance = null
    }

    fun showCursorInternal() {
        if (cursorView != null) return
        val view = android.widget.ImageView(this).apply {
            setImageResource(com.inputleaf.android.R.drawable.cursor)
            scaleX = -1f
        }
        cursorView = view
        
        val CURSOR_SIZE = 80
        val params = android.view.WindowManager.LayoutParams(
            CURSOR_SIZE,
            CURSOR_SIZE,
            android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = com.inputleaf.android.service.CursorOverlayService.cursorX.value.toInt() - CURSOR_SIZE / 2
            y = com.inputleaf.android.service.CursorOverlayService.cursorY.value.toInt() - CURSOR_SIZE / 2
        }
        
        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add cursor overlay", e)
        }
    }

    fun hideCursorInternal() {
        cursorView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
        }
        cursorView = null
    }

    fun moveCursorInternal(x: Float, y: Float) {
        val view = cursorView ?: return
        val params = view.layoutParams as? android.view.WindowManager.LayoutParams ?: return
        params.x = x.toInt() - 40 // CURSOR_SIZE / 2
        params.y = y.toInt() - 40
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            // View might not be attached
        }
    }

    fun injectTap(x: Float, y: Float): Boolean {
        Log.d(TAG, "injectTap: $x, $y")
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun injectTouchDown(x: Float, y: Float): Boolean {
        Log.d(TAG, "injectTouchDown: $x, $y")
        isDragging = true
        lastDragX = x
        lastDragY = y
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 10, true)
        lastStroke = stroke
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun injectTouchMove(x: Float, y: Float): Boolean {
        if (!isDragging) return false
        val prev = lastStroke ?: return false
        // Only emit if there's actual movement to avoid empty paths
        if (Math.abs(x - lastDragX) < 0.1f && Math.abs(y - lastDragY) < 0.1f) return true
        
        val path = Path().apply { moveTo(lastDragX, lastDragY); lineTo(x, y) }
        lastDragX = x
        lastDragY = y
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val stroke = prev.continueStroke(path, 0, 10, true)
            lastStroke = stroke
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    fun injectTouchUp(x: Float, y: Float): Boolean {
        if (!isDragging) return false
        val prev = lastStroke ?: return false
        val path = Path().apply { moveTo(lastDragX, lastDragY); lineTo(x, y) }
        isDragging = false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val stroke = prev.continueStroke(path, 0, 10, false)
            lastStroke = null
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    fun injectSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        Log.d(TAG, "injectSwipe: ($x1, $y1) -> ($x2, $y2), duration=$duration")
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun insertTextIntoNode(node: android.view.accessibility.AccessibilityNodeInfo, text: String) {
        val currentText = node.text?.toString() ?: ""
        val selStart = node.textSelectionStart.coerceAtLeast(0)
        val selEnd = node.textSelectionEnd.coerceAtLeast(selStart)
        
        val newText = currentText.substring(0, selStart) + text + currentText.substring(selEnd)
        val arguments = android.os.Bundle().apply {
            putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        
        val selArgs = android.os.Bundle().apply {
            putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, selStart + text.length)
            putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, selStart + text.length)
        }
        node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
    }

    private fun deleteTextInNode(node: android.view.accessibility.AccessibilityNodeInfo) {
        val currentText = node.text?.toString() ?: ""
        if (currentText.isEmpty()) {
            return
        }
        
        val selStart = node.textSelectionStart.coerceAtLeast(0)
        val selEnd = node.textSelectionEnd.coerceAtLeast(selStart)
        
        val newText: String
        val newCursor: Int
        
        if (selStart != selEnd) {
            newText = currentText.substring(0, selStart) + currentText.substring(selEnd)
            newCursor = selStart
        } else if (selStart > 0) {
            newText = currentText.substring(0, selStart - 1) + currentText.substring(selEnd)
            newCursor = selStart - 1
        } else {
            return
        }
        
        val arguments = android.os.Bundle().apply {
            putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        
        val selArgs = android.os.Bundle().apply {
            putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursor)
            putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursor)
        }
        node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
    }
}

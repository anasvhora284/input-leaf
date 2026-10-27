package com.inputleaf.android.inject

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

private const val TAG = "AccessibilityInputService"

/** Length of each drag gesture segment; one plays at a time (see DragCoalescer). */
private const val SEGMENT_MS = 10L

/** How long to watch for Android TV Home's app menu after a click: 12 x 250ms, for slow boxes. */
private const val LAUNCHER_MENU_CHECKS = 12
private const val LAUNCHER_MENU_CHECK_MS = 250L

class AccessibilityInputService : AccessibilityService() {

    companion object {
        private var instance: AccessibilityInputService? = null

        fun getInstance(): AccessibilityInputService? = instance

        fun isServiceRunning(): Boolean = instance != null

        @kotlin.jvm.Volatile
        var targetImeLabelToSelect: String? = null
    }

    @Volatile private var isDragging = false
    private var lastStroke: GestureDescription.StrokeDescription? = null
    
    private var windowManager: android.view.WindowManager? = null
    private var cursorView: android.view.View? = null

    private lateinit var prefs: com.inputleaf.android.storage.AppPreferences
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var currentCursorStyle = "default"

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        Log.d(TAG, "AccessibilityInputService connected")
        
        prefs = com.inputleaf.android.storage.AppPreferences(this)
        scope.launch {
            prefs.cursorStyle.collect { style ->
                currentCursorStyle = style
                updateCursorImage()
            }
        }
        if (com.inputleaf.android.service.CursorOverlayService.isVisible.value) {
            showCursorInternal()
        } else {
            ensureCursorView(visible = false)
        }
    }

    private fun updateCursorImage() {
        val imageView = cursorView as? android.widget.ImageView ?: return
        com.inputleaf.android.service.CursorOverlayView.applyStyle(imageView, currentCursorStyle)
        moveCursorInternal(
            com.inputleaf.android.service.CursorOverlayService.cursorX.value,
            com.inputleaf.android.service.CursorOverlayService.cursorY.value,
        )
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.d(TAG, "AccessibilityInputService disconnected")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        try {
            scope.cancel()
        } catch (e: Exception) {}
        cursorView?.let {
            runCatching { windowManager?.removeView(it) }
        }
        cursorView = null
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val target = targetImeLabelToSelect ?: return
        val rootNode = rootInActiveWindow ?: return
        val nodes = rootNode.findAccessibilityNodeInfosByText(target)
        if (!nodes.isNullOrEmpty()) {
            for (node in nodes) {
                var current: android.view.accessibility.AccessibilityNodeInfo? = node
                while (current != null) {
                    if (current.isClickable) {
                        current.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        Log.i(TAG, "Accessibility service auto-clicked input method item: $target")
                        targetImeLabelToSelect = null // Clear target so we stop matching
                        break
                    }
                    current = current.parent
                }
                if (targetImeLabelToSelect == null) break
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
        hideCursorInternal()
        instance = null
    }

    fun showCursorInternal() {
        ensureCursorView(visible = true)
    }

    private fun ensureCursorView(visible: Boolean) {
        if (cursorView == null) {
            val view = com.inputleaf.android.service.CursorOverlayView.create(this, currentCursorStyle)
            val params = com.inputleaf.android.service.CursorOverlayView.layoutParams(
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                currentCursorStyle,
                com.inputleaf.android.service.CursorOverlayService.cursorX.value,
                com.inputleaf.android.service.CursorOverlayService.cursorY.value,
            )
            try {
                windowManager?.addView(view, params)
                cursorView = view
                Log.i(TAG, "Cursor overlay view attached")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add cursor overlay", e)
                return
            }
        }
        cursorView?.visibility = if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE
    }

    fun hideCursorInternal() {
        cursorView?.visibility = android.view.View.INVISIBLE
    }

    fun moveCursorInternal(x: Float, y: Float) {
        val view = cursorView ?: return
        val params = view.layoutParams as? android.view.WindowManager.LayoutParams ?: return
        com.inputleaf.android.service.CursorOverlayView.place(params, currentCursorStyle, x, y)
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

    /**
     * Android rejects accessibility gestures on devices without touch or fake-touch support.
     * Activate the deepest clickable node beneath the software cursor instead.
     */
    fun clickNodeAt(x: Float, y: Float): Boolean {
        val roots = accessibilityRoots()
        val onLauncher = rootInActiveWindow?.packageName == TvLauncherMenu.LAUNCHER_PACKAGE
        val clicked = AccessibilityNodeClicker.clickAt(roots, x, y)
        Log.d(TAG, "Clicked accessibility node at $x, $y: $clicked")
        if (clicked && onLauncher) watchForLauncherMenu()
        return clicked
    }

    private var launcherMenuWatch: Job? = null

    /**
     * Some Android TV Home builds open an app's long-press menu for an accessibility
     * click (#64). If that menu appears right after our click, open the app as meant.
     */
    private fun watchForLauncherMenu() {
        launcherMenuWatch?.cancel()
        launcherMenuWatch = scope.launch {
            repeat(LAUNCHER_MENU_CHECKS) {
                delay(LAUNCHER_MENU_CHECK_MS)
                if (TvLauncherMenu.confirmOpen(accessibilityRoots())) {
                    Log.i(TAG, "Launcher answered the click with its app menu; opened the app")
                    return@launch
                }
            }
            Log.d(TAG, "No launcher app menu after the click")
        }
    }

    fun performGlobalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun performGlobalHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    /**
     * The wheel on devices that reject gesture swipes (non-touch TV boxes): each step moves
     * focus like a D-pad press, which is how TV lists scroll. Screens with nothing focused
     * fall back to the scroll action on the list under the cursor.
     */
    fun scrollNodeAt(x: Float, y: Float, steps: Int, horizontal: Boolean): Boolean {
        val roots = accessibilityRoots()
        val direction = when {
            horizontal && steps > 0 -> View.FOCUS_RIGHT
            horizontal -> View.FOCUS_LEFT
            steps > 0 -> View.FOCUS_DOWN
            else -> View.FOCUS_UP
        }
        var moved = 0
        repeat(kotlin.math.abs(steps)) { if (AccessibilityNodeClicker.moveFocus(roots, direction)) moved++ }
        if (moved > 0) return true
        return AccessibilityNodeClicker.scrollAt(roots, x, y, steps, horizontal)
    }

    private fun accessibilityRoots(): List<AccessibilityNodeInfo> = buildList {
        rootInActiveWindow?.let(::add)
        for (window in windows) {
            val root = window.root ?: continue
            if (none { it == root }) add(root)
        }
    }

    private val drag = DragCoalescer()

    private val segmentCallback = object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
            drag.onSegmentDone()?.let(::dispatchSegment)
        }

        override fun onCancelled(gestureDescription: GestureDescription?) {
            drag.onCancelled()
            synchronized(drag) { lastStroke = null; isDragging = false }
        }
    }

    fun injectTouchDown(x: Float, y: Float): Boolean {
        Log.d(TAG, "injectTouchDown: $x, $y")
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, SEGMENT_MS, true)
        synchronized(drag) {
            isDragging = true
            lastStroke = stroke
            drag.onDown(x, y)
        }
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, segmentCallback, null)
    }

    fun injectTouchMove(x: Float, y: Float): Boolean {
        if (!isDragging) return false
        drag.onMove(x, y)?.let(::dispatchSegment)
        return true
    }

    fun injectTouchUp(x: Float, y: Float): Boolean {
        if (!isDragging) return false
        drag.onUp(x, y)?.let(::dispatchSegment)
        return true
    }

    /** Continues the drag stroke with one segment; the callback chains the next one. */
    private fun dispatchSegment(segment: DragCoalescer.Segment) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gesture = synchronized(drag) {
            val prev = lastStroke ?: return
            val path = Path().apply { moveTo(segment.fromX, segment.fromY); lineTo(segment.toX, segment.toY) }
            val stroke = prev.continueStroke(path, 0, SEGMENT_MS, !segment.last)
            lastStroke = if (segment.last) null else stroke
            if (segment.last) isDragging = false
            GestureDescription.Builder().addStroke(stroke).build()
        }
        dispatchGesture(gesture, segmentCallback, null)
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

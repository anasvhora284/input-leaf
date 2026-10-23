package com.inputleaf.android.inject

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Activates the best clickable accessibility node at a software cursor position. */
internal object AccessibilityNodeClicker {
    fun clickAt(roots: List<AccessibilityNodeInfo>, x: Float, y: Float): Boolean {
        for (root in roots) {
            val target = deepestNodeAt(root, x.toInt(), y.toInt()) { it.isClickable }
            if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        }

        // TV widgets driven only by the D-pad (Android TV Home's Search / Home / Apps tabs)
        // are focusable but not clickable; moving focus to them is how they activate (#65).
        for (root in roots) {
            val focusable = deepestNodeAt(root, x.toInt(), y.toInt()) { it.isFocusable && !it.isFocused }
            if (focusable?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) == true) return true
        }

        for (root in roots) {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            if (clickableAncestor(focused)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                return true
            }
        }
        return false
    }

    /**
     * Scrolls the innermost scrollable item under the cursor by [steps] (#65). Positive
     * vertical steps scroll down (forward), positive horizontal steps scroll right.
     * @return false when nothing under the cursor scrolls, or it refused.
     */
    fun scrollAt(roots: List<AccessibilityNodeInfo>, x: Float, y: Float, steps: Int, horizontal: Boolean): Boolean {
        if (steps == 0) return false
        val target = roots.firstNotNullOfOrNull { root ->
            deepestNodeAt(root, x.toInt(), y.toInt()) { it.isScrollable }
        } ?: return false
        val directional = when {
            horizontal && steps > 0 -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
            horizontal -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
            steps > 0 -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
            else -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        }
        // Lists only expose forward/backward; grids and pagers may expose the directional ones.
        val action = if (target.actionList.any { it.id == directional }) directional
        else if (steps > 0) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        var scrolled = false
        repeat(kotlin.math.abs(steps)) { if (target.performAction(action)) scrolled = true }
        return scrolled
    }

    private fun deepestNodeAt(
        root: AccessibilityNodeInfo,
        x: Int,
        y: Int,
        accept: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        data class PendingNode(val node: AccessibilityNodeInfo, val depth: Int)

        val pending = java.util.ArrayDeque<PendingNode>()
        pending.add(PendingNode(root, 0))
        var best: AccessibilityNodeInfo? = null
        var bestDepth = -1
        var bestArea = Long.MAX_VALUE
        val bounds = Rect()

        while (pending.isNotEmpty()) {
            val (node, depth) = pending.removeLast()
            node.getBoundsInScreen(bounds)
            if (node.isVisibleToUser && bounds.contains(x, y) && accept(node)) {
                val area = bounds.width().toLong() * bounds.height().toLong()
                if (depth > bestDepth || (depth == bestDepth && area < bestArea)) {
                    best = node
                    bestDepth = depth
                    bestArea = area
                }
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { pending.add(PendingNode(it, depth + 1)) }
            }
        }
        return best
    }

    private fun clickableAncestor(start: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = start
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }
}

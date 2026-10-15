package com.inputleaf.android.inject

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Activates the best clickable accessibility node at a software cursor position. */
internal object AccessibilityNodeClicker {
    fun clickAt(roots: List<AccessibilityNodeInfo>, x: Float, y: Float): Boolean {
        for (root in roots) {
            val target = deepestClickableNodeAt(root, x.toInt(), y.toInt())
            if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
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

    private fun deepestClickableNodeAt(
        root: AccessibilityNodeInfo,
        x: Int,
        y: Int,
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
            if (node.isVisibleToUser && bounds.contains(x, y) && node.isClickable) {
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

package com.inputleaf.android.inject

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Android TV Home can answer an accessibility click on an app tile with its long-press
 * menu (Open, Move, Add to favourites, Info, Uninstall) instead of launching the app.
 * Seen on an Android 12 box (#64), though not on the stock emulator images. The menu
 * opens with Open pre-selected, so activating that row is what the click meant.
 *
 * Rows are matched by view id, never by their translated labels.
 */
internal object TvLauncherMenu {
    const val LAUNCHER_PACKAGE = "com.google.android.tvlauncher"
    private const val MENU_ROW_ID = "$LAUNCHER_PACKAGE:id/main_container"

    /** Activates the menu's pre-selected first row; false when that menu is not showing. */
    fun confirmOpen(roots: List<AccessibilityNodeInfo>): Boolean {
        val row = roots.asSequence()
            .flatMap { it.findAccessibilityNodeInfosByViewId(MENU_ROW_ID).asSequence() }
            .firstOrNull { it.isFocused }
            ?: return false
        // Only the first row is Open. A focused later row means the user moved there.
        if (row.parent?.getChild(0) != row) return false
        return row.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}

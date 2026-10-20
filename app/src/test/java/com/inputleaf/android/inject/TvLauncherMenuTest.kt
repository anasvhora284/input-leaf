package com.inputleaf.android.inject

import android.view.accessibility.AccessibilityNodeInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

/**
 * Android TV Home's app menu, as dumped on the Android 12 emulator: a popup window whose
 * rows (Open, Move, Add to favourites, Info, Uninstall) share the id main_container, with
 * Open focused when it opens.
 */
@RunWith(RobolectricTestRunner::class)
class TvLauncherMenuTest {
    private val rowId = "com.google.android.tvlauncher:id/main_container"

    @Test
    fun `opens the app when the menu shows with Open selected`() {
        val (root, rows) = menu(focusedRow = 0)

        assertThat(TvLauncherMenu.confirmOpen(listOf(root))).isTrue()
        verify(rows[0]).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    @Test
    fun `leaves a row the user moved to alone`() {
        val (root, rows) = menu(focusedRow = 2)

        assertThat(TvLauncherMenu.confirmOpen(listOf(root))).isFalse()
        rows.forEach { verify(it, never()).performAction(AccessibilityNodeInfo.ACTION_CLICK) }
    }

    @Test
    fun `does nothing when the menu is not showing`() {
        val noMenu = mock(AccessibilityNodeInfo::class.java)
        `when`(noMenu.findAccessibilityNodeInfosByViewId(rowId)).thenReturn(emptyList())
        val (unfocused, _) = menu(focusedRow = null)

        assertThat(TvLauncherMenu.confirmOpen(listOf(noMenu))).isFalse()
        assertThat(TvLauncherMenu.confirmOpen(listOf(unfocused))).isFalse()
        assertThat(TvLauncherMenu.confirmOpen(emptyList())).isFalse()
    }

    /** A menu window root with five rows under one parent; [focusedRow] has focus. */
    private fun menu(focusedRow: Int?): Pair<AccessibilityNodeInfo, List<AccessibilityNodeInfo>> {
        val parent = mock(AccessibilityNodeInfo::class.java)
        val rows = List(5) { index ->
            mock(AccessibilityNodeInfo::class.java).also {
                `when`(it.isFocused).thenReturn(index == focusedRow)
                `when`(it.parent).thenReturn(parent)
                `when`(it.performAction(AccessibilityNodeInfo.ACTION_CLICK)).thenReturn(true)
            }
        }
        `when`(parent.getChild(0)).thenReturn(rows[0])
        val root = mock(AccessibilityNodeInfo::class.java)
        `when`(root.findAccessibilityNodeInfosByViewId(rowId)).thenReturn(rows)
        return root to rows
    }
}

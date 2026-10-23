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
import org.robolectric.annotation.Config

/**
 * The hit-test path is covered on a real Android TV hierarchy by AccessibilityTvClickTest;
 * these cover the focus fallback that TV launchers rely on when nothing clickable sits
 * under the cursor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class AccessibilityNodeClickerTest {

    @Test
    fun `falls back to the focused item and clicks its nearest clickable ancestor`() {
        val card = node(clickable = true)
        `when`(card.performAction(AccessibilityNodeInfo.ACTION_CLICK)).thenReturn(true)
        val title = node(clickable = false, parent = card)
        val root = node(clickable = false)
        `when`(root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)).thenReturn(title)

        assertThat(AccessibilityNodeClicker.clickAt(listOf(root), 10f, 10f)).isTrue()
        verify(card).performAction(AccessibilityNodeInfo.ACTION_CLICK)
        verify(title, never()).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    @Test
    fun `reports no click when nothing is under the cursor or focused`() {
        val orphan = node(clickable = false)
        val root = node(clickable = false)
        `when`(root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)).thenReturn(orphan)

        assertThat(AccessibilityNodeClicker.clickAt(listOf(root), 10f, 10f)).isFalse()
    }

    @Test
    fun `a focusable widget under the cursor is focused when nothing there is clickable`() {
        // Android TV Home's Search / Home / Apps tabs: focusable, not clickable (#65).
        val tab = node(clickable = false)
        `when`(tab.isFocusable).thenReturn(true)
        `when`(tab.isVisibleToUser).thenReturn(true)
        `when`(tab.performAction(AccessibilityNodeInfo.ACTION_FOCUS)).thenReturn(true)
        coverScreen(tab)

        assertThat(AccessibilityNodeClicker.clickAt(listOf(tab), 10f, 10f)).isTrue()
        verify(tab).performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    @Test
    fun `scrolling uses the directional action when offered, else forward and backward`() {
        val grid = scrollable(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN)
        assertThat(AccessibilityNodeClicker.scrollAt(listOf(grid), 10f, 10f, steps = 2, horizontal = false)).isTrue()
        verify(grid, org.mockito.Mockito.times(2))
            .performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id)

        val list = scrollable()
        assertThat(AccessibilityNodeClicker.scrollAt(listOf(list), 10f, 10f, steps = -1, horizontal = false)).isTrue()
        verify(list).performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)

        val row = scrollable()
        AccessibilityNodeClicker.scrollAt(listOf(row), 10f, 10f, steps = 1, horizontal = true)
        verify(row).performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    @Test
    fun `nothing scrollable under the cursor reports no scroll`() {
        val plain = node(clickable = false)
        coverScreen(plain)
        assertThat(AccessibilityNodeClicker.scrollAt(listOf(plain), 10f, 10f, steps = 1, horizontal = false)).isFalse()
        assertThat(AccessibilityNodeClicker.scrollAt(listOf(plain), 10f, 10f, steps = 0, horizontal = false)).isFalse()
    }

    @Test
    fun `focus moves to the neighbour in the wheel direction, like a D-pad press`() {
        val next = node(clickable = false)
        `when`(next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)).thenReturn(true)
        val focused = node(clickable = true)
        `when`(focused.focusSearch(android.view.View.FOCUS_DOWN)).thenReturn(next)
        val root = node(clickable = false)
        `when`(root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)).thenReturn(focused)

        assertThat(AccessibilityNodeClicker.moveFocus(listOf(root), android.view.View.FOCUS_DOWN)).isTrue()
        verify(next).performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        // Nothing further up, and nothing focused at all, report no move.
        assertThat(AccessibilityNodeClicker.moveFocus(listOf(root), android.view.View.FOCUS_UP)).isFalse()
        assertThat(AccessibilityNodeClicker.moveFocus(listOf(node(clickable = false)), android.view.View.FOCUS_UP)).isFalse()
    }

    @Test
    fun `a vertical scroll skips a horizontal row to reach the page that scrolls down`() {
        val page = scrollable(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN)
        val row = scrollable(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT)
        `when`(page.childCount).thenReturn(1)
        `when`(page.getChild(0)).thenReturn(row)

        AccessibilityNodeClicker.scrollAt(listOf(page), 10f, 10f, steps = 1, horizontal = false)

        verify(page).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id)
        verify(row, never()).performAction(org.mockito.ArgumentMatchers.anyInt())
    }

    private fun scrollable(vararg offered: AccessibilityNodeInfo.AccessibilityAction): AccessibilityNodeInfo =
        node(clickable = false).also {
            `when`(it.isScrollable).thenReturn(true)
            `when`(it.isVisibleToUser).thenReturn(true)
            `when`(it.actionList).thenReturn(offered.toList())
            `when`(it.performAction(org.mockito.ArgumentMatchers.anyInt())).thenReturn(true)
            coverScreen(it)
        }

    /** Makes [n] report bounds that contain (10, 10). */
    private fun coverScreen(n: AccessibilityNodeInfo) {
        org.mockito.Mockito.doAnswer { (it.arguments[0] as android.graphics.Rect).set(0, 0, 100, 100); null }
            .`when`(n).getBoundsInScreen(org.mockito.ArgumentMatchers.any())
    }

    private fun node(clickable: Boolean, parent: AccessibilityNodeInfo? = null): AccessibilityNodeInfo =
        mock(AccessibilityNodeInfo::class.java).also {
            `when`(it.isClickable).thenReturn(clickable)
            `when`(it.parent).thenReturn(parent)
        }
}

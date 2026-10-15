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
 * The hit-test path is covered on a real Android TV hierarchy by AccessibilityTvClickTest;
 * these cover the focus fallback that TV launchers rely on when nothing clickable sits
 * under the cursor.
 */
@RunWith(RobolectricTestRunner::class)
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

    private fun node(clickable: Boolean, parent: AccessibilityNodeInfo? = null): AccessibilityNodeInfo =
        mock(AccessibilityNodeInfo::class.java).also {
            `when`(it.isClickable).thenReturn(clickable)
            `when`(it.parent).thenReturn(parent)
        }
}

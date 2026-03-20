package com.inputleaf.android.uhid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MousePositionTrackerTest {
    @Test fun `first absolute move returns full position as delta`() {
        val tracker = MousePositionTracker()
        val (dx, dy) = tracker.updateAbsolute(100, 200)
        assertThat(dx).isEqualTo(100)
        assertThat(dy).isEqualTo(200)
    }

    @Test fun `subsequent absolute moves return delta`() {
        val tracker = MousePositionTracker()
        tracker.updateAbsolute(100, 200)
        val (dx, dy) = tracker.updateAbsolute(110, 195)
        assertThat(dx).isEqualTo(10)
        assertThat(dy).isEqualTo(-5)
    }

    @Test fun `relative move returns unchanged`() {
        val tracker = MousePositionTracker()
        val (dx, dy) = tracker.updateRelative(3, -7)
        assertThat(dx).isEqualTo(3)
        assertThat(dy).isEqualTo(-7)
    }
}

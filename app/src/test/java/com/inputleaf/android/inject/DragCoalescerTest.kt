package com.inputleaf.android.inject

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.inject.DragCoalescer.Segment
import org.junit.Test

class DragCoalescerTest {

    @Test
    fun `moves during a playing segment collapse into one jump to the latest position`() {
        val drag = DragCoalescer()
        drag.onDown(0f, 0f)
        // The touch-down stroke is still playing: a burst of mouse reports queues nothing.
        for (x in 1..100) assertThat(drag.onMove(x.toFloat(), 0f)).isNull()

        assertThat(drag.onSegmentDone()).isEqualTo(Segment(0f, 0f, 100f, 0f, last = false))
        // Nothing new arrived while that played.
        assertThat(drag.onSegmentDone()).isNull()
    }

    @Test
    fun `a move with nothing playing is sent straight away`() {
        val drag = DragCoalescer()
        drag.onDown(0f, 0f)
        drag.onSegmentDone()

        assertThat(drag.onMove(5f, 5f)).isEqualTo(Segment(0f, 0f, 5f, 5f, last = false))
        assertThat(drag.onMove(9f, 9f)).isNull()
        assertThat(drag.onSegmentDone()).isEqualTo(Segment(5f, 5f, 9f, 9f, last = false))
    }

    @Test
    fun `a release waits for the playing segment, then ends at the release point`() {
        val drag = DragCoalescer()
        drag.onDown(0f, 0f)
        drag.onMove(3f, 0f)
        assertThat(drag.onUp(7f, 1f)).isNull()

        assertThat(drag.onSegmentDone()).isEqualTo(Segment(0f, 0f, 7f, 1f, last = true))
        // The drag is over: later callbacks and moves do nothing.
        assertThat(drag.onSegmentDone()).isNull()
        assertThat(drag.onMove(9f, 9f)).isNull()
        assertThat(drag.onUp(9f, 9f)).isNull()
    }

    @Test
    fun `a release with nothing playing ends at once, even without movement`() {
        val drag = DragCoalescer()
        drag.onDown(4f, 4f)
        drag.onSegmentDone()

        assertThat(drag.onUp(4f, 4f)).isEqualTo(Segment(4f, 4f, 4f, 4f, last = true))
    }

    @Test
    fun `tiny moves are not sent mid-drag, and a cancel ends the drag`() {
        val drag = DragCoalescer()
        drag.onDown(0f, 0f)
        drag.onSegmentDone()
        assertThat(drag.onMove(0.05f, 0f)).isNull()

        drag.onCancelled()
        assertThat(drag.onMove(50f, 50f)).isNull()
        assertThat(drag.onSegmentDone()).isNull()
    }
}

package com.inputleaf.android.uhid

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.InputLeapEvent
import org.junit.Test

class UhidEventQueueTest {
    @Test fun `enqueue and dequeue works`() {
        val q = UhidEventQueue(capacity = 5)
        q.enqueue(InputLeapEvent.KeyDown(65, 0, 0))
        assertThat(q.size()).isEqualTo(1)
        assertThat(q.dequeueAll()).hasSize(1)
        assertThat(q.size()).isEqualTo(0)
    }

    @Test fun `drops oldest when full`() {
        val q = UhidEventQueue(capacity = 3)
        q.enqueue(InputLeapEvent.KeyDown(1, 0, 0))
        q.enqueue(InputLeapEvent.KeyDown(2, 0, 0))
        q.enqueue(InputLeapEvent.KeyDown(3, 0, 0))
        q.enqueue(InputLeapEvent.KeyDown(4, 0, 0))
        val events = q.dequeueAll()
        assertThat(events.map { (it as InputLeapEvent.KeyDown).keyId }).containsExactly(2, 3, 4).inOrder()
    }
}

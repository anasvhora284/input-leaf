package com.inputleaf.android.uhid

import com.inputleaf.android.model.InputLeapEvent
import java.util.ArrayDeque

class UhidEventQueue(private val capacity: Int = 100) {
    private val queue = ArrayDeque<InputLeapEvent>(capacity)

    @Synchronized fun enqueue(event: InputLeapEvent) {
        if (queue.size >= capacity) queue.pollFirst()
        queue.addLast(event)
    }

    @Synchronized fun dequeueAll(): List<InputLeapEvent> {
        val result = queue.toList()
        queue.clear()
        return result
    }

    @Synchronized fun size() = queue.size
}

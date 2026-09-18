package com.inputleaf.android.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InputLeapEventTest {
    @Test
    fun `infoAck event is instantiable and has equality`() {
        val event1 = InputLeapEvent.InfoAck()
        val event2 = InputLeapEvent.InfoAck(Unit)
        assertThat(event1).isEqualTo(event2)
        assertThat(event1.dummy).isEqualTo(Unit)
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode())
        assertThat(event1.toString()).contains("InfoAck")
    }
}

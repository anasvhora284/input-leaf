package com.inputleaf.android.service

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.ConnectionState
import org.junit.Test

class ConnectionStateMachineTest {
    @Test fun `initial state is Disconnected`() {
        val sm = ConnectionStateMachine()
        assertThat(sm.state.value).isInstanceOf(ConnectionState.Disconnected::class.java)
    }

    @Test fun `connect transitions to Connecting`() {
        val sm = ConnectionStateMachine()
        sm.onConnecting("192.168.1.10")
        assertThat(sm.state.value).isEqualTo(ConnectionState.Connecting("192.168.1.10"))
    }

    @Test fun `DInfo sent transitions to Idle`() {
        val sm = ConnectionStateMachine()
        sm.onConnecting("192.168.1.10")
        sm.onHandshaking("192.168.1.10")
        sm.onIdle("192.168.1.10", "work-pc")
        assertThat(sm.state.value).isEqualTo(ConnectionState.Idle("192.168.1.10", "work-pc"))
    }

    @Test fun `Enter transitions Idle to Active`() {
        val sm = ConnectionStateMachine()
        sm.onIdle("192.168.1.10", "work-pc")
        sm.onActive()
        assertThat(sm.state.value).isEqualTo(ConnectionState.Active("192.168.1.10", "work-pc"))
    }

    @Test fun `duplicate Enter while Active is ignored`() {
        val sm = ConnectionStateMachine()
        sm.onIdle("192.168.1.10", "work-pc")
        sm.onActive()
        sm.onActive()
        assertThat(sm.state.value).isEqualTo(ConnectionState.Active("192.168.1.10", "work-pc"))
    }

    @Test fun `disconnect resets to Disconnected`() {
        val sm = ConnectionStateMachine()
        sm.onIdle("192.168.1.10", "work-pc")
        sm.onDisconnected()
        assertThat(sm.state.value).isInstanceOf(ConnectionState.Disconnected::class.java)
    }
}

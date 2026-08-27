package com.inputleaf.android.service

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.InputLeapEvent
import org.junit.Test

class ConnectionCoordinatorTest {
    @Test fun `connection transitions are exposed as state`() {
        val coordinator = ConnectionCoordinator()
        val generation = coordinator.beginConnection()

        assertThat(coordinator.onConnecting(generation, "192.168.1.10")).isTrue()
        assertThat(coordinator.state.value).isEqualTo(ConnectionState.Connecting("192.168.1.10"))

        assertThat(coordinator.onConnected(generation, "192.168.1.10", "work-pc")).isTrue()
        assertThat(coordinator.state.value)
            .isEqualTo(ConnectionState.Idle("192.168.1.10", "work-pc"))
    }

    @Test fun `events from an older connection generation are ignored`() {
        val coordinator = ConnectionCoordinator()
        val staleGeneration = coordinator.beginConnection()
        val currentGeneration = coordinator.beginConnection()
        coordinator.onConnected(currentGeneration, "192.168.1.10", "work-pc")

        val effects = coordinator.onEvent(staleGeneration, InputLeapEvent.KeyDown(1, 0, 2))

        assertThat(effects).isEmpty()
        assertThat(coordinator.state.value)
            .isEqualTo(ConnectionState.Idle("192.168.1.10", "work-pc"))
    }

    @Test fun `unexpected disconnect resets state and schedules retry`() {
        val coordinator = ConnectionCoordinator()
        val generation = coordinator.beginConnection()
        coordinator.onConnected(generation, "server", "phone")

        val effects = coordinator.onUnexpectedDisconnect(generation)

        assertThat(coordinator.state.value).isEqualTo(ConnectionState.Disconnected)
        assertThat(effects).containsExactly(
            ConnectionCoordinator.Effect.HideCursor,
            ConnectionCoordinator.Effect.RestoreIme,
            ConnectionCoordinator.Effect.ScheduleRetry,
        ).inOrder()
    }

    @Test fun `user disconnect invalidates connection and suppresses retry`() {
        val coordinator = ConnectionCoordinator()
        val generation = coordinator.beginConnection()
        coordinator.onConnected(generation, "server", "phone")

        coordinator.onUserDisconnect()

        assertThat(coordinator.isCurrent(generation)).isFalse()
        assertThat(coordinator.onUnexpectedDisconnect(generation)).isEmpty()
        assertThat(coordinator.onConnectionFailed(generation, retry = true)).isEmpty()
        assertThat(coordinator.state.value).isEqualTo(ConnectionState.Disconnected)
    }

    @Test fun `connection failure only retries when retry is allowed`() {
        val coordinator = ConnectionCoordinator()
        val firstGeneration = coordinator.beginConnection()

        assertThat(coordinator.onConnectionFailed(firstGeneration, retry = false)).isEmpty()

        val secondGeneration = coordinator.beginConnection()
        assertThat(coordinator.onConnectionFailed(secondGeneration, retry = true))
            .containsExactly(ConnectionCoordinator.Effect.ScheduleRetry)
    }

    @Test fun `fourth keepalive miss closes connection and resets state`() {
        val coordinator = ConnectionCoordinator()
        val generation = coordinator.beginConnection()
        coordinator.onConnected(generation, "server", "phone")

        repeat(3) {
            assertThat(coordinator.onKeepAliveMiss(generation)).isEmpty()
        }
        val effects = coordinator.onKeepAliveMiss(generation)

        assertThat(effects).containsExactly(
            ConnectionCoordinator.Effect.CloseConnection,
            ConnectionCoordinator.Effect.HideCursor,
            ConnectionCoordinator.Effect.RestoreIme,
        ).inOrder()
        assertThat(coordinator.state.value).isEqualTo(ConnectionState.Disconnected)
    }

    @Test fun `keepalive event resets missed keepalive count`() {
        val coordinator = ConnectionCoordinator()
        val generation = coordinator.beginConnection()
        coordinator.onKeepAliveMiss(generation)
        coordinator.onKeepAliveMiss(generation)

        assertThat(coordinator.onEvent(generation, InputLeapEvent.KeepAlive))
            .containsExactly(ConnectionCoordinator.Effect.SendKeepAlive)
        assertThat(coordinator.onKeepAliveMiss(generation)).isEmpty()
    }

    @Test fun `unhandled event is ignored without resetting missed keepalive count`() {
        val coordinator = ConnectionCoordinator()
        val generation = coordinator.beginConnection()
        repeat(3) {
            assertThat(coordinator.onKeepAliveMiss(generation)).isEmpty()
        }

        assertThat(coordinator.onEvent(generation, InputLeapEvent.Unhandled("ZZZZ"))).isEmpty()
        assertThat(coordinator.onKeepAliveMiss(generation)).containsExactly(
            ConnectionCoordinator.Effect.CloseConnection,
            ConnectionCoordinator.Effect.HideCursor,
            ConnectionCoordinator.Effect.RestoreIme,
        ).inOrder()
    }

    @Test fun `keyboard routing follows keyboard enablement`() {
        val coordinator = ConnectionCoordinator()
        val generation = coordinator.beginConnection()
        val key = InputLeapEvent.KeyDown(1, 0, 2)

        assertThat(coordinator.onEvent(generation, key))
            .containsExactly(ConnectionCoordinator.Effect.RouteInput(key))

        coordinator.setKeyboardEnabled(false)
        assertThat(coordinator.onEvent(generation, key)).isEmpty()
    }

    @Test fun `mouse routing follows mouse enablement`() {
        val coordinator = ConnectionCoordinator()
        val generation = coordinator.beginConnection()
        val move = InputLeapEvent.MouseMoveRel(4, -2)

        assertThat(coordinator.onEvent(generation, move))
            .containsExactly(ConnectionCoordinator.Effect.RouteInput(move))

        assertThat(coordinator.setMouseEnabled(false))
            .containsExactly(ConnectionCoordinator.Effect.HideCursor)
        assertThat(coordinator.onEvent(generation, move)).isEmpty()
        assertThat(coordinator.onEvent(generation, InputLeapEvent.Enter(0, 0, 1, 0))).isEmpty()
    }
}

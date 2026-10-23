package com.inputleaf.android.service

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.network.ConnectResult
import com.inputleaf.android.network.ConnectionTransportPolicy
import org.junit.Test

class ConnectionCoordinatorTest {

    @Test
    fun `initial state is Disconnected and generation is zero`() {
        val coordinator = ConnectionCoordinator()
        assertThat(coordinator.state.value).isInstanceOf(ConnectionState.Disconnected::class.java)
        assertThat(coordinator.generation).isEqualTo(0)
        assertThat(coordinator.currentRetryAttempt).isEqualTo(0)
        assertThat(coordinator.isPointerOnScreen).isFalse()
        assertThat(coordinator.isInfoAckPending).isFalse()
    }

    @Test
    fun `startConnect increments generation, sets Connecting state, and returns plan`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")

        assertThat(plan.generation).isEqualTo(1)
        assertThat(plan.initialDelayMs).isEqualTo(0L)
        assertThat(coordinator.generation).isEqualTo(1)
        assertThat(coordinator.state.value).isEqualTo(ConnectionState.Connecting("192.168.1.50"))
        assertThat(coordinator.serverIp).isEqualTo("192.168.1.50")
        assertThat(coordinator.screenName).isEqualTo("android-tablet")
    }

    @Test
    fun `startConnect with forceReconnect sets initial delay to 150ms`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet", forceReconnect = true)
        assertThat(plan.initialDelayMs).isEqualTo(150L)
    }

    @Test
    fun `onConnectOk transitions to Idle and resets retry attempt and keepalive counters`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")

        val accepted = coordinator.onConnectOk("192.168.1.50", "android-tablet", plan.generation)
        assertThat(accepted).isTrue()
        assertThat(coordinator.state.value).isEqualTo(ConnectionState.Idle("192.168.1.50", "android-tablet"))
        assertThat(coordinator.currentRetryAttempt).isEqualTo(0)
        assertThat(coordinator.currentKeepAliveMissed).isEqualTo(0)
    }

    @Test
    fun `stale generation callbacks are ignored`() {
        val coordinator = ConnectionCoordinator()
        val oldPlan = coordinator.startConnect("192.168.1.50", "android-tablet")
        val newPlan = coordinator.startConnect("192.168.1.60", "android-tablet")

        assertThat(coordinator.onConnectOk("192.168.1.50", "android-tablet", oldPlan.generation)).isFalse()
        assertThat(coordinator.state.value).isEqualTo(ConnectionState.Connecting("192.168.1.60"))

        val failedAction = coordinator.onConnectFailed(
            reason = ConnectResult.FailureReason.NETWORK,
            detail = "Timeout",
            policy = ConnectionTransportPolicy.AUTO,
            generation = oldPlan.generation,
        )
        assertThat(failedAction).isEqualTo(ConnectFailureAction.Ignore)

        val rejected = coordinator.onConnectRejected(oldPlan.generation)
        assertThat(rejected).isFalse()

        val eventResult = coordinator.processEvent(
            event = InputLeapEvent.KeepAlive,
            generation = oldPlan.generation,
        )
        assertThat(eventResult).isEqualTo(CoordinatorEventResult.Ignored)

        assertThat(coordinator.onLeaveDebounceExpired(oldPlan.generation)).isFalse()
        assertThat(coordinator.onKeepAliveMiss(oldPlan.generation)).isFalse()
    }

    @Test
    fun `onConnectRejected transitions to Disconnected and clears session`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")

        val accepted = coordinator.onConnectRejected(plan.generation)
        assertThat(accepted).isTrue()
        assertThat(coordinator.state.value).isInstanceOf(ConnectionState.Disconnected::class.java)
        assertThat(coordinator.serverIp).isNull()
        assertThat(coordinator.screenName).isNull()
    }

    @Test
    fun `onConnectFailed schedules retry with backoff for retryable network errors`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")

        val action1 = coordinator.onConnectFailed(
            reason = ConnectResult.FailureReason.NETWORK,
            detail = "Connection refused",
            policy = ConnectionTransportPolicy.AUTO,
            generation = plan.generation,
        )
        assertThat(action1).isEqualTo(ConnectFailureAction.Retry(delayMs = 1_000L, generation = plan.generation))
        assertThat(coordinator.currentRetryAttempt).isEqualTo(1)
        assertThat(coordinator.state.value).isInstanceOf(ConnectionState.Disconnected::class.java)

        val action2 = coordinator.onConnectFailed(
            reason = ConnectResult.FailureReason.NETWORK,
            detail = "Connection refused",
            policy = ConnectionTransportPolicy.AUTO,
            generation = plan.generation,
        )
        assertThat(action2).isEqualTo(ConnectFailureAction.Retry(delayMs = 2_000L, generation = plan.generation))
        assertThat(coordinator.currentRetryAttempt).isEqualTo(2)
    }

    @Test
    fun `onConnectFailed produces terminal action for non-retryable failures`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")

        val action = coordinator.onConnectFailed(
            reason = ConnectResult.FailureReason.UNKNOWN_SCREEN,
            detail = "Unknown client screen",
            policy = ConnectionTransportPolicy.AUTO,
            generation = plan.generation,
        )
        assertThat(action).isEqualTo(
            ConnectFailureAction.Terminal(ConnectResult.FailureReason.UNKNOWN_SCREEN, "Unknown client screen")
        )
        assertThat(coordinator.serverIp).isNull()
        assertThat(coordinator.screenName).isNull()
    }

    @Test
    fun `clipboard events go to the clipboard bridge and Enter records its sequence number`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("10.0.0.1", "server")
        coordinator.onConnectOk("10.0.0.1", "server", plan.generation)

        coordinator.processEvent(InputLeapEvent.Enter(1, 2, seqNum = 42, mask = 0), plan.generation)
        assertThat(coordinator.enterSeqNum).isEqualTo(42)

        val grab = InputLeapEvent.ClipboardGrab(0, 1)
        assertThat(coordinator.processEvent(grab, plan.generation))
            .isEqualTo(CoordinatorEventResult.Clipboard(grab))
        val data = InputLeapEvent.ClipboardData(0, 1, 3, ByteArray(0))
        assertThat(coordinator.processEvent(data, plan.generation))
            .isEqualTo(CoordinatorEventResult.Clipboard(data))
    }

    @Test
    fun `Enter transitions state to Active and Leave debounce transitions back to Idle`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")
        coordinator.onConnectOk("192.168.1.50", "android-tablet", plan.generation)

        val enterResult = coordinator.processEvent(
            event = InputLeapEvent.Enter(x = 100, y = 200, seqNum = 1, mask = 0),
            generation = plan.generation,
        )
        assertThat(enterResult).isEqualTo(CoordinatorEventResult.Enter(100, 200))
        assertThat(coordinator.state.value).isEqualTo(ConnectionState.Active("192.168.1.50", "android-tablet"))
        assertThat(coordinator.isPointerOnScreen).isTrue()

        val leaveResult = coordinator.processEvent(
            event = InputLeapEvent.Leave,
            generation = plan.generation,
        )
        assertThat(leaveResult).isEqualTo(CoordinatorEventResult.Leave)
        assertThat(coordinator.isPointerOnScreen).isTrue() // Remains true until debounce expires

        val debounceHandled = coordinator.onLeaveDebounceExpired(plan.generation)
        assertThat(debounceHandled).isTrue()
        assertThat(coordinator.isPointerOnScreen).isFalse()
        assertThat(coordinator.state.value).isEqualTo(ConnectionState.Idle("192.168.1.50", "android-tablet"))
    }

    @Test
    fun `keepalive miss counter triggers disconnection after 4 misses`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")
        coordinator.onConnectOk("192.168.1.50", "android-tablet", plan.generation)

        assertThat(coordinator.onKeepAliveMiss(plan.generation)).isFalse()
        assertThat(coordinator.currentKeepAliveMissed).isEqualTo(1)
        assertThat(coordinator.onKeepAliveMiss(plan.generation)).isFalse()
        assertThat(coordinator.currentKeepAliveMissed).isEqualTo(2)
        assertThat(coordinator.onKeepAliveMiss(plan.generation)).isFalse()
        assertThat(coordinator.currentKeepAliveMissed).isEqualTo(3)

        // Incoming keepalive resets counter
        coordinator.processEvent(InputLeapEvent.KeepAlive, plan.generation)
        assertThat(coordinator.currentKeepAliveMissed).isEqualTo(0)

        coordinator.onKeepAliveMiss(plan.generation)
        coordinator.onKeepAliveMiss(plan.generation)
        coordinator.onKeepAliveMiss(plan.generation)
        val disconnected = coordinator.onKeepAliveMiss(plan.generation)

        assertThat(disconnected).isTrue()
        assertThat(coordinator.state.value).isInstanceOf(ConnectionState.Disconnected::class.java)
        assertThat(coordinator.isPointerOnScreen).isFalse()
    }

    @Test
    fun `infoAck event clears infoAckPending flag`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")
        coordinator.onConnectOk("192.168.1.50", "android-tablet", plan.generation)
        coordinator.updateScreenBounds(1920, 1080)
        assertThat(coordinator.isInfoAckPending).isTrue()

        val result = coordinator.processEvent(InputLeapEvent.InfoAck(), plan.generation)
        assertThat(result).isEqualTo(CoordinatorEventResult.InfoAck)
        assertThat(coordinator.isInfoAckPending).isFalse()
    }

    @Test
    fun `QueryInfo returns current dimensions and mouse position`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")
        coordinator.updateScreenBounds(1920, 1080)
        coordinator.setInitialCoordinates(500f, 600f)

        val result = coordinator.processEvent(InputLeapEvent.QueryInfo(), plan.generation)
        assertThat(result).isEqualTo(CoordinatorEventResult.QueryInfo(width = 1920, height = 1080, x = 500, y = 600))
    }

    @Test
    fun `relative mouse motion clamps deltas to screen dimensions`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")
        coordinator.onConnectOk("192.168.1.50", "android-tablet", plan.generation)
        coordinator.updateScreenBounds(1000, 1000)
        coordinator.processEvent(InputLeapEvent.InfoAck(), plan.generation)
        coordinator.setInitialCoordinates(500f, 500f)

        val move1 = coordinator.processEvent(InputLeapEvent.MouseMoveRel(300, 400), plan.generation)
        assertThat(move1).isInstanceOf(CoordinatorEventResult.MouseMotion::class.java)
        val motion1 = move1 as CoordinatorEventResult.MouseMotion
        assertThat(motion1.cursorX).isEqualTo(800f)
        assertThat(motion1.cursorY).isEqualTo(900f)

        // Clamping at right/bottom bounds
        val move2 = coordinator.processEvent(InputLeapEvent.MouseMoveRel(500, 500), plan.generation)
        val motion2 = move2 as CoordinatorEventResult.MouseMotion
        assertThat(motion2.cursorX).isEqualTo(1000f)
        assertThat(motion2.cursorY).isEqualTo(1000f)

        // Clamping at left/top bounds
        val move3 = coordinator.processEvent(InputLeapEvent.MouseMoveRel(-2000, -2000), plan.generation)
        val motion3 = move3 as CoordinatorEventResult.MouseMotion
        assertThat(motion3.cursorX).isEqualTo(0f)
        assertThat(motion3.cursorY).isEqualTo(0f)
    }

    @Test
    fun `absolute mouse motion clamps coordinates to screen dimensions`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")
        coordinator.onConnectOk("192.168.1.50", "android-tablet", plan.generation)
        coordinator.updateScreenBounds(1920, 1080)
        coordinator.processEvent(InputLeapEvent.InfoAck(), plan.generation)

        val move = coordinator.processEvent(InputLeapEvent.MouseMoveAbs(2500, -100), plan.generation)
        val motion = move as CoordinatorEventResult.MouseMotion
        assertThat(motion.cursorX).isEqualTo(1920f)
        assertThat(motion.cursorY).isEqualTo(0f)
    }

    @Test
    fun `input gating drops mouse events when mouseEnabled is false`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")
        coordinator.onConnectOk("192.168.1.50", "android-tablet", plan.generation)
        coordinator.mouseEnabled = false

        assertThat(coordinator.processEvent(InputLeapEvent.MouseMoveRel(10, 10), plan.generation))
            .isEqualTo(CoordinatorEventResult.Ignored)
        assertThat(coordinator.processEvent(InputLeapEvent.MouseMoveAbs(100, 100), plan.generation))
            .isEqualTo(CoordinatorEventResult.Ignored)
        assertThat(coordinator.processEvent(InputLeapEvent.MouseDown(1), plan.generation))
            .isEqualTo(CoordinatorEventResult.Ignored)
        assertThat(coordinator.processEvent(InputLeapEvent.MouseUp(1), plan.generation))
            .isEqualTo(CoordinatorEventResult.Ignored)
        assertThat(coordinator.processEvent(InputLeapEvent.MouseWheel(0, 5), plan.generation))
            .isEqualTo(CoordinatorEventResult.Ignored)
    }

    @Test
    fun `input gating drops keyboard events when keyboardEnabled is false`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")
        coordinator.onConnectOk("192.168.1.50", "android-tablet", plan.generation)
        coordinator.keyboardEnabled = false

        assertThat(coordinator.processEvent(InputLeapEvent.KeyDown(65, 0, 30), plan.generation))
            .isEqualTo(CoordinatorEventResult.Ignored)
        assertThat(coordinator.processEvent(InputLeapEvent.KeyUp(65, 0, 30), plan.generation))
            .isEqualTo(CoordinatorEventResult.Ignored)
        assertThat(coordinator.processEvent(InputLeapEvent.KeyRepeat(65, 0, 1, 30), plan.generation))
            .isEqualTo(CoordinatorEventResult.Ignored)
    }

    @Test
    fun `mouse motion is dropped when infoAck is pending`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")
        coordinator.onConnectOk("192.168.1.50", "android-tablet", plan.generation)
        coordinator.updateScreenBounds(1920, 1080)
        assertThat(coordinator.isInfoAckPending).isTrue()

        val relResult = coordinator.processEvent(InputLeapEvent.MouseMoveRel(10, 10), plan.generation)
        assertThat(relResult).isEqualTo(CoordinatorEventResult.Ignored)

        val absResult = coordinator.processEvent(InputLeapEvent.MouseMoveAbs(100, 100), plan.generation)
        assertThat(absResult).isEqualTo(CoordinatorEventResult.Ignored)
    }

    @Test
    fun `unexpected socket disconnect triggers retry when policy allows`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")
        coordinator.onConnectOk("192.168.1.50", "android-tablet", plan.generation)

        val result = coordinator.processEvent(
            event = InputLeapEvent.Unhandled("__DISCONNECTED__"),
            generation = plan.generation,
            policy = ConnectionTransportPolicy.AUTO,
        )
        assertThat(result).isInstanceOf(CoordinatorEventResult.Disconnected::class.java)
        val disconnectedResult = result as CoordinatorEventResult.Disconnected
        assertThat(disconnectedResult.retry).isNotNull()
        assertThat(disconnectedResult.retry?.delayMs).isEqualTo(1_000L)
        assertThat(coordinator.state.value).isInstanceOf(ConnectionState.Disconnected::class.java)
    }

    @Test
    fun `userInitiatedDisconnect suppresses retries on socket disconnect and disconnect calls`() {
        val coordinator = ConnectionCoordinator()
        val plan = coordinator.startConnect("192.168.1.50", "android-tablet")
        coordinator.onConnectOk("192.168.1.50", "android-tablet", plan.generation)

        val newGen = coordinator.disconnect()
        assertThat(newGen).isEqualTo(plan.generation + 1)
        assertThat(coordinator.isUserInitiatedDisconnect).isTrue()
        assertThat(coordinator.state.value).isInstanceOf(ConnectionState.Disconnected::class.java)

        val disconnectEvent = coordinator.processEvent(
            event = InputLeapEvent.Unhandled("__DISCONNECTED__"),
            generation = plan.generation,
        )
        assertThat(disconnectEvent).isEqualTo(CoordinatorEventResult.Ignored)

        val failedAction = coordinator.onConnectFailed(
            reason = ConnectResult.FailureReason.NETWORK,
            detail = "Dropped",
            policy = ConnectionTransportPolicy.AUTO,
            generation = plan.generation,
        )
        assertThat(failedAction).isEqualTo(ConnectFailureAction.Ignore)
    }
}

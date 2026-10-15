# Phase 06 Research: Architectural Modularity

## Executive Summary

Phase 06 addresses requirement `ARCH-01` (tracking Issue #12 by collaborator Guaje) and `ARCH-02` (zero emojis).

The core objective is to decouple connection state transitions, generation counters, retry backoff, keepalive timeouts, coordinate clamping, and input routing decisions out of `ConnectionService.kt` into a pure, platform-independent `ConnectionCoordinator.kt`. This allows all coordination logic to be tested deterministically on the JVM without starting Android Services, Mockito mocks, or Robolectric.

Additionally, as specified in Issue #12, server-keyed preference records (TLS fingerprints and transport modes) in `AppPreferences.kt` will be upgraded to an unambiguous, IPv6-safe versioned representation (`v2|<base64url-server>|<base64url-value>`), with backwards-compatible migration for legacy records.

## Problem Analysis

### 1. Monolithic Responsibilities in `ConnectionService.kt`
Currently, `ConnectionService` (961 lines) intermingles three distinct layers:
1. **Android Framework Integration & Lifecycle**:
   - Foreground service start/stop (`ServiceCompat.startForeground`, `stopForeground`).
   - Notification updates (`NotificationHelper.build`, `NotificationManager.notify`).
   - System settings and IME management (`CursorOverlayService`, `Settings.Secure`, `InputMethodManager`).
   - WindowMetrics resolution and configuration changes (`getScreenBounds()`, `ScreenBoundsResolver`).
   - Shizuku and UHID binder lifecycle listeners.
2. **Connection Lifecycle & State Coordination**:
   - Connection generation counter (`connectGeneration: Int`) guarding against stale callbacks.
   - Retry scheduling and backoff (`retryAttempt`, `RetryDelayCalculator`, `scheduleRetry`).
   - Keepalive miss tracking and timeout disconnection (`keepAliveMissed`, `onKeepAliveMiss`).
   - User-initiated disconnect tracking (`userInitiatedDisconnect`) suppressing automatic retries.
   - Active session clearing (`activeServerIp`, `activeScreenName`, `shouldClearActiveSession`).
3. **Event Filtering & Input Routing**:
   - Mouse enablement filtering (`mouseEnabled`) and keyboard enablement filtering (`keyboardEnabled`).
   - Screen bounds clamping for relative mouse deltas (`(currentMouseX + event.dx).coerceIn(...)`).
   - `infoAckPending` gating (dropping mouse motion while DINF response is unacknowledged).
   - Event translation (Enter/Leave pointer tracking, keepalive responses, QueryInfo dimension responses).

Because domain coordination and input routing are embedded within an `android.app.Service`, testing retry behavior, keepalive timeouts, or generation race conditions previously required mocking or running on Android.

### 2. IPv6 Ambiguity in Server-Keyed Preference Records
In `AppPreferences.kt`, TLS fingerprints (`KEY_FINGERPRINTS`) and transport modes (`KEY_TRANSPORT_MODES`) are persisted as newline-delimited strings:
```text
server:value
```
When a server address is an IPv6 literal (e.g. `2001:db8::1`), splitting on the first colon (`line.substringBefore(":")`) truncates the address to `2001`, causing address corruption and incorrect lookup.

Issue #12 specifies migrating to an unambiguous format:
```text
v2|<base64url-server>|<base64url-value>
```
where both fields are encoded using URL-safe Base64 with no padding.

## Architectural Design

### 1. `ConnectionCoordinator` Domain Boundary

The coordinator is a pure Kotlin class with zero dependencies on `android.*` packages (no `Context`, `Service`, `Binder`, `NotificationManager`, or `WindowManager`).

```
+-------------------------------------------------------------+
|                     ConnectionService                       |
|   (Android Service, Notifications, IME, Window, Overlays)   |
+------------------------------+------------------------------+
                               | delegates decisions
                               v
+-------------------------------------------------------------+
|                    ConnectionCoordinator                    |
|   - Generation tokens                                       |
|   - State machine & transitions                             |
|   - Retry backoff & scheduling decisions                    |
|   - Keepalive timeout tracking                              |
|   - Mouse/Keyboard routing & coordinate clamping            |
+-------------------------------------------------------------+
```

### 2. Coordinator State & Data Contracts

#### Events and Decisions
```kotlin
sealed interface CoordinatorEventAction {
    data class Dispatch(val event: InputLeapEvent) : CoordinatorEventAction
    data class Enter(val x: Int, val y: Int) : CoordinatorEventAction
    object Leave : CoordinatorEventAction
    object SendKeepAlive : CoordinatorEventAction
    data class SendDataInfo(val width: Int, val height: Int, val x: Int, val y: Int) : CoordinatorEventAction
    data class MouseMoveAbs(val x: Float, val y: Float) : CoordinatorEventAction
    data class MouseMoveRel(val dx: Int, val dy: Int, val newX: Float, val newY: Float) : CoordinatorEventAction
    object DisconnectDueToKeepAlive : CoordinatorEventAction
    data class ScheduleRetry(val ip: String, val screenName: String, val delayMs: Long, val generation: Int) : CoordinatorEventAction
    object Ignore : CoordinatorEventAction
}
```

#### Lifecycle Actions
- `startConnect(serverIp: String, screenName: String, force: Boolean): ConnectAttemptPlan`
  - Validates if already connecting/connected to the same IP.
  - Increments and returns `generation`.
  - Determines delay (e.g. 150ms if force reconnect).
- `onConnectOk(transport: ServerTransport, serverIp: String, screenName: String, generation: Int): Boolean`
  - Verifies generation matching.
  - Resets `retryAttempt = 0`, resets keepalive misses, transitions to `Idle`.
- `onConnectRejected(generation: Int): Boolean`
  - Verifies generation, transitions to `Disconnected`, marks session cleared.
- `onConnectFailed(reason: ConnectResult.FailureReason, policy: ConnectionTransportPolicy, generation: Int): ConnectFailurePlan`
  - Evaluates `policy.shouldRetry(reason)` and user-initiated disconnect.
  - Returns whether to retry (with calculated backoff delay and generation) or surface terminal failure.
- `onEvent(event: InputLeapEvent, generation: Int): CoordinatorEventAction`
  - Validates generation.
  - Enforces input gating (`mouseEnabled`, `keyboardEnabled`, `infoAckPending`).
  - Coerces motion coordinates within screen bounds.
- `onKeepAliveMiss(generation: Int): Boolean`
  - Increments keepalive miss counter; returns `true` if threshold reached (>= 4).
- `onUserDisconnect(): Int`
  - Flags user disconnect, increments generation, transitions to `Disconnected`.

### 3. IPv6-Safe Preference Codec

In `AppPreferences.kt` / `ServerRecordCodec.kt`:
- **Format**: `v2|<b64-server>|<b64-val>`
- **Encoding**: URL-safe Base64 without padding.
- **Decoding**:
  * If record starts with `v2|`: decode base64 fields safely.
  * Else: legacy `server:value` parsing. For IPv6 in legacy format, parse last colon segment for known value patterns (64-character hex fingerprint or known transport modes `auto`, `tls`, `plain`).
- **Storage**: When modifying or saving, output canonical `v2|...` records.

## Test Strategy

1. **`ConnectionCoordinatorTest` (JVM, pure)**:
   - Generation safety: events or failures from older generations are ignored.
   - Retry suppression: user-initiated disconnect prevents retries on disconnect events.
   - Unexpected socket disconnect: triggers retry with exponentially increasing delay according to `RetryDelayCalculator`.
   - Keepalive timeout: 3 misses remain connected, 4th miss triggers `DisconnectDueToKeepAlive`.
   - Input gating:
     * When `mouseEnabled = false`: motion, buttons, and wheel return `Ignore`.
     * When `keyboardEnabled = false`: key down, up, and repeat return `Ignore`.
     * When `infoAckPending = true`: motion returns `Ignore`.
   - Screen bounds clamping: relative motion deltas correctly clamp within `[0, width]` and `[0, height]`.
   - Enter/Leave transitions: Enter transitions state to `Active`, Leave transitions state to `Idle`.

2. **`AppPreferencesTest`**:
   - IPv6 server address storage and retrieval with full fidelity.
   - Backward-compatible migration of legacy `ip:fingerprint` and `ip:mode` records.
   - Malformed records safely ignored without crashing or data loss.

3. **`ConnectionService` Regression**:
   - All existing service integration and bounds tests continue to pass.
   - Full JVM test suite remains 100% green.

## Zero Emoji Policy (`ARCH-02`)
Strictly enforce zero emojis in code, comments, test names, logs, and commit messages.

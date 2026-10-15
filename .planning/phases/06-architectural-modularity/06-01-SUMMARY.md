# Plan 06-01 Summary: Architectural Modularity & Pure Domain Coordination

## Execution Overview

Plan 06-01 extracted pure domain coordination logic from `ConnectionService` into `ConnectionCoordinator`, implemented an IPv6-safe versioned server record codec in `AppPreferences`, and reduced `ConnectionService` to an Android lifecycle and side-effect shell (`ARCH-01`, `ARCH-02`, Issue #12).

All 3 tasks were executed cleanly with zero emojis across code, tests, docs, and git commit history:
- Task 06-01-01: Extracted pure JVM `ConnectionCoordinator` domain logic and comprehensive unit tests (`afc5173`).
- Task 06-01-02: Implemented IPv6-safe versioned server record codec in `AppPreferences` with backwards-compatible legacy migration (`f537a7e`).
- Task 06-01-03: Refactored `ConnectionService` into an Android lifecycle shell delegating domain decisions to `ConnectionCoordinator` (`d76e504`).

## Key Implementation Details

1. **Pure Domain Coordination (`ARCH-01`, #12)**:
   - `ConnectionCoordinator.kt`:
     * Pure Kotlin class with zero dependencies on `android.*` packages.
     * Manages `StateFlow<ConnectionState>` through lifecycle transitions (`Connecting` -> `Handshaking` -> `Idle` -> `Active` -> `Disconnected`).
     * Manages connection generation tokens (`generation: Int`) and filters out stale asynchronous callbacks (`isCurrentGeneration`).
     * Tracks exponential retry backoff using `RetryDelayCalculator.getDelay(retryAttempt)` and handles user-initiated disconnect suppression.
     * Tracks keepalive miss counts with automatic timeout disconnection after 4 consecutive misses.
     * Enforces input gating: drops relative/absolute motion, button presses, and scroll wheels when `mouseEnabled` is false; drops key events when `keyboardEnabled` is false; drops motion when `infoAckPending` is true.
     * Clamps relative and absolute mouse coordinates within screen boundaries (`[0, screenWidth]`, `[0, screenHeight]`).
     * Returns structured, actionable results (`CoordinatorEventResult`, `ConnectAttemptPlan`, `ConnectFailureAction`, `ScreenBoundsUpdatePlan`) allowing callers to execute pure side-effects.
   - `ConnectionCoordinatorTest.kt`:
     * 19 pure JVM unit tests validating state transitions, generation validation, retry escalation, keepalive timeouts, input gating, coordinate bounds clamping, and orientation changes without Android Service lifecycle, Mockito mocks, or Robolectric.

2. **IPv6-Safe Preference Codec (`ARCH-01`, #12)**:
   - `ServerRecordCodec.kt`:
     * Canonical representation: `v2|<base64url-server>|<base64url-value>` using URL-safe unpadded Base64.
     * Backwards compatibility: safely parses legacy `server:value` records by splitting on the last colon (`lastIndexOf(':')`), preventing truncation of IPv6 literals (e.g. `2001:db8::1`).
     * Safely discards malformed lines without throwing exceptions or corrupting data.
   - `AppPreferences.kt`:
     * Integrated `ServerRecordCodec` across `saveFingerprint`, `removeFingerprint`, `fingerprintFor`, `allFingerprints`, `saveTransport`, `clearTransport`, `transportFor`, and `savedServerAddresses`.
     * Existing legacy records are automatically migrated to `v2` format on next modification.
   - `ServerRecordCodecTest.kt` & `AppPreferencesTest.kt`:
     * Verified roundtrip preservation of IPv6 addresses (e.g. `2001:db8::1`, `[fe80::1%wlan0]:24800`, `::1`).
     * Verified legacy recovery, deduplication, and migration.

3. **Android Lifecycle Shell (`ARCH-01`)**:
   - `ConnectionService.kt`:
     * Removed all domain coordination state (`retryAttempt`, `connectGeneration`, `infoAckPending`, `userInitiatedDisconnect`, `mouseEnabled`, `keyboardEnabled`, `absolutePointer`, `pointerOnScreen`, `currentMouseX`, `currentMouseY`, `screenWidth`, `screenHeight`).
     * Reduced service responsibilities to Android foreground service lifecycle, notification dispatch, window metrics resolution, settings content observers, IME auto-switching, overlay management, and injector hardware dispatch.
     * Retained `ConnectionStateMachine` class at the bottom of the file for full backwards compatibility with existing unit tests.

## Verification Results

- **Unit Tests**: 552 passing JVM tests (+27 new tests, 0 failures, 0 errors, 1 skipped optional live test).
- **Python Setup Helper Tests**: 16 passing unit tests (0 failures).
- **Code Coverage**: `:koverVerifyDebugJvm` passed with full compliance.
- **Emoji Audit**: 0 emojis across all modified files and commit messages (`ARCH-02`).

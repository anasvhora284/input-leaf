---
phase: "06"
slug: "architectural-modularity"
status: pending
requirements:
  - ARCH-01
  - ARCH-02
---

# Phase 06 Validation Contract: Architectural Modularity

## 1. Test Architecture & Automated Verification

All requirements must be validated via automated, deterministic JVM unit tests without Android Context dependencies, mocks, or wall-clock timing races:

| Requirement | Test Class / Suite | Automated Command | Target Invariants |
|-------------|--------------------|-------------------|-------------------|
| **ARCH-01** | `ConnectionCoordinatorTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.service.ConnectionCoordinatorTest"` | State machine transitions (Connecting -> Handshaking -> Idle -> Active -> Disconnected); generation token mismatch filtering; retry backoff calculation; keepalive timeout at 4 misses; input gating (mouse/keyboard disabled, infoAck pending); relative coordinate clamping to screen dimensions. |
| **ARCH-01** | `AppPreferencesTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.storage.AppPreferencesTest"` | IPv6-safe `v2|<base64url-server>|<base64url-value>` encoding and decoding; backward-compatible migration of legacy `server:value` records; IPv6 literal persistence without truncation. |
| **ARCH-01** | `ConnectionStateMachineTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.service.ConnectionStateMachineTest"` | Backwards compatibility of underlying state machine transitions. |
| **ARCH-01** | `ConnectionServiceBoundsTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.service.ConnectionServiceBoundsTest"` | Screen bounds resolution across display metrics fallbacks. |
| **ARCH-02** | Zero-emoji audit | `python3 -c "..."` | Strictly ZERO emojis in any added or modified code, tests, documentation, or commit messages. |
| **Full Suite** | Regression & Kover | `./gradlew :app:testDebugUnitTest :koverVerifyDebugJvm` | Complete test suite passes with zero failures and maintains baseline code coverage. |

---

## 2. Acceptance Criteria

1. `ConnectionCoordinator` encapsulates connection generations, state transitions, retry backoff calculation, keepalive miss tracking, input gating, coordinate bounds clamping, and event routing decisions without depending on `android.*` packages.
2. `ConnectionCoordinatorTest` runs purely on the JVM (no Android Service lifecycle, no Mockito, no Robolectric) and tests all coordinator states, input gating scenarios, coordinate boundaries, and failure plans.
3. `ServerRecordCodec` reliably handles IPv6 addresses (e.g. `2001:db8::1`, `[fe80::1%wlan0]:24800`) using URL-safe unpadded Base64 encoding in canonical `v2|...` format while gracefully migrating legacy records.
4. `ConnectionService` delegates state, retry, keepalive, generation, and input routing decisions to `ConnectionCoordinator`, functioning as an Android lifecycle shell.
5. All tests in `:app:testDebugUnitTest` pass with 0 failures, and `:koverVerifyDebugJvm` passes.
6. Zero emojis are introduced in any modified or new files.

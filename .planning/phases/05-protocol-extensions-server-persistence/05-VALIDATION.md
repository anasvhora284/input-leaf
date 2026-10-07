---
phase: "05"
slug: "protocol-extensions-server-persistence"
status: pending
requirements:
  - PROT-01
  - PROT-02
  - PROT-03
  - ARCH-02
---

# Phase 05 Validation Contract: Protocol Extensions & Server Persistence

## 1. Test Architecture & Automated Verification

All requirements must be validated via automated, deterministic JVM unit tests and script test suites without network dependencies or flaky wall-clock sleeps:

| Requirement | Test Class / Suite | Automated Command | Target Invariants |
|-------------|--------------------|-------------------|-------------------|
| **PROT-01** | `ProtocolParserTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.protocol.ProtocolParserTest"` | 4-byte signed 16-bit `DMRM` relative delta decoding (`-32768..32767`), 8-byte backward compatibility, and length validation. |
| **PROT-01** | `InputLeapConnectionTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.network.InputLeapConnectionTest"` | Handshake completes on `DSOP` after `DINF`; server hello with `SYNERGY` magic succeeds; `EUNK` yields `UNKNOWN_SCREEN`; `EBAD` yields `PROTOCOL_ERROR`; incompatible major versions rejected. |
| **PROT-02** | `ProtocolScanCodeDecoderTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.inject.ProtocolScanCodeDecoderTest"` | Mac Carbon keycode +1 translation to Linux evdev; key release tracking during chord source shifts; Mac modifiers and navigation key mapping. |
| **PROT-03** | `AppPreferencesTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.storage.AppPreferencesTest"` | Server persistence across DataStore restarts; recovery of legacy favorite/last/fingerprint records; unstarring preserves saved address. |
| **PROT-03** | `ServerListPersistenceTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.ui.ServerListPersistenceTest"` | `mergeServerLists` retains saved servers during empty scans and merges discovered server details without duplicates. |
| **PROT-01** | Python helper tests | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests -v` | Synergy 3 macOS registration helper schema validation, preview output, backup/restore mechanics, and error handling. |
| **ARCH-02** | Zero-emoji audit | `python3 -c "..."` | Strictly ZERO emojis across all added/modified code, tests, documentation, and commits. |
| **Full Suite** | Regression & Kover | `./gradlew :app:testDebugUnitTest :koverVerifyDebugJvm` | Complete test suite passes with zero failures and maintains baseline code coverage. |

---

## 2. Acceptance Criteria

1. `InputLeapConnection.connect` completes cleanly with Synergy 3.7+ servers after receiving `DSOP`, without premature success or unexpected disconnections.
2. `ProtocolParser.readNext` parses both 4-byte and 8-byte `DMRM` payloads into `InputLeapEvent.MouseMoveRel` with accurate signed deltas.
3. Typing physical keys on a Mac Synergy server generates the expected evdev scancodes without sending Escape or getting keys stuck in the down position.
4. Manually added server addresses persist across application process destruction and are visible in the server list even before or without network scan results.
5. All tests in `:app:testDebugUnitTest` and `scripts/tests` pass with 0 failures, and `:koverVerifyDebugJvm` passes.
6. Zero emojis are introduced in any file or commit message.

---
phase: "05"
slug: "protocol-extensions-server-persistence"
status: verified
verified_commit: "f745e2197d19565f12952db5877c8e9b8915b497"
verified_at: "2026-10-07T10:25:00.000Z"
requirements:
  - PROT-01
  - PROT-02
  - PROT-03
  - ARCH-02
---

# Phase 05 Verification Report: Protocol Extensions & Server Persistence

## 1. Summary of Work

Phase 05 delivered protocol compatibility with Synergy 3.7+ desktop servers, accurate macOS physical keyboard translation, persistent server list storage across restarts, and a macOS registration helper:
1. **Synergy 3.7+ Protocol & Handshake Resilience (PROT-01, #47, #53)**:
   - Handshake now completes strictly upon receiving `DSOP` (session options) after `dinfSent`, preventing premature connection when servers acknowledge screen dimensions via `CIAK` before screen validation.
   - Server errors `EUNK` (unknown screen) and `EBAD` (protocol bad message) are mapped to `ConnectResult.FailureReason.UNKNOWN_SCREEN` and `PROTOCOL_ERROR` respectively and marked permanent without futile retries or fallbacks.
   - Server major version mismatch is rejected before sending client data.
   - `ProtocolParser` decodes 4-byte signed 16-bit relative mouse packets (`DMRM%2i%2i`) alongside legacy 8-byte 32-bit packets.
   - Added optional smoke test `Synergy3LiveTest` for live Synergy 3.7 server verification.
2. **Mac Carbon Virtual Keycode Translation & Chord Tracking (PROT-02, #53)**:
   - Implemented `MacKeyCode` mapping Apple Carbon `kVK_*` virtual keycodes to Linux evdev scancodes.
   - Enhanced `ProtocolScanCodeDecoder` with multi-encoding detection (`EVDEV`, `X11`, `MAC`).
   - Added `decodeKeyEvent` and `pressedKeys` mapping table to ensure key releases match the exact physical scancode recorded on press even when layout detection shifts mid-chord.
   - Added `clearPressedKeys()` on disconnect and keyboard detach to eliminate stuck keys.
   - Integrated into both `ShizukuInputInjector` and `AccessibilityInputInjector`.
3. **Server Persistence & macOS Registration Helper (PROT-03, #53)**:
   - Added `saved_servers` preference in `AppPreferences` with `DataStore<Preferences>` constructor for unit testing.
   - Migrated and recovered legacy addresses from `last_server_ip`, `favorite_servers`, `tls_fingerprints`, and `server_transport_modes`.
   - Updated `MainViewModel` with `mergeServerLists` to combine saved and discovered hosts with stable sorting and deduplication.
   - Provided standalone Python script `scripts/synergy3_setup.py` with 16 unit tests in `scripts/tests/test_synergy3_setup.py`.
   - Authored complete user guide in `docs/synergy3-android.md` and added CI verification step.
4. **Code Quality and Zero Emojis (ARCH-02)**:
   - Maintained ZERO emojis across all code, tests, docs, scripts, and commit messages.

## 2. Automated Test Verification Results

| Test Class / Suite | Command | Result |
|--------------------|---------|--------|
| `ProtocolParserTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.protocol.ProtocolParserTest"` | Passed (18/18 tests) |
| `InputLeapConnectionTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.network.InputLeapConnectionTest"` | Passed (34/34 tests) |
| `ProtocolScanCodeDecoderTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.inject.ProtocolScanCodeDecoderTest"` | Passed (10/10 tests) |
| `AppPreferencesTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.storage.AppPreferencesTest"` | Passed (5/5 tests) |
| `ServerListPersistenceTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.ui.ServerListPersistenceTest"` | Passed (2/2 tests) |
| Python Setup Helper Tests | `python3 -m unittest discover -s scripts/tests -v` | Passed (16/16 tests) |
| Full JVM Test Suite | `./gradlew :app:testDebugUnitTest` | Passed (525/525 tests, 1 skipped optional live test) |
| Kover Coverage Verification | `./gradlew :koverVerifyDebugJvm` | Passed |

## 3. Requirements Traceability Matrix

| Requirement | Description | Verified By | Status |
|-------------|-------------|-------------|--------|
| **PROT-01** | Support Synergy 3.7+ desktop servers by completing handshakes strictly on `DSOP` after `DINF`, reporting refusal codes (`UNKNOWN_SCREEN`, `PROTOCOL_ERROR`) without auto-retrying permanent errors, and decoding 4-byte signed 16-bit relative mouse deltas (`DMRM`) alongside legacy 8-byte 32-bit packets (#47, #53). | `ProtocolParserTest`, `InputLeapConnectionTest`, `ConnectionTransportPolicyTest` | Complete |
| **PROT-02** | Translate Mac Carbon virtual keycodes to Linux evdev scancodes via `MacKeyCode.kt`, adaptive multi-encoding detection (`EVDEV`, `X11`, `MAC`), and release tracking (`pressedKeys`) to prevent stuck keys (#53). | `ProtocolScanCodeDecoderTest`, `ShizukuInputInjector`, `AccessibilityInputInjector` | Complete |
| **PROT-03** | Persist manually added, connected, and favorited servers in DataStore (`saved_servers`), migrate legacy records, merge saved and discovered servers in `MainViewModel`, and provide the macOS registration helper script and guide (#53). | `AppPreferencesTest`, `ServerListPersistenceTest`, `test_synergy3_setup.py`, `docs/synergy3-android.md` | Complete |
| **ARCH-02** | Strictly enforce ZERO emojis across code, tests, docs, scripts, and commit messages (`CODE_QUALITY_STANDARDS.md`). | Python regex audit across all project files and git commits | Complete |

## 4. Phase Verification Digest

```
digest: v1:sha256:8894170e28d84a7e93096417730e6203cf31a89c93a81a7d65df35ff65a0b5df
phase: 05-protocol-extensions-server-persistence
commit: f745e2197d19565f12952db5877c8e9b8915b497
timestamp: 2026-10-07T10:25:00.000Z
status: passed
```

# Plan 05-01 Summary: Protocol Extensions & Server Persistence

## Execution Overview

Plan 05-01 completed the protocol extensions and server persistence features required for Synergy 3.7+ compatibility, macOS Carbon keycode translation, and persistent server list storage across restarts (`PROT-01`, `PROT-02`, `PROT-03`, `ARCH-02`).

All 3 tasks were executed cleanly with zero emojis across code, tests, docs, and git commit history:
- Task 05-01-01: Handshake completion on `DSOP`, signed 16-bit `DMRM` parsing, and refusal error handling (`6c47bf8`).
- Task 05-01-02: Mac Carbon virtual keycode mapping, multi-source adaptive decoding, and chord release tracking (`bf43e0f`).
- Task 05-01-03: `saved_servers` DataStore persistence, network scan merging, and macOS registration helper script/guide (`f745e21`).

## Key Implementation Details

1. **Protocol Handshake & DMRM Deltas (`PROT-01`)**:
   - `ProtocolParser.kt`: Added support for 4-byte signed 16-bit relative mouse packets (`s16(0)`, `s16(2)`) alongside legacy 8-byte 32-bit packets.
   - `ConnectResult.kt`: Added `UNKNOWN_SCREEN` and `PROTOCOL_ERROR` to `FailureReason`.
   - `ConnectionTransportPolicy.kt`: Marked `UNKNOWN_SCREEN` and `PROTOCOL_ERROR` as permanent failures (`shouldRetry = false`, `shouldFallbackWithinAttempt = false`).
   - `InputLeapConnection.kt`:
     * Enforced server major version check against `ProtocolConstants.PROTOCOL_MAJOR`.
     * Completed handshake strictly on `DSOP` options receipt after `dinfSent`.
     * Mapped `InputLeapEvent.Unknown` (`EUNK`) to `UNKNOWN_SCREEN` and `InputLeapEvent.BadMessage` (`EBAD`) to `PROTOCOL_ERROR`.
     * Removed the lenient fallback that passed before server acceptance.
   - Added `Synergy3LiveTest.kt` for optional live server validation using environment variables.

2. **Mac Carbon Virtual Keycodes & Release Tracking (`PROT-02`)**:
   - Created `MacKeyCode.kt` mapping Apple Carbon `kVK_*` virtual keycodes to Linux evdev scancodes.
   - `ProtocolScanCodeDecoder.kt`:
     * Added adaptive `Encoding` detection across `EVDEV`, `X11`, and `MAC`.
     * Implemented `decodeKeyEvent(button, keysym, isDown)` with `pressedKeys` mapping table to ensure key-up events release the exact scancode recorded on down.
     * Added `clearPressedKeys()` to purge held keys on detach or disconnect.
   - Integrated `decodeKeyEvent` and `clearPressedKeys` into both `ShizukuInputInjector.kt` and `AccessibilityInputInjector.kt`.

3. **Server Persistence & macOS Registration Helper (`PROT-03`)**:
   - `AppPreferences.kt`:
     * Added `DataStore<Preferences>` secondary constructor for unit testability.
     * Added `KEY_SAVED_SERVERS = stringSetPreferencesKey("saved_servers")`.
     * Added `savedServers` Flow with legacy recovery of historical addresses from `last_server_ip`, `favorite_servers`, `tls_fingerprints`, and `server_transport_modes`.
     * Implemented `saveServer(address)` and automatic server retention in `saveLastServer`, `toggleFavoriteServer`, and `removeFingerprint`.
     * Preserved Phase 4 `KEY_POINTER_DEVICE` and `KEY_ROOT_GRANT_REMEMBERED` preferences.
   - `MainViewModel.kt`:
     * Implemented `mergeServerLists(savedAddresses, discovered)` to deduplicate and stably order server lists.
     * Combined `prefs.savedServers` and `_discoveredServers` into `discoveredServers` StateFlow.
     * Updated `addManualServer` to persist manually entered servers via `prefs.saveServer`.
   - UI strings generalized to remove hardcoded Deskflow assumptions in `ServerListScreen.kt`, `SettingsScreen.kt`, and `FingerprintDialog.kt`.
   - Created `scripts/synergy3_setup.py` and unit test suite `scripts/tests/test_synergy3_setup.py`.
   - Created comprehensive guide `docs/synergy3-android.md` and updated `docs/TESTING.md`, `.github/workflows/ci.yml`.

## Verification Results

- **Unit Tests**: 525 passing JVM tests (0 failures, 0 errors, 1 skipped optional live test).
- **Python Setup Helper Tests**: 16 passing unit tests (0 failures).
- **Code Coverage**: `:koverVerifyDebugJvm` passed with full compliance.
- **Emoji Audit**: 0 emojis found across any committed or documentation files.

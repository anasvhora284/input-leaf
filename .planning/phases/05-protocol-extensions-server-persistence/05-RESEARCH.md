# Phase 05 Research: Protocol Extensions & Server Persistence

## 1. Executive Summary

Phase 05 expands Input Leaf's desktop server compatibility to Synergy 3.7+ (including macOS servers) and resolves server list persistence across application restarts:
1. **Synergy 3.7+ Handshake & Protocol (PROT-01, #47, #53)**:
   - Handshake sequence: Server Hello (`Synergy`) -> Client Hello -> `QINF` -> `DINF` -> `CIAK` -> `CROP` -> `DSOP`.
   - On Synergy 3.7.2+, `CIAK` merely acknowledges dimensions; session acceptance is confirmed upon receiving `DSOP`. If the screen name is unregistered, Synergy responds with `EUNK` after `CIAK`. Completing on `CIAK` results in premature connection states.
   - Relative motion (`DMRM`): Synergy sends 4 bytes (`s16 dx, s16 dy`), whereas older Input Leaf implementations strictly enforced 8 bytes (`s32 dx, s32 dy`), triggering a fatal `ProtocolException`.
   - Failure reporting: Explicitly differentiate `UNKNOWN_SCREEN` and `PROTOCOL_ERROR`, disabling unhelpful auto-retry loops on permanent authentication/registration errors.
2. **Mac Carbon Keycode Translation (PROT-02, #53)**:
   - macOS Synergy servers pass Carbon physical virtual keycodes (`kVK_*` from Apple HIToolbox) offset by `+1` (where 0 indicates no physical key).
   - In `ProtocolScanCodeDecoder`, implement multi-source adaptive decoding (`EVDEV`, `X11`, `MAC`), and maintain release state tracking (`pressedKeys`) to guarantee correct key-up releases even if encoding detection resolves mid-chord.
3. **Server Persistence in DataStore (PROT-03, #53)**:
   - Manually added, connected, and favorited servers must persist in DataStore (`KEY_SAVED_SERVERS = stringSetPreferencesKey("saved_servers")`).
   - Recover legacy addresses from `favorite_servers`, `last_server_ip`, `tls_fingerprints`, and `server_transport_modes`.
   - Merge persisted and discovered mDNS servers in `MainViewModel` via `mergeServerLists(saved, discovered)` so that empty network scans or offline periods do not hide configured servers.
4. **Code Quality and Architecture (ARCH-02)**:
   - Zero emojis across all code, tests, documentation, and git commit messages.
   - Comprehensive JVM unit test coverage without real-time delays.

---

## 2. Technical Findings & Root Cause Analysis

### 2.1 Synergy 3.7+ Handshake & Protocol Dynamics (#47, #53)

In standard Barrier and Input Leap protocols:
```
Server Hello ("Barrier" / "Synergy" 1.6+) -> Client Hello
Server QINF -> Client DINF
Server CIAK / CROP / DSOP
```
In Synergy 3.7.2 on macOS:
- Synergy announces `"Synergy"` magic with major 1, minor 8 (or 7).
- After the client sends `DINF` (screen dimensions), Synergy sends `CIAK` (Confirm Info Acknowledged).
- However, if the screen name was not explicitly registered in Synergy's database, the server transmits `EUNK` ("Unknown Screen") immediately after `CIAK` and disconnects.
- In `InputLeapConnection.kt`, the previous handshake logic marked the connection as complete if `helloSent && dinfSent` (lenient fallback) or if `sawPostDinf` encountered `CIAK`. Consequently, the Android app transitioned to `ConnectionState.Active`, only to be severed immediately by EOF.
- **Solution**: The handshake must require `DSOP` (Set Options) following `DINF` to confirm session acceptance. Receiving `EUNK` must cleanly fail the connection with `ConnectResult.FailureReason.UNKNOWN_SCREEN` and a user-friendly error message guiding screen registration.

### 2.2 Relative Mouse Delta Wire Format (`DMRM`)

`ProtocolConstants.TAG_MOUSE_REL = "DMRM"`:
- Synergy 3.x, Barrier, and Deskflow encode mouse relative movements as:
  `DMRM %2i %2i` (2 bytes signed X delta, 2 bytes signed Y delta = 4 payload bytes).
- Input Leaf previously expected 8 bytes (`s32(0)` and `s32(4)`):
  ```kotlin
  requireExact(payload.size, 8, tag)
  ```
- Any movement packet from Synergy 3.x threw `ProtocolException("Malformed DMRM message: expected 8 payload bytes, got 4")`.
- **Solution**: Accept 4-byte payloads (`s16(0)`, `s16(2)`) while continuing to accept 8-byte payloads (`s32(0)`, `s32(4)`) for backwards compatibility.

### 2.3 Mac Carbon Keycode Mapping

On macOS Synergy servers, the `button` field in `DKDN` / `DKUP` is `CarbonVirtualKeyCode + 1`:
- For example, physical key 'A' has Carbon code `0`, transmitted as `button = 1`.
- If treated as Linux evdev, scancode 1 is `KEY_ESC`, causing typing 'A' on a Mac server to trigger Android back/escape navigation!
- **Solution**:
  - Implement `MacKeyCode.kt` mapping Carbon `kVK_*` codes (0..127) to Linux evdev scancodes.
  - In `ProtocolScanCodeDecoder`, implement triple-candidate learning:
    - `button == expected` -> `Encoding.EVDEV`
    - `button == expected + 8` -> `Encoding.X11`
    - `MacKeyCode.toEvdev(button - 1) == expected` -> `Encoding.MAC`
  - When ambiguous, defer until an unambiguous key (e.g. Space = 50 on Mac vs 57 on Linux, or modifiers) resolves the encoding.
  - Store pressed keys in `pressedKeys: MutableMap<Int, Int>` so that `decodeKeyEvent(button, keysym, isDown = false)` always releases the exact evdev scancode that was pressed, preventing stuck keys if learning finishes mid-press.

### 2.4 Server Persistence Architecture

Currently:
- `AppPreferences` stores `favorite_servers` (as a newline-delimited string) and `last_server_ip`.
- Manually entered servers via `addManualServer` only updated the in-memory `_discoveredServers` state in `MainViewModel`.
- When the app is closed or restarted, manual servers disappear completely. Furthermore, if network mDNS discovery fails or is blocked on Wi-Fi isolation, the server list appears completely empty.
- **Solution**:
  - Add `KEY_SAVED_SERVERS = stringSetPreferencesKey("saved_servers")`.
  - Expose constructor `AppPreferences(dataStore: DataStore<Preferences>)` to permit deterministic unit testing with temporary file DataStores.
  - Provide `savedServers: Flow<Set<String>>` which automatically migrates/recovers historical addresses from `KEY_FAVORITE_SERVERS`, `KEY_LAST_SERVER_IP`, `KEY_FINGERPRINTS`, and `KEY_TRANSPORT_MODES`.
  - In `MainViewModel`, combine `prefs.savedServers` and `_discoveredServers` using `mergeServerLists(...)`, populating the server list immediately upon startup.

---

## 3. Scope Boundaries & Non-Goals

- Non-goal: Re-implementing Synergy 3's cloud or proprietary management daemon on Android. The connection is made directly to Synergy's local TCP 24800 core socket using existing TLS/mTLS capabilities.
- Non-goal: Modifying Android permissions or root injectors for key mapping. Keycode translation occurs entirely within the protocol decode layer before passing evdev scancodes to UHID or injectInputEvent.

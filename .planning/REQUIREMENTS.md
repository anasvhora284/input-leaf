# Requirements: Input Leaf Android

**Defined:** 2026-10-05
**Core Value:** Ultra-low latency, rock-solid, hardware-grade input injection and seamless KVM connectivity across diverse Android devices and form factors with pristine code quality and zero regressions.

## v1 Requirements

### Kernel & UHID Stability

- [ ] **UHID-01**: `UhidChannel.stopReader()` unblocks `readerThread` cleanly using bounded polling / timeout on `/dev/uhid`, ensuring `reader.join()` never times out and prevents zombie reader thread leaks (#48).
- [ ] **UHID-02**: `UhidChannel.awaitReady()` exits the readiness window upon `sawStart || sawOpen`, eliminating 2-second device attachment latency penalties on OPEN-less OEM ROMs (#49).
- [ ] **UHID-03**: Remove dead stream-model methods `waitForStart()` and `drainPayload()` from `UhidProtocol` and eliminate unreachable deferred INPUT2 queue from `UhidChannel` (#50).

### Deterministic Testing & CI Guardrails

- [ ] **TEST-01**: Replace all `Thread.sleep` and wall-clock assertions in JVM unit tests (`InputInjectorServiceEnterTest`, `HidAttachmentControllerTest`) with virtual clocks (`UhidReadinessConfig`) and coroutine test scopes (#51).
- [ ] **TEST-02**: Add Android smoke test workflow (`.github/workflows/android-smoke.yml`) and dependency provider (`AppDependencies.kt`, `TestAppDependencies.kt`) executing without real hardware or network dependencies (#13).
- [ ] **TEST-03**: Enforce 100% patch test coverage on core protocol and UHID modules via Kover and Codecov CI checks.

### IPC Lifecycle & Code Quality Follow-ups

- [ ] **IPC-01**: `InputInjectorService.attachClient` assigns monotonic generation tokens so stale death notifications from replaced clients do not terminate active virtual devices (#52).
- [ ] **IPC-02**: Fix `HidMouseEnterWarp` planning and logging to accurately record applied warp steps on partial writes without double-applying snaps (#52).
- [ ] **IPC-03**: Append new AIDL methods strictly after `destroy()` in `IInputInjector.aidl` to maintain stable transaction codes (#52).

### Form-Factor & OEM Compatibility

- [ ] **OEM-01**: Resolve Huawei MatePad SE 11 connection crash (#54) by defensively guarding window metrics, display insets, and background service start constraints.
- [ ] **OEM-02**: Add Android TV support by providing Leanback banner metadata in `AndroidManifest.xml`, declaring touchscreen non-mandatory, and mapping arrow keys to Android DPAD keycodes.
- [ ] **OEM-03**: Expose a user settings toggle for Pointer Device (Relative Mouse, Absolute Stylus/Pen, Touch Fallback) and complete `HidPen` absolute digitizer report injection.

### Protocol Extensions & Server Persistence

- [ ] **PROT-01**: Support Synergy 3.7+ protocol negotiation, complete handshake on `DSOP`, and handle signed 16-bit `DMRM` relative delta packets (#47, #53).
- [ ] **PROT-02**: Translate Mac Carbon keycodes to Linux evdev usages for cross-platform desktop server compatibility (#53).
- [ ] **PROT-03**: Persist manually added, used, and favorited servers in DataStore so they survive app restarts and merge cleanly with local mDNS scans (#53).

### Architectural Modularity

- [ ] **ARCH-01**: Extract `ConnectionCoordinator` out of `ConnectionService` to isolate connection generations, retry backoff, keepalive timeouts, and input routing into pure, JVM-testable domain logic (#12).
- [ ] **ARCH-02**: Ensure zero emojis across all commit messages, files, and outputs in compliance with `CODE_QUALITY_STANDARDS.md`.

## v2 Requirements

### Advanced Input Capabilities

- **INP-01**: Support 5-button mouse mapping (buttons 4 & 5 for browser back/forward navigation) in UHID descriptors and dispatcher (#16).
- **INP-02**: Add multi-touch and pinch gesture emulation over Android Accessibility or multi-finger digitizer reports.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Cloud Relays / WebRTC signaling | Input Leaf is strictly local LAN over Wi-Fi / Ethernet for maximum privacy and minimal latency. |
| Dagger / Hilt DI | Introduces kapt/ksp compile time overhead; lightweight manual dependency factories preferred. |
| Real-time sleeps in tests | Violates testing principles in docs/TESTING.md and creates flaky CI builds. |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| UHID-01 | Phase 1 | Pending |
| UHID-02 | Phase 1 | Pending |
| UHID-03 | Phase 1 | Pending |
| TEST-01 | Phase 2 | Pending |
| TEST-02 | Phase 2 | Pending |
| TEST-03 | Phase 2 | Pending |
| IPC-01 | Phase 3 | Pending |
| IPC-02 | Phase 3 | Pending |
| IPC-03 | Phase 3 | Pending |
| OEM-01 | Phase 4 | Pending |
| OEM-02 | Phase 4 | Pending |
| OEM-03 | Phase 4 | Pending |
| PROT-01 | Phase 5 | Pending |
| PROT-02 | Phase 5 | Pending |
| PROT-03 | Phase 5 | Pending |
| ARCH-01 | Phase 6 | Pending |
| ARCH-02 | Phase 6 | Pending |

---
*Requirements defined: 2026-10-05*

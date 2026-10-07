# Roadmap: Input Leaf Android

## Overview

Harden kernel-level input stability and testing determinism, resolve open collaborator review findings, expand form-factor and protocol support (Synergy 3, Android TV, Stylus/Pen), and extract a pure Kotlin connection coordinator to deliver high-quality, zero-regression KVM client performance.

## Phases

- [x] **Phase 1: Core Kernel UHID Fixes** - Resolve un-stoppable reader thread leaks, eliminate OPEN-less attach latency stalls, and purge dead stream-model UHID methods (#48, #49, #50) (completed 2026-10-06)
- [x] **Phase 2: Deterministic Testing & CI Guardrails** - Remove all wall-clock sleeps from JVM tests and establish Android smoke testing workflow with coverage guardrails (#51, #13) (completed 2026-10-06)
- [x] **Phase 3: IPC Lifecycle & Review Follow-ups** - Prevent token death races from killing active devices, correct enter-warp snap reporting, and stabilize AIDL method order (#52) (completed 2026-10-06)
- [x] **Phase 4: Form-Factor & OEM Compatibility** - Fix Huawei MatePad SE 11 crash, add Android TV D-pad navigation with Leanback metadata, and wire user settings toggle for `HidPen` stylus (#54) (completed 2026-10-07)
- [x] **Phase 5: Protocol Extensions & Server Persistence** - Integrate Synergy 3.7+ signed 16-bit deltas and Mac Carbon keycodes, and persist saved server addresses across app restarts (#47, #53) (completed 2026-10-07)
- [ ] **Phase 6: Architectural Modularity** - Extract `ConnectionCoordinator` out of `ConnectionService` to isolate state transitions and input routing into pure, JVM-testable domain logic (#12)

## Phase Details

### Phase 1: Core Kernel UHID Fixes

**Goal**: Make `/dev/uhid` device management fully leak-free, eliminate 2-second attach delays on OEM devices, and align driver code with kernel message-queue semantics.
**Depends on**: Nothing
**Requirements**: UHID-01, UHID-02, UHID-03
**Success Criteria** (what must be TRUE):

  1. `UhidChannel.stopReader()` terminates the reader thread promptly without timing out or leaving parked threads after disconnect.
  2. `createDevice()` exits the readiness window as soon as `sawStart || sawOpen` is true without burning the 2-second timeout on OPEN-less OEM ROMs.
  3. `waitForStart()`, `drainPayload()`, and the unreachable deferred INPUT2 queue are completely removed from the codebase.

**Plans**: 1 plan
- 01-01: Core Kernel UHID Stability [complete]

### Phase 2: Deterministic Testing & CI Guardrails

**Goal**: Eliminate all flaky wall-clock timings from unit tests and establish non-hardware Android smoke test workflows.
**Depends on**: Phase 1
**Requirements**: TEST-01, TEST-02, TEST-03
**Success Criteria** (what must be TRUE):

  1. No `Thread.sleep` or wall-clock timing assertions exist in `app/src/test/java/`.
  2. `./gradlew testDebugUnitTest` runs 100% deterministically using virtual clocks (`UhidReadinessConfig`) and coroutine test scopes.
  3. Smoke testing workflow (`.github/workflows/android-smoke.yml`) and dependency fakes (`TestAppDependencies.kt`) verify `MainActivity` rendering in CI without hardware dependencies.

**Plans**: 1 plan
- 02-01: Deterministic Testing & CI Guardrails [complete]

### Phase 3: IPC Lifecycle & Review Follow-ups

**Goal**: Resolve code review findings regarding binder death token races, enter-warp calculation, and AIDL interface consistency.
**Depends on**: Phase 2
**Requirements**: IPC-01, IPC-02, IPC-03, ARCH-02
**Success Criteria** (what must be TRUE):

  1. Replaced client binder tokens do not trigger unconditional teardown of active virtual devices when stale death callbacks arrive.
  2. `HidMouseEnterWarp` accurately tracks applied snap steps on partial writes without double-applying warp deltas.
  3. AIDL methods in `IInputInjector.aidl` maintain stable transaction code numbering.
  4. Zero emojis exist in code, commit messages, or documentation.

**Plans**: 1 plan
- 03-01: IPC Lifecycle & Review Follow-ups [complete]

### Phase 4: Form-Factor & OEM Compatibility

**Goal**: Expand device compatibility to Android TV, Huawei tablets, and stylus/drawing pen inputs.
**Depends on**: Phase 3
**Requirements**: OEM-01, OEM-02, OEM-03
**Success Criteria** (what must be TRUE):

  1. App does not crash when connecting on Huawei MatePad SE 11 (defensive display insets and service binding).
  2. Android TV launcher banner and Leanback intent category are declared, and arrow keys route to Android DPAD keycodes.
  3. Users can select Pointer Device in Settings (`Mouse`, `Pen/Stylus`, `Touch`), routing absolute digitizer reports via `HidPen` when pen mode is active.

**Plans**: 1 plan
- 04-01: Form-Factor & OEM Compatibility [complete]

### Phase 5: Protocol Extensions & Server Persistence

**Goal**: Add Synergy 3.7+ desktop server support and persist user server lists across app restarts.
**Depends on**: Phase 4
**Requirements**: PROT-01, PROT-02, PROT-03, ARCH-02
**Success Criteria** (what must be TRUE):

  1. Client successfully completes handshakes with Synergy 3.7+ servers (`DSOP` completion) and decodes signed 16-bit relative deltas (`DMRM`).
  2. Mac Carbon keycodes are accurately mapped to Linux evdev keys for Android input.
  3. Manually added and favorited server addresses are persisted in DataStore and merged with mDNS scan results upon app launch.

**Plans**: 1 plan
- 05-01: Protocol Extensions & Server Persistence [complete]

### Phase 6: Architectural Modularity

**Goal**: Decompose `ConnectionService` by extracting pure domain coordination logic into `ConnectionCoordinator`.
**Depends on**: Phase 5
**Requirements**: ARCH-01, ARCH-02
**Success Criteria** (what must be TRUE):

  1. `ConnectionCoordinator` encapsulates connection generations, retry backoff, keepalive timeouts, and event routing decisions.
  2. All coordination and routing decisions are covered by fast JVM unit tests without Android Context or Mockito mocks.
  3. `ConnectionService` is reduced to an Android lifecycle shell managing foreground notifications and window overlay display.

**Plans**: 1 plan
- 06-01: Architectural Modularity & Pure Domain Coordination [pending]

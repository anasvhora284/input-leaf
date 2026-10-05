# Project Research Summary

**Project:** Input Leaf Android
**Domain:** Android Software KVM (Input Leap / Deskflow / Synergy Client, Linux UHID, Shizuku IPC)
**Researched:** 2026-10-05
**Confidence:** HIGH

## Executive Summary

Input Leaf is a specialized Android KVM client application bridging desktop operating systems (Linux, macOS, Windows) running Input Leap, Deskflow, or Synergy to Android devices over local Wi-Fi/LAN. To achieve sub-5ms input latency without requiring root, the app leverages Shizuku (or libsu for rooted devices) to bind to Linux's `/dev/uhid` character device, emulating native hardware USB input devices.

Research shows that while the application's core functionality is solid, several critical low-level kernel and lifecycle interactions require hardening. Specifically, open collaborator issues (#48, #49, #50, #51, #52) identified that `/dev/uhid` must be treated strictly as a message queue rather than a byte stream, reader thread stops must be poll-based to prevent thread leaks, and device readiness gates must accept `UHID_START` to avoid 2-second stalls on OEM devices. Furthermore, form-factor expansion (tablets with stylus/pen support, Android TV navigation) and protocol expansion (Synergy 3.x) represent high-value differentiators.

The recommended engineering approach is to resolve kernel stability and test determinism issues first, establish automated CI guardrails, integrate form-factor and protocol expansions, and decompose the central `ConnectionService` into a testable `ConnectionCoordinator`.

## Key Findings

### Recommended Stack

- **Core Kernel Driver:** `/dev/uhid` with bounded `Os.poll()` file descriptor timeouts and standard USB HID Report Descriptors (Mouse, Keyboard, Digitizer Pen).
- **Presentation:** Jetpack Compose Material 3 Expressive UI with dynamic color and settings toggles for pointer mode selection.
- **Testing:** Pure JVM unit tests using virtual clocks (`UhidReadinessConfig`), `LoopbackServer`, and coroutine test dispatchers (`runTest`) with zero real-time sleeps.

### Expected Features

**Must have (table stakes):**
- Stoppable UHID reader without parked thread leaks (#48)
- Zero-delay device attach exiting on `sawStart || sawOpen` (#49)
- Dead stream-model code removal (#50)
- Zero-flake deterministic JVM tests (#51)
- Huawei MatePad SE 11 crash fix (#54)
- Server persistence across restarts (#53)

**Should have (competitive differentiators):**
- Multi-Pointer mode toggle (Relative Mouse vs Absolute Pen/Tablet vs Touch)
- Synergy 3.x protocol compatibility (signed 16-bit deltas, Mac Carbon keycodes)
- Android TV Leanback manifest metadata and D-pad navigation mapping
- Decoupled `ConnectionCoordinator` architecture (#12)

**Defer (v2+):**
- Complex multi-touch gesture interpolation and trackpad pinch-to-zoom (#16 non-goals)

### Architecture Approach

- Extract state transitions, reconnect backoff, and event routing out of the 950+ line `ConnectionService` into a pure Kotlin `ConnectionCoordinator`.
- `ConnectionService` remains solely responsible for Android framework glue (foreground service notifications, overlay display, and Binder lifecycles).
- Stamp client binder tokens with a monotonic generation ID to prevent stale death notifications from tearing down newly active virtual devices (#52).

### Critical Pitfalls

1. **Uninterruptible Blocking File Reads:** Standard Java `FileInputStream.read()` on `/dev/uhid` cannot be interrupted by `Thread.interrupt()`. Must use `Os.poll()` with timeouts (#48).
2. **Waiting exclusively for `UHID_OPEN`:** Burns 2-second timeouts on OEM devices where userspace does not emit `OPEN`. Must exit on `START || OPEN` (#49).
3. **Byte-skipping on `/dev/uhid`:** Kernel message queue discards entire event on partial reads. Never use `waitForStart()` or `drainPayload()` (#50).
4. **Wall-clock sleeps in tests:** Introduces CI test flakiness. Must control time with virtual clocks (#51).

## Roadmap Implications

The research suggests a phased progression that eliminates existing debt before expanding features:

1. **Phase 1: Core UHID & Kernel Driver Fixes** — Resolve #48, #49, and #50.
2. **Phase 2: Deterministic Testing & CI Guardrails** — Resolve #51 and establish #13 smoke test and coverage workflows.
3. **Phase 3: Lifecycle, IPC & Review Follow-ups** — Address #52 (token generation, warp planning, documentation).
4. **Phase 4: Form-Factor & OEM Compatibility** — Fix #54 Huawei crash, add Android TV D-pad mapping, and wire `HidPen` pointer mode.
5. **Phase 5: Protocol Extensions & Synergy 3.x** — Complete #47/#53 for Synergy 3.7+ compatibility and saved server persistence.
6. **Phase 6: Architectural Modularity (`ConnectionCoordinator`)** — Implement #12 to decompose `ConnectionService`.

---
*Research summary: 2026-10-05*

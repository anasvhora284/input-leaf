# Input Leaf Android

## What This Is

Input Leaf is a free, open-source Android client for Input Leap, Deskflow, and Synergy desktop KVM switches. It enables users to share their PC's mouse, keyboard, and stylus/drawing tablet with Android phones, tablets, and TV devices over Wi-Fi/LAN without USB cables or mandatory root privileges.

## Core Value

Ultra-low latency, rock-solid, hardware-grade input injection and seamless KVM connectivity across diverse Android devices and form factors with pristine code quality and zero regressions.

## Requirements

### Validated

- ✓ TCP socket client implementing Barrier / Input Leap binary protocol (v1.6 / v1.7) — existing
- ✓ TLS encryption with Trust-On-First-Use (TOFU) certificate fingerprint pinning — existing
- ✓ Mutual TLS (mTLS) with client certificate authentication — existing
- ✓ mDNS server auto-discovery for local LAN — existing
- ✓ Shizuku rootless privileged input injection via AIDL IPC — existing
- ✓ libsu root input injection as UID 0 without Shizuku — existing
- ✓ Accessibility Service + InputLeafIME soft-keyboard fallback — existing
- ✓ Linux kernel `/dev/uhid` virtual USB mouse and keyboard drivers — existing
- ✓ Foreground service with connection persistence and exponential backoff retry — existing
- ✓ Material 3 Expressive UI with dynamic color theming — existing

### Active

- [ ] Core Kernel / UHID Stability Fixes:
  - [ ] Make UHID readiness reader stoppable without leaving parked threads (#48)
  - [ ] Exit readiness window on `sawStart || sawOpen` to eliminate attach latency regressions on OPEN-less devices (#49)
  - [ ] Purge dead stream-model UHID methods (`waitForStart`/`drainPayload`) and unreachable deferred queue (#50)
- [ ] Deterministic Testing & CI Guardrails:
  - [ ] Eliminate `Thread.sleep` and wall-clock assertions from JVM test suite (#51)
  - [ ] Add Android smoke testing workflow and test coverage guardrails (#13)
- [ ] Form-Factor & OEM Compatibility:
  - [ ] Diagnose and resolve Huawei MatePad SE 11 connection crash (#54)
  - [ ] Android TV support: D-pad navigation mapping and Leanback banner/manifest metadata
  - [ ] Stylus & Tablet support: User settings toggle for Pointer Device (Relative Mouse vs Absolute Pen/Tablet vs Touch Emulation) and complete `HidPen` absolute digitizer integration with tip/barrel buttons
- [ ] Protocol & Feature Enhancements:
  - [ ] Support experimental Synergy 3.x protocol negotiation and persistent saved server addresses (#47, #53)
  - [ ] Resolve low-severity review follow-ups (#52: IPC ordering, token generation check, warp planning)
- [ ] Architectural Modularity:
  - [ ] Extract `ConnectionCoordinator` from `ConnectionService` god object to decouple routing/state decisions from Android framework lifecycle (#12)

### Out of Scope

- Multitouch, pinch-to-zoom, or complex trackpad gestures — tracked in separate issue (#16 non-goals)
- Cloud relay or telemetry servers — Input Leaf is strictly 100% local LAN
- Heavy dependency injection frameworks (Hilt / Dagger) — maintain lightweight build times and minimal runtime overhead

## Context

Input Leaf is maintained by project owner @anasvhora284 with collaborator @guaje regularly providing detailed code reviews, Linux kernel insights, and architecture feedback. The application relies on kernel character device `/dev/uhid` for USB HID emulation, requiring strict compliance with kernel mechanics in `drivers/hid/uhid.c`. The codebase aims for 100% patch test coverage on core modules and strict adherence to `CODE_QUALITY_STANDARDS.md`.

## Constraints

- **Compatibility**: Android 8.0 (API 26) through Android 16 (API 37).
- **Kernel/Hardware**: User-space HID interaction must comply with Linux kernel UHID message-queue semantics.
- **Code Quality**: Strict zero emojis policy across all commits, outputs, and files; machine-enforceable lint rules.
- **Testing**: JVM tests must be fast, fully deterministic, and free of wall-clock timing or arbitrary sleeps.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Prioritize kernel UHID & test determinism first | Resolves open collaborator issues (#48, #49, #50, #51) before expanding features | — Pending |
| Exit UHID readiness on `START || OPEN` | Kernel guarantees `UHID_START` on probe; `UHID_OPEN` is userspace-dependent | — Pending |
| Add explicit Pointer Device toggle | Allows users to pick between relative mouse, absolute drawing pen, and touch fallback | — Pending |
| Extract `ConnectionCoordinator` | Decomposes 950+ line `ConnectionService` god object into pure testable state logic | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-10-05 after initialization*

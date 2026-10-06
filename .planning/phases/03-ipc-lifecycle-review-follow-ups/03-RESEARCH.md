# Phase 3: IPC Lifecycle & Review Follow-ups - Research

**Phase**: 03-ipc-lifecycle-review-follow-ups  
**Date**: 2026-10-06  
**Status**: Completed  
**Traceability**: Issue #52; Requirements IPC-01, IPC-02, IPC-03, ARCH-02

---

## 1. Executive Summary

Phase 3 addresses findings from collaborator review (#52) regarding IPC reliability, client lifecycle concurrency, edge-case math in mouse compensation, and AIDL transaction stability.

### Key Objectives
1. **IPC-01 (#52)**: Prevent stale binder death callbacks from tearing down newly attached clients by introducing monotonic generation stamping and recipient isolation in `InputInjectorService.attachClient`.
2. **IPC-02 (#52)**: Prevent double-applying warp deltas on partial HID writes in `HidMouseEnterWarp.applyIfPending`, correct warp logging, and align clock source with `SystemClock.uptimeMillis()`.
3. **IPC-03 (#52)**: Ensure AIDL method order consistency by strictly appending new methods at the end of `IInputInjector.aidl` so transaction code numbering remains stable across daemon/app updates.
4. **ARCH-02**: Enforce zero emojis across all code, commits, and documentation.
5. **Quality & Review Follow-ups (#52)**:
   - Handle `Double.NaN` gracefully in `MousePointerCompensation.gainForSpeedMmPerS`.
   - Prevent unbounded/overflowed wheel notches in `ShizukuInputInjector` and reset `wheelNotches` on `onHidMouseLeave()`.
   - Ensure volatile visibility for `leaveDebounceJob` and `hidMouseIdleJob` in `ConnectionService`.
   - Correct `UHID_OUTPUT` KDoc direction to "Host→device".
   - Fix MarkdownContent URL parser to reject trailing link titles/whitespace in `isDispatchableUrl`.
   - Clean up code formatting and imports.

---

## 2. Technical Analysis & Solutions

### 2.1 Monotonic Generation Stamping for Client Tokens (IPC-01)
- **Problem**: When a client replaces an existing client token via `attachClient(newToken)`, an in-flight death notification for the previous token can still be delivered by Binder thread pool. Because `clientDeathRecipient` previously tore down devices unconditionally, the stale notification killed devices that the new client just created.
- **Solution**:
  1. Add `private var clientGeneration = 0L` guarded by `clientLock`.
  2. Each call to `attachClient(token)` increments `clientGeneration` and creates a closure/recipient capturing the token and generation snapshot.
  3. Inside `binderDied()`, check `if (clientGeneration != generation || clientToken !== token) return`.
  4. Write a unit test `stale death notification from replaced client does not destroy newly attached devices`.

### 2.2 HidMouseEnterWarp Partial Write & Clock Alignment (IPC-02)
- **Problem**:
  1. Clock source defaulted to `System.currentTimeMillis()`, while all other motion compensation uses `SystemClock.uptimeMillis()`. Wall-clock NTP jumps skewed velocity calculations.
  2. In `applyIfPending`, if `mouse.move` failed midway, the full `plans` list was returned while `pending` was left set, causing the client to replay the full snap and double-apply the prefix that already reached the device.
- **Solution**:
  1. Change default clock in `HidMouseEnterWarp` to `{ android.os.SystemClock.uptimeMillis() }`.
  2. Accumulate `applied` plans in `applyIfPending`. On write failure, return only `applied` and clear or advance pending state so prefixes are never repeated.
  3. Ensure `InputInjectorService` logs actual applied count.

### 2.3 AIDL Stability (IPC-03)
- **Problem**: AIDL assigns numeric `TRANSACTION_*` codes sequentially. Inserting methods in the middle shifts subsequent transaction codes and breaks communication between an existing background injector daemon and an updated client app.
- **Solution**:
  1. Verify and document that all methods must strictly append at the end of `IInputInjector.aidl`.
  2. Note in comments that `destroy()` was preserved and any future methods (e.g. pen injection) must appear at the end.

### 2.4 Math, Timing, and Parsing Follow-ups
- **Mouse Pointer NaN Handling**:
  `MousePointerCompensation.gainForSpeedMmPerS`: Guard against `speedMmPerS.isNaN()` or `speedMmPerS <= 0.0` by returning `firstSegmentGain(settingsSpeed)`. Use `firstOrNull ?: CURVE_SEGMENTS.last()`.
- **Wheel Notch Clamping & Reset**:
  Clamp wheel notch writes to `[-127, 127]` in `HidMouse.move`, and call `wheelNotches.reset()` in `onHidMouseLeave()`.
- **MarkdownContent URL Validation**:
  In `isDispatchableUrl`, reject URLs containing whitespace. Strip trailing whitespace or titles in `[text](url)` markdown links.

---

## 3. Verification Strategy

1. **Unit Testing**:
   - `InputInjectorServiceClientDeathTest`: Test that a stale death notification from a replaced token does not tear down virtual devices.
   - `HidMouseEnterWarpTest`: Test partial write failure returning only applied plans without double-applying.
   - `MousePointerCompensationTest`: Test `gainForSpeedMmPerS(Double.NaN, speed)` returns valid base gain without throwing.
   - `MarkdownContentTest`: Test `[text](http://example.com "title")` parses dispatchable URI without trailing title.
2. **Regression Check**:
   - Run `./gradlew :app:testDebugUnitTest :koverVerifyDebugJvm`.
   - Ensure zero emojis in all files and commits.

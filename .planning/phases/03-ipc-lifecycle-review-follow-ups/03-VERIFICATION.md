---
phase: "03"
slug: "ipc-lifecycle-review-follow-ups"
status: verified
verified_commit: "9bd612a23ab7f9b09f485dbda421110a17cae497"
verified_at: "2026-10-06T18:10:00.000Z"
requirements:
  - IPC-01
  - IPC-02
  - IPC-03
  - ARCH-02
---

# Phase 03 Verification Report: IPC Lifecycle & Review Follow-ups

## 1. Summary of Work

Phase 03 resolved all IPC lifecycle race conditions, timing and math edge cases, and hygiene findings raised in Issue #52:
1. **Client Generation Stamping (IPC-01)**: `InputInjectorService.attachClient` assigns monotonic generation tokens (`clientGeneration`). Death notifications from superseded client Binder tokens are dropped, protecting active virtual devices from being torn down when a client re-attaches quickly.
2. **HidMouseEnterWarp Partial Write & Clock Alignment (IPC-02)**:
   - Clock source aligned to `android.os.SystemClock.uptimeMillis()`.
   - `applyIfPending` tracks actually transmitted plans and clears pending state upon write failure, preventing duplicate snap delta application.
   - Guarded `MousePointerCompensation.gainForSpeedMmPerS` against `Double.NaN` and non-positive speeds.
   - Clamped wheel reports to `[-127, 127]` in `HidMouse.sendReport` and reset `wheelNotches` on `onHidMouseLeave()`.
3. **AIDL Ordering, Concurrency, and Markdown Sanitation (IPC-03, ARCH-02)**:
   - Documented AIDL transaction code ordering contract in `IInputInjector.aidl`.
   - Marked `leaveDebounceJob` and `hidMouseIdleJob` as `@Volatile` in `ConnectionService`.
   - Corrected `UHID_OUTPUT` KDoc to "Host→device report".
   - Sanitized markdown link parsing to reject whitespace in `isDispatchableUrl` and strip link titles in `parseInline`.
   - Maintained zero emojis across all code, tests, documentation, and commits.

## 2. Automated Test Verification Results

| Test Class / Suite | Command | Result |
|--------------------|---------|--------|
| `InputInjectorServiceClientDeathTest` | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.InputInjectorServiceClientDeathTest"` | Passed (6/6 tests) |
| UHID Module Unit Tests | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.uhid.*"` | Passed (106/106 tests) |
| Markdown Parser Tests | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.ui.MarkdownParserTest"` | Passed (19/19 tests) |
| Full JVM Test Suite | `./gradlew --rerun-tasks :app:testDebugUnitTest` | Passed (490/490 tests) |
| Kover Coverage Verification | `./gradlew :koverVerifyDebugJvm` | Passed |

## 3. Requirements Traceability Matrix

| Requirement | Description | Verified By | Status |
|-------------|-------------|-------------|--------|
| **IPC-01** | `InputInjectorService.attachClient` assigns monotonic generation tokens so stale death notifications from replaced clients do not terminate active virtual devices (#52). | `InputInjectorServiceClientDeathTest.stale death notification from replaced client does not destroy newly attached devices` | Complete |
| **IPC-02** | Fix `HidMouseEnterWarp` planning, partial write handling, and clock alignment (`SystemClock.uptimeMillis()`) without double-applying snaps (#52). | `HidMouseEnterWarpTest`, `MousePointerCompensationTest`, `HidMouseTest` | Complete |
| **IPC-03** | Preserve stable AIDL transaction code order in `IInputInjector.aidl` by strictly appending new methods at the end (#52). | Contract documentation in `IInputInjector.aidl` | Complete |
| **ARCH-02** | Strictly enforce ZERO emojis across code, commits, and docs (`CODE_QUALITY_STANDARDS.md`). | Code review of all phase changes | Complete |

## 4. Phase Verification Digest

```
digest: v1:sha256:d82e185c85b1bf7b1b51e9df646927dc918d381ea6d9337ffea0335e40da1150
phase: 03-ipc-lifecycle-review-follow-ups
commit: 9bd612a23ab7f9b09f485dbda421110a17cae497
timestamp: 2026-10-06T18:10:00.000Z
status: passed
```

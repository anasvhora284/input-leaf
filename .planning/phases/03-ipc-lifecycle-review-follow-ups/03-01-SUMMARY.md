# Plan 03-01 Summary: IPC Lifecycle & Review Follow-ups

## Overview
- **Phase**: 03 (IPC Lifecycle & Review Follow-ups)
- **Plan**: 01
- **Status**: Complete
- **Head Commit**: `9bd612a`

## Objectives Achieved
1. **IPC-01**: Hardened Binder lifecycle in `InputInjectorService.attachClient` with monotonic client generation stamping (`clientGeneration = 0L`). Outdated death notifications from replaced client tokens are recognized and safely dropped, preventing teardown of active virtual devices created by reconnected clients.
2. **IPC-02**: Resolved HID motion and enter warp issues:
   - Aligned default clock in `HidMouseEnterWarp` to `android.os.SystemClock.uptimeMillis()`.
   - Updated `applyIfPending` to return only plans successfully sent via UHID. On write failure, `pending` state is cleared to prevent double-applying prefix plans.
   - Handled `Double.NaN` and non-positive speeds gracefully in `MousePointerCompensation.gainForSpeedMmPerS` to eliminate `NoSuchElementException`.
   - Clamped wheel reports to `[-127, 127]` in `HidMouse.sendReport` and ensured `wheelNotches.reset()` is invoked upon `onHidMouseLeave()`.
3. **IPC-03 & ARCH-02**: Enforced AIDL stability and codebase hygiene:
   - Documented contract in `IInputInjector.aidl` requiring all new methods to strictly append at the end of the interface to maintain stable transaction code numbering.
   - Sanitized markdown URLs in `MarkdownContent.isDispatchableUrl` by rejecting whitespace and stripping trailing title attributes in `[label](url "title")`.
   - Marked `leaveDebounceJob` and `hidMouseIdleJob` as `@Volatile` in `ConnectionService` for multi-threaded visibility.
   - Corrected KDoc for `UHID_OUTPUT` to "Host→device report".
   - Verified that zero emojis are used in code, documentation, and commit messages.

## Verification
- Unit test suite: 490 tests passing without failures (`./gradlew --rerun-tasks :app:testDebugUnitTest`).
- Kover coverage verification: Passed baseline threshold (`:koverVerifyDebugJvm`).
- Specific regression tests:
  - `InputInjectorServiceClientDeathTest.stale death notification from replaced client does not destroy newly attached devices`: Passed.
  - `HidMouseEnterWarpTest.failed HID write returns only applied plans and clears pending to prevent double snap`: Passed.
  - `HidMouseEnterWarpTest.partial HID write returns only applied plans and clears pending`: Passed.
  - `MousePointerCompensationTest.gainForSpeedMmPerS with NaN or negative speed falls back to first segment gain`: Passed.
  - `HidMouseTest.move clamps wheel between minus 127 and 127`: Passed.
  - `MarkdownParserTest.parseInline_markdownLinkWithTitleStripsTitleAndKeepsUrlLinkified`: Passed.

## Commits
1. `7ff6dfb`: `fix(ipc): stamp monotonic generation tokens on client attach to drop stale death notifications`
2. `f79653e`: `fix(warp): return applied plans on partial write, align clock source, and clamp wheel notches`
3. `9bd612a`: `fix(hygiene): enforce aidl transaction stability, sanitize markdown urls, and update kdocs`

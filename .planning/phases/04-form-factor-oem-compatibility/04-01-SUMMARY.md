# Plan 04-01 Summary: Form-Factor & OEM Compatibility

## Overview
- **Phase**: 04 (Form-Factor & OEM Compatibility)
- **Plan**: 01
- **Status**: Complete
- **Head Commit**: `54902af`

## Objectives Achieved
1. **OEM-01**: Hardened `ConnectionService` and `CursorOverlayService` against Huawei MatePad SE 11 crashes (GitHub Issue #54):
   - Created `ScreenBoundsResolver` implementing a robust 3-tier cascade:
     * Tier 1: `WindowManager.currentWindowMetrics.bounds` (API 30+) in `runCatching`
     * Tier 2: `DisplayManager.getDisplay(DEFAULT_DISPLAY).getRealSize(...)`
     * Tier 2b: `WindowManager.defaultDisplay.getSize(...)` legacy fallback
     * Tier 3: `resources.displayMetrics` coerced to at least `1x1` dimensions
   - Wrapped `ServiceCompat.startForeground` with `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` in `runCatching` to handle background execution restrictions safely.
   - Wrapped `ServiceCompat.stopForeground` in `runCatching`.
   - Hardened `CursorOverlayService` by removing the overlay view from `WindowManager` on `onDestroy()` inside a guarded block and catching layout update errors.
   - Added unit test `ConnectionServiceBoundsTest` verifying cascade resolution and Application context safety.
2. **OEM-02**: Expanded Android TV support:
   - Added `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>` to `AndroidManifest.xml`.
   - Added `<uses-feature android:name="android.software.leanback" android:required="false"/>`.
   - Added `android:banner="@drawable/tv_banner"` to `<application>`.
   - Added `<category android:name="android.intent.category.LEANBACK_LAUNCHER"/>` to `MainActivity`'s intent filter.
   - Created 16:9 vector drawable banner `app/src/main/res/drawable/tv_banner.xml` featuring brand palette and stylized leaf pointer artwork.
   - Verified D-pad arrow key mappings (scancodes 103, 108, 105, 106) in `KeyMapUtilsTest`.
3. **OEM-03**: Completed user pointer selection and absolute digitizer stylus support:
   - Created `PointerDevice` enum (`MOUSE`, `STYLUS`) with DataStore persistence in `AppPreferences`.
   - Added "Pointer device" selection row in `SettingsScreen` and `LeafNavigation`.
   - Wired `prefs.pointerDevice` collection in `ConnectionService` to dynamically notify the active injector via `injector?.setAbsolutePointer(absolutePointer)`.
   - Implemented `HidPen` with 5-byte report format (tip, barrel, in-range, and 16-bit X/Y scaled against the display's maximum extent `maxOf(maxX, maxY)` to preserve 1:1 mapping on widescreen tablets).
   - Wired `openVirtualPen()`, `closeVirtualPen()`, and `injectHidPen()` across `IInputInjector`, `InputInjectorService`, and `ShizukuInputInjector`.
   - Added `PointerDeviceTest` and `HidPenTest` covering descriptors, buttons, and coordinate transformations across landscape and portrait displays.
4. **ARCH-02**: Strictly preserved ZERO emojis across all source files, resource drawables, test suites, documentation, and git commits.

## Verification
- Unit test suite: 100% passing without failures (`./gradlew :app:testDebugUnitTest`).
- Kover coverage verification: Passed baseline threshold (`:koverVerifyDebugJvm`).
- Tests executed and passing:
  - `ConnectionServiceBoundsTest`: 6/6 tests passing.
  - `KeyMapUtilsTest`: 6/6 tests passing (including D-pad arrow mappings).
  - `PointerDeviceTest`: 2/2 tests passing.
  - `HidPenTest`: 9/9 tests passing.

## Commits
1. `5c264f1`: `fix(oem): guard window metrics and foreground service starts against huawei crashes`
2. `1d732e4`: `feat(tv): add leanback launcher banner metadata and non-touchscreen support`
3. `54902af`: `feat(stylus): implement user pointer toggle and hid pen absolute digitizer injection`

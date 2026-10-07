# Phase 04 Research: Form-Factor & OEM Compatibility

## 1. Objectives & Scope
Expand Input Leaf Android's hardware and OS compatibility across three targeted dimensions:
1. **OEM-01 (#54)**: Resolve connection crashes on Huawei tablets (MatePad SE 11 / HarmonyOS / EMUI) by defensively handling `WindowManager` access from background Service contexts, display insets/metrics fallbacks, and Android 14+ foreground service start constraints.
2. **OEM-02**: Support Android TV and large-screen form factors:
   - Declare touchscreen non-mandatory (`android.hardware.touchscreen` required="false").
   - Declare Leanback software feature (`android.software.leanback` required="false").
   - Add `LEANBACK_LAUNCHER` intent filter to `MainActivity` with TV banner asset.
   - Verify that desktop arrow keys translate to Android D-pad keycodes (`KEYCODE_DPAD_*`).
3. **OEM-03**: Support absolute digitizer stylus input:
   - Provide user settings toggle in `SettingsScreen` for Pointer Device (`Mouse` vs `Stylus`).
   - Persist preference in `AppPreferences`.
   - Wire `setAbsolutePointer(enabled)` through `InputInjector`, `ShizukuInputInjector`, and `ConnectionService`.
   - Implement `openVirtualPen()`, `closeVirtualPen()`, and `injectHidPen()` in `IInputInjector.aidl` and `InputInjectorService`.
   - Scale coordinates to display aspect ratio using longest-edge normalization in `HidPen`.
   - Provide unit test coverage in `HidPenTest` and `PointerDeviceTest`.

---

## 2. Technical Analysis

### 2.1 Huawei MatePad SE 11 Connection Crash (OEM-01)
- **Root Cause Analysis**:
  1. **Non-Visual Context WindowManager Access**: In `ConnectionService.getScreenBounds()`, calling `wm.currentWindowMetrics.bounds` from a `Service` context throws an `IllegalArgumentException` on Android 11+ ("Tried to access visual service WindowManager from a non-visual Context") or returns unexpected null/empty bounds in EMUI/HarmonyOS multi-window / desktop mode.
  2. **Display Fallback Fragility**: On Android R+, `wm.defaultDisplay` is deprecated and throws or returns null on background services when not associated with a window token.
  3. **Foreground Service Start Restrictions**: In `ConnectionService.performConnect()`, calling `startForeground` without `ServiceCompat` or without specifying `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` can trigger `ForegroundServiceStartNotAllowedException` on Android 14+ when running under Huawei's aggressive background power management.
  4. **Cursor Overlay BadTokenException**: In `CursorOverlayService`, `windowManager.addView` can throw `BadTokenException` if overlay permissions are revoked or if EMUI restricts window additions.
- **Solution Strategy**:
  1. Refactor `getScreenBounds()` into a resilient three-tier fallback:
     - Tier 1: `runCatching { wm?.currentWindowMetrics?.bounds }` (valid if WindowManager supports it).
     - Tier 2: `DisplayManager.getDisplay(Display.DEFAULT_DISPLAY)` with `display.getRealSize()`.
     - Tier 3: `resources.displayMetrics` (`widthPixels`, `heightPixels`).
     - Always enforce positive bounds: `width.coerceAtLeast(1)`, `height.coerceAtLeast(1)`.
  2. Wrap `startForeground()` in `runCatching` with proper `ServiceCompat.startForeground` API 34 type declaration so failures log warnings without crashing the app.
  3. Ensure `CursorOverlayService.ensureCursorView` safely catches all `WindowManager` exceptions.

### 2.2 Android TV & Chromium Support (OEM-02)
- **Manifest Changes**:
  ```xml
  <uses-feature android:name="android.hardware.touchscreen" android:required="false"/>
  <uses-feature android:name="android.software.leanback" android:required="false"/>
  ```
- **Leanback Launcher**:
  Add `android.intent.category.LEANBACK_LAUNCHER` to `MainActivity`.
- **TV Banner**:
  Add `android:banner="@drawable/tv_banner"` pointing to a dedicated 16:9 vector/drawable.
- **D-Pad Mapping**:
  `KeyMapUtils` already maps keysyms `0xFF51..0xFF54` to `KEYCODE_DPAD_LEFT`, `KEYCODE_DPAD_UP`, `KEYCODE_DPAD_RIGHT`, `KEYCODE_DPAD_DOWN` and evdev scancodes 103, 105, 106, 108.
  Verify that `MainActivity` and compose settings remain navigable via directional keys.

### 2.3 Stylus / Pen Input Device (OEM-03)
- **HID Report Descriptor**:
  Generic Desktop / Pointer with Digitizer Stylus usage:
  - 1 byte buttons: Tip (bit 0), Barrel (bit 1), In-Range (bit 2).
  - 2 bytes X: 16-bit absolute (0..32767).
  - 2 bytes Y: 16-bit absolute (0..32767).
- **Coordinate Scaling**:
  Android 15+ maps both axes to the longer display dimension (`maxOf(maxX, maxY)`). Scaling by width alone displaces the pointer horizontally on landscape tablets. Normalizing against the maximum display extent ensures 1:1 screen mapping.
- **Event Translation**:
  - `MouseMoveAbs` / `MouseMoveRel`: updates target and sends in-range pen report.
  - `MouseDown` / `MouseUp`: Left button maps to `tip`, right button maps to `barrel`.
  - `MouseWheel`: routed via `injectScrollEvent` since digitizers do not have physical scroll wheels.
  - `onHidMouseLeave`: clears `tip`, `barrel`, and sends `inRange = false`.

---

## 3. Architecture & Traceability
- **OEM-01**: `ConnectionService.kt`, `CursorOverlayService.kt`.
- **OEM-02**: `AndroidManifest.xml`, `tv_banner.xml`.
- **OEM-03**: `PointerDevice.kt`, `HidPen.kt`, `AppPreferences.kt`, `SettingsScreen.kt`, `ConnectionService.kt`, `ShizukuInputInjector.kt`, `InputInjectorService.kt`, `IInputInjector.aidl`, `HidPenTest.kt`, `PointerDeviceTest.kt`.
- **Zero Emojis**: Stored under ARCH-02 guidelines.

---
phase: "04"
slug: "form-factor-oem-compatibility"
status: draft
nyquist_compliant: true
wave_0_complete: false
created: "2026-10-07"
---

# Phase 04 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Robolectric + Google Truth |
| **Config file** | `app/build.gradle.kts` |
| **Quick run command** | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.inject.PointerDeviceTest" --tests "com.inputleaf.android.shizuku.uhid.HidPenTest"` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest :koverVerifyDebugJvm` |
| **Estimated runtime** | ~20 seconds |

---

## Sampling Rate

- **After Task 1 (OEM-01):** Run `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.service.*"`.
- **After Task 2 (OEM-02):** Run `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.inject.KeyMapUtilsTest"`.
- **After Task 3 (OEM-03):** Run `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.inject.PointerDeviceTest" --tests "com.inputleaf.android.shizuku.uhid.HidPenTest"` and full suite `:koverVerifyDebugJvm`.
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 04-01-01 | 01 | 1 | OEM-01 | — | Resilient screen metrics & foreground service error handling prevent crashes on Huawei tablets | unit | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.service.*"` | yes | pending |
| 04-01-02 | 01 | 1 | OEM-02 | — | Android TV manifest declarations and DPAD mapping support remote control navigation | unit/manifest | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.inject.KeyMapUtilsTest"` | yes | pending |
| 04-01-03 | 01 | 1 | OEM-03, ARCH-02 | — | Pointer device settings toggle and HidPen absolute digitizer report injection | unit | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.inject.PointerDeviceTest" --tests "com.inputleaf.android.shizuku.uhid.HidPenTest" :koverVerifyDebugJvm` | yes | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

Existing test infrastructure covers all phase requirements. New unit tests will be added:
- `HidPenTest.kt`: Validates digitizer descriptor, scaling calculations, and bitmask packing.
- `PointerDeviceTest.kt`: Validates settings serialization and fallback defaults.

---

## Manual-Only Verifications

- Manual testing on Android TV emulator or device verifying Leanback launcher icon display.
- Manual testing on Huawei tablets verifying background connection resilience.

---
*Validation strategy created: 2026-10-07*

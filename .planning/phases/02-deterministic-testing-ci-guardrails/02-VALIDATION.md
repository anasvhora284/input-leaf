---
phase: "02"
slug: "deterministic-testing-ci-guardrails"
status: draft
nyquist_compliant: true
wave_0_complete: false
created: "2026-10-06"
---

# Phase 02 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Robolectric + Google Truth + Kotlinx Coroutines Test |
| **Config file** | `app/build.gradle.kts`, `build.gradle.kts`, `codecov.yml` |
| **Quick run command** | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.InputInjectorServiceEnterTest" --tests "com.inputleaf.android.service.HidAttachmentControllerTest"` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest :koverVerifyDebugJvm` |
| **Smoke assemble command** | `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` |
| **Estimated runtime** | ~20 seconds |

---

## Sampling Rate

- **After Task 1 (TEST-01):** Run `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.*" --tests "com.inputleaf.android.service.HidAttachmentControllerTest"` and check zero `Thread.sleep` in `app/src/test/java/`.
- **After Task 2 (TEST-02):** Run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` to ensure `AppDependencies`, `MainViewModelFactory`, and `TestAppDependencies` compile cleanly and smoke tests assemble without real hardware.
- **After Task 3 (TEST-03):** Run `./gradlew :koverVerifyDebugJvm` and verify `codecov.yml` syntax.
- **Before `/gsd-verify-work`:** Full suite must be green (`./gradlew :app:testDebugUnitTest :koverVerifyDebugJvm`).
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | TEST-01 | — | Deterministic virtual clock in InputInjectorServiceEnterTest and coroutine synchronization in HidAttachmentControllerTest | unit | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.InputInjectorServiceEnterTest" --tests "com.inputleaf.android.service.HidAttachmentControllerTest"` | ✅ | ⬜ pending |
| 02-01-02 | 01 | 1 | TEST-02 | — | AppDependencies and TestAppDependencies isolate MainActivity from hardware/network; Android smoke workflow defined | unit/smoke | `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` | ⬜ | ⬜ pending |
| 02-01-03 | 01 | 1 | TEST-03 | — | Kover verification rules enforce coverage on UHID/protocol; Codecov unignores UHID | coverage | `./gradlew :koverVerifyDebugJvm` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements.

---

## Manual-Only Verifications

None required; all Phase 2 changes are verified by deterministic JVM tests, Gradle compilation, and Kover verification tasks.

---
*Validation strategy created: 2026-10-06*

---
phase: "03"
slug: "ipc-lifecycle-review-follow-ups"
status: verified
nyquist_compliant: true
wave_0_complete: true
created: "2026-10-06"
---

# Phase 03 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Robolectric + Google Truth |
| **Config file** | `app/build.gradle.kts` |
| **Quick run command** | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.InputInjectorServiceClientDeathTest" --tests "com.inputleaf.android.shizuku.uhid.*"` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest :koverVerifyDebugJvm` |
| **Estimated runtime** | ~20 seconds |

---

## Sampling Rate

- **After Task 1 (IPC-01):** Run `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.InputInjectorServiceClientDeathTest"`.
- **After Task 2 (IPC-02 & Timing/Math):** Run `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.uhid.*"`.
- **After Task 3 (IPC-03, Markdown & Concurrency):** Run full suite `./gradlew :app:testDebugUnitTest :koverVerifyDebugJvm`.
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 1 | IPC-01 | — | Monotonic generation tokens prevent stale client death from killing active devices | unit | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.InputInjectorServiceClientDeathTest"` | yes | green |
| 03-01-02 | 01 | 1 | IPC-02 | — | Partial warp writes return applied steps without re-sending snap prefix; clock aligned | unit | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.uhid.HidMouseEnterWarpTest" --tests "com.inputleaf.android.shizuku.uhid.MousePointerCompensationTest"` | yes | green |
| 03-01-03 | 01 | 1 | IPC-03, ARCH-02 | — | AIDL transaction ordering locked; markdown link sanitation; zero emojis | unit/lint | `./gradlew :app:testDebugUnitTest :koverVerifyDebugJvm` | yes | green |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements.

---

## Manual-Only Verifications

None required; all IPC lifecycle, math, and parsing logic is covered by deterministic JVM unit tests.

---
*Validation strategy created: 2026-10-06*

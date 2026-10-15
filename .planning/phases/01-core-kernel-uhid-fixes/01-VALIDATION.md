---
phase: "01"
slug: "core-kernel-uhid-fixes"
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-06"
---

# Phase 01 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Robolectric + Google Truth |
| **Config file** | `app/build.gradle.kts` |
| **Quick run command** | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.uhid.*"` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.uhid.*"`
- **After every plan wave:** Run `./gradlew :app:testDebugUnitTest`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | UHID-01 | — | Clean reader thread termination without thread leaks | unit | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.uhid.UhidChannelReadinessTest"` | ✅ | ✅ green |
| 01-01-02 | 01 | 1 | UHID-02 | — | Readiness gate completes on either START or OPEN without stalling | unit | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.uhid.UhidChannelReadinessTest"` | ✅ | ✅ green |
| 01-01-03 | 01 | 1 | UHID-03 | — | Dead stream-model and deferred queue methods purged cleanly | unit | `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.uhid.UhidProtocolTest"` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements.

---

## Manual-Only Verifications

None required; all Phase 1 kernel driver changes are covered by deterministic JVM tests with virtual clock injection.

---
*Validation strategy created: 2026-10-06*

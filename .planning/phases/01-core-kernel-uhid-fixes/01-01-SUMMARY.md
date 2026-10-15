---
phase: "01-core-kernel-uhid-fixes"
plan: "01"
subsystem: uhid
tags: [uhid, linux-kernel, android-posix, character-device, input-injection]

requires: []
provides:
  - Stoppable UHID reader loop using bounded Os.poll on Android /dev/uhid and timed available polling
  - Dual START / OPEN readiness gate preventing 2000ms attach stalls on OPEN-less OEM ROMs
  - Stream-model method purge (waitForStart, drainPayload) and removed dead deferredReports queue
affects: [02-deterministic-testing, 03-ipc-lifecycle, 04-form-factor]

actuals:
  tokens: 1800
  tasks: 3
  commits: 1

tech-stack:
  added: [android.system.Os, android.system.StructPollfd]
  patterns: [non-blocking bounded character device polling, dual-signal kernel driver probe readiness]

key-files:
  created: []
  modified:
    - app/src/main/java/com/inputleaf/android/shizuku/uhid/UhidChannel.kt
    - app/src/main/java/com/inputleaf/android/shizuku/uhid/UhidProtocol.kt
    - app/src/test/java/com/inputleaf/android/shizuku/uhid/UhidChannelReadinessTest.kt
    - app/src/test/java/com/inputleaf/android/shizuku/uhid/UhidProtocolTest.kt

key-decisions:
  - "Used Os.poll with StructPollfd and 50ms bounded timeout on Android /dev/uhid file descriptors so reader thread joins cleanly without blocking in native kernel read."
  - "Gated readiness on sawStart || sawOpen instead of sawOpen alone to prevent 2-second device attachment latency penalties on OEM ROMs like ColorOS."
  - "Purged stream-model methods (waitForStart, drainPayload) and dead deferredReports queue in compliance with collaborator review issues #48, #49, #50."

patterns-established:
  - "Bounded POSIX polling for Linux character device event readers."
  - "Kernel driver probe readiness detection allowing immediate device operation upon hid_hw_start."

requirements-completed: [UHID-01, UHID-02, UHID-03]

coverage:
  - id: D1
    description: "Stoppable UHID reader thread that terminates cleanly within READER_JOIN_MS without thread leaks"
    requirement: "UHID-01"
    verification:
      - kind: unit
        ref: "com.inputleaf.android.shizuku.uhid.UhidChannelReadinessTest#stopReader terminates reader thread promptly without leaking thread"
        status: pass
    human_judgment: false
  - id: D2
    description: "Readiness gate exiting upon sawStart || sawOpen without 2-second timeout penalty"
    requirement: "UHID-02"
    verification:
      - kind: unit
        ref: "com.inputleaf.android.shizuku.uhid.UhidChannelReadinessTest#START completes readiness without burning the whole timeout"
        status: pass
    human_judgment: false
  - id: D3
    description: "Purged stream-model methods and unreachable deferredReports queue"
    requirement: "UHID-03"
    verification:
      - kind: unit
        ref: "com.inputleaf.android.shizuku.uhid.UhidProtocolTest"
        status: pass
    human_judgment: false

duration: 15min
completed: 2026-10-06
status: complete
---

# Phase 01: Plan 01 Summary

**Kernel UHID character device reader stabilized with bounded polling, dual START/OPEN readiness gate, and stream-model purge.**

## Performance

- **Duration:** 15 min
- **Tasks:** 3
- **Files modified:** 4
- **Verification:** 100% green on `./gradlew :app:testDebugUnitTest`

## Accomplishments

- **UHID-01 (#48):** Refactored `UhidChannel.startReader()` and `stopReader()` to avoid blocking indefinitely in `input.read()`. Utilized `Os.poll` with 50ms bounded timeout on Android device nodes and cooperative polling on JVM test streams. `reader.join(READER_JOIN_MS)` now reliably joins within 100ms.
- **UHID-02 (#49):** Refactored `UhidChannel.awaitReady()` to exit readiness as soon as `sawStart || sawOpen` is observed. OEM ROMs that omit userspace `UHID_OPEN` now attach immediately without incurring a 2000ms stall.
- **UHID-03 (#50):** Purged dead stream-model methods (`waitForStart()` and `drainPayload()`) from `UhidProtocol.kt` and purged the unreachable `deferredReports` queue from `UhidChannel.kt`.

## Task Commits

1. **Tasks 01-01-01, 01-01-02, 01-01-03** - `bed65ff` (fix(uhid): resolve kernel reader thread leak, dual readiness gate, and stream-model purge)

## Verification Results

- `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.uhid.*"`: Passed (28 tasks, 0 failures)
- `./gradlew :app:testDebugUnitTest`: Passed (Full suite green, 0 regressions)

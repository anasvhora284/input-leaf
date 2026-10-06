---
phase: "01-core-kernel-uhid-fixes"
verified: "2026-10-06T15:15:00Z"
status: passed
score: 4/4 must-haves verified
covered_files:
  - .planning/phases/01-core-kernel-uhid-fixes/01-01-SUMMARY.md
  - app/src/main/java/com/inputleaf/android/shizuku/uhid/UhidChannel.kt
  - app/src/main/java/com/inputleaf/android/shizuku/uhid/UhidProtocol.kt
  - app/src/test/java/com/inputleaf/android/shizuku/uhid/UhidChannelReadinessTest.kt
  - app/src/test/java/com/inputleaf/android/shizuku/uhid/UhidProtocolTest.kt
covered_digest: "v1:sha256:492098ca0e9488b8460d077366b1812a049f6b9815a968576f617ec077dfadb0"
behavior_unverified: 0
---

# Phase 01: Core Kernel UHID Fixes Verification Report

**Phase Goal:** Fix Linux kernel `/dev/uhid` integration bugs reported by collaborator @guaje: make readiness reader stoppable without leaking zombie threads (#48), exit readiness upon `sawStart || sawOpen` to avoid 2-second stalls on OPEN-less devices (#49), and purge dead stream-model methods and unreachable deferred reports queue (#50).
**Verified:** 2026-10-06T15:15:00Z
**Status:** passed

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `UhidChannel.stopReader()` terminates the reader thread cleanly within `READER_JOIN_MS` without hanging or leaking threads | ✓ VERIFIED | Verified via `Os.poll` (50ms timeout) and unit test `stopReader terminates reader thread promptly without leaking thread` |
| 2 | `UhidChannel.awaitReady()` exits immediately when either `sawStart` or `sawOpen` is true without stalling for `openTimeoutMs` | ✓ VERIFIED | Verified via unit test `START completes readiness without burning the whole timeout` (<300ms elapsed) |
| 3 | No stream-model methods (`waitForStart`, `drainPayload`) exist in `UhidProtocol` | ✓ VERIFIED | Removed methods; `UhidProtocolTest` compiles and passes without stream methods |
| 4 | No unbounded `deferredReports` queue exists in `UhidChannel` | ✓ VERIFIED | Deleted `deferredReports` and `allowInputLocked`; direct write on `sendReport` |

**Score:** 4/4 truths verified (0 present, behavior-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/com/inputleaf/android/shizuku/uhid/UhidChannel.kt` | Refactored non-blocking poll reader and dual readiness gate | ✓ EXISTS + SUBSTANTIVE | Contains `Os.poll` timeout loop, `sawStart || sawOpen` check, and direct report dispatch |
| `app/src/main/java/com/inputleaf/android/shizuku/uhid/UhidProtocol.kt` | Clean wire packet definitions without stream-model helpers | ✓ EXISTS + SUBSTANTIVE | Dead stream methods purged; clean constants and packet layout |
| `app/src/test/java/com/inputleaf/android/shizuku/uhid/UhidChannelReadinessTest.kt` | Unit tests for START readiness, absence of stall, and reader termination | ✓ EXISTS + SUBSTANTIVE | 4 unit tests covering OPEN, START, neither, and reader thread join |
| `app/src/test/java/com/inputleaf/android/shizuku/uhid/UhidProtocolTest.kt` | Unit tests for kernel ABI packet layouts and event types | ✓ EXISTS + SUBSTANTIVE | Verified 10 tests checking struct sizes, offsets, and event numbers |

**Artifacts:** 4/4 verified

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `UhidChannel.startReader` | `/dev/uhid` | `Os.poll` + `input.read` | ✓ WIRED | Polls fd with 50ms timeout, safely catching `ErrnoException` |
| `UhidChannel.createDevice` | `UhidChannel.awaitReady` | function call | ✓ WIRED | Evaluates `sawStart` and `sawOpen` flags concurrently |
| `UhidChannel.sendReport` | `output.write` | `writeInput2Locked` | ✓ WIRED | Writes `UHID_INPUT2` packets directly without intermediate queuing |

**Wiring:** 3/3 connections verified

## Requirements Coverage

| Requirement | Status | Blocking Issue |
|-------------|--------|----------------|
| **UHID-01**: Stoppable reader thread without leaks (#48) | ✓ SATISFIED | None; thread terminates within `READER_JOIN_MS` (100ms) |
| **UHID-02**: Early exit upon `sawStart || sawOpen` (#49) | ✓ SATISFIED | None; attachment latency eliminated on OPEN-less devices |
| **UHID-03**: Purge dead stream-model methods and deferred queue (#50) | ✓ SATISFIED | None; dead code completely removed |

**Coverage:** 3/3 requirements satisfied

## Anti-Patterns Found

None found. No TODOs, stubs, or placeholder comments introduced. All changes conform to `CODE_QUALITY_STANDARDS.md` with zero emojis.

## Human Verification Required

None — all kernel protocol and readiness changes are verified deterministically by Robolectric JVM unit tests with virtual clock injection.

# Phase 1: Core Kernel UHID Fixes - Research

**Researched:** 2026-10-06
**Domain:** Linux Kernel UHID Driver, Android POSIX I/O, Character Device Message Queues
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

No user constraints - all decisions at the agent's discretion.

### Key Goals & Review Items
- Address GitHub Issue #48: Stoppable UHID readiness reader without leaked/parked background threads.
- Address GitHub Issue #49: Readiness gate exiting on `sawStart || sawOpen` to avoid 2000ms stalls on OPEN-less devices.
- Address GitHub Issue #50: Remove dead stream-model methods (`waitForStart`, `drainPayload`) and unreachable deferred INPUT2 queue.
</user_constraints>

<architectural_responsibility_map>
## Architectural Responsibility Map

Single-tier application (Android Client Hardware Emulation Layer) — all capabilities reside in the `shizuku.uhid` package:
- `UhidChannel.kt`: File descriptor management, non-blocking polling read loop, readiness gate, device creation/destruction.
- `UhidProtocol.kt`: Binary packet structuring (`UHID_CREATE2`, `UHID_DESTROY`, `UHID_INPUT2`), packet size constants, and kernel message definitions.
</architectural_responsibility_map>

<research_summary>
## Summary

Phase 1 addresses core Linux kernel `/dev/uhid` integration stability issues raised in reviews by collaborator @guaje (#48, #49, #50). `/dev/uhid` is a character device backed by kernel message queues in `drivers/hid/uhid.c`. Unlike ordinary byte streams, every `read()` syscall consumes and frees an entire `struct uhid_event` message from the kernel queue.

The current implementation suffers from three primary issues:
1. `stopReader()` calls `Thread.interrupt()` and closes `readPfd`, but because `readPfd` shares the underlying kernel open file description with the primary descriptor, blocking reads on `input.read()` remain parked in kernel wait queues indefinitely, leaking `uhid-reader` threads over repeated attach/detach cycles (#48).
2. `awaitReady()` waits exclusively for `UHID_OPEN`, but the Linux kernel only guarantees `UHID_START` during probe; `UHID_OPEN` is queued only when userspace opens the device node, which customized OEM ROMs (ColorOS, EMUI, etc.) may delay or omit, causing a full 2-second timeout stall on every attach (#49).
3. `UhidProtocol` contains dead stream-model methods (`waitForStart` and `drainPayload`) and `UhidChannel` contains an unreachable deferred INPUT2 queue that claims delivery of reports without backpressure (#50).

**Primary recommendation:** Use non-blocking `Os.poll()` or timed availability polling on `/dev/uhid` so reader threads exit cleanly when `readerStop` is flagged; exit `awaitReady()` immediately on `sawStart || sawOpen`; and purge the dead stream-model methods and unreachable deferred queue.
</research_summary>

<standard_stack>
## Standard Stack

### Core
| Library / API | Version | Purpose | Why Standard |
|---------------|---------|---------|--------------|
| `android.system.Os` | Android API 26+ | POSIX `poll()` and non-blocking fd I/O | Provides bounded polling on the file descriptor with timeout, enabling clean cooperative cancellation of reader threads. |
| `java.nio.ByteBuffer` | Java standard | Binary packet packing/unpacking | Standard, endian-safe byte serialization for 4376-byte `uhid_event` packets. |
| Linux Kernel UHID UAPI | Linux 4.4+ | `/dev/uhid` protocol | Kernel standard for userspace virtual HID devices. |

### Supporting
| Component | Purpose | When to Use |
|-----------|---------|-------------|
| `UhidReadinessConfig` | Injected virtual clock and presence checker | Provides deterministic time control in unit tests without wall-clock sleeps. |
| `PipedInputStream` / `PipedOutputStream` | In-memory pipe for JVM unit tests | Allows testing packet reception on JVM without requiring real `/dev/uhid`. |

### What NOT to Use
- `Thread.interrupt()` to unblock `FileInputStream.read()`: `FileInputStream` is not an `InterruptibleChannel`; `Thread.interrupt()` has no effect on a thread blocked in a native `read()` syscall on a Linux device node.
- Byte-skipping on `/dev/uhid`: `InputStream.skip()` or partial reads drop entire kernel messages due to kernel `kfree` semantics in `uhid_char_read()`.
</standard_stack>

<architecture_patterns>
## Architecture Patterns

### Non-blocking / Polled Reader Thread Loop (#48)
```
          ┌───────────────────────────────────┐
          │ startReader() spawns uhid-reader  │
          └─────────────────┬─────────────────┘
                            │
            ┌───────────────▼───────────────┐
            │ Check !readerStop.get() &&    │
            │ !closed.get()                 │
            └───────────────┬───────────────┘
                            │
            ┌───────────────▼───────────────┐
            │ Poll fd with 50-100ms timeout │
            │ (or input.available() check)  │
            └───────┬───────────────┬───────┘
          Data ready?      Timeout expired?
             │                     │
             ▼                     ▼
┌─────────────────────────┐   Loop back & re-check
│ Read full 4376-byte     │   readerStop flag
│ uhid_event struct       │
└────────────┬────────────┘
             │
┌────────────▼────────────┐
│ Dispatch START or OPEN  │
│ to ReadinessWait        │
└─────────────────────────┘
```

When `stopReader()` sets `readerStop.set(true)`, the loop exits at the next poll timeout (within milliseconds), allowing `reader.join(READER_JOIN_MS)` to complete reliably.

### Dual Exit Readiness Gate (#49)
```kotlin
// Exit readiness window as soon as either START or OPEN is observed
while (readinessConfig.nanoTime() < openDeadline &&
       !wait.sawStart.get() &&
       !wait.sawOpen.get()) {
    readinessConfig.sleeper(readinessConfig.pollIntervalMs)
}
```
`UHID_START` proves the kernel driver bound successfully. `UHID_OPEN` is an optional early confirmation. If neither arrives within `openTimeoutMs`, device creation logs a warning and proceeds as before.

### Removal of Dead Stream-Model Code (#50)
- Remove `UhidProtocol.waitForStart()` and `UhidProtocol.drainPayload()`.
- Retain `UhidProtocol.payloadSize()` as documentation / diagnostic reference or clean internal helper.
- Delete `deferredReports` and `allowInputLocked()` from `UhidChannel`: `createDevice()` blocks until the device is created, so all callers write directly once created.
</architecture_patterns>

## Validation Architecture

### Automated Verification Commands
- Quick Unit Suite:
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.uhid.*"
  ```
- Full Test Suite:
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```

### Critical Test Cases to Validate
1. **Stoppable Reader:** Verify that `stopReader()` and `channel.close()` terminate the reader thread within `READER_JOIN_MS` without hanging or throwing unhandled exceptions.
2. **Dual START / OPEN Gate:**
   - Pre-writing `UHID_OPEN` exits readiness before timeout.
   - Pre-writing `UHID_START` exits readiness before timeout.
   - Writing neither degrades gracefully after bounded timeout without hanging.
3. **Stream-Model Purge:** Verify that `UhidProtocolTest` and `UhidChannelReadinessTest` compile and pass without referencing deleted methods.

---
*Research completed: 2026-10-06*

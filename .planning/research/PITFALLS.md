# Pitfalls Research

**Domain:** Android Software KVM (Linux UHID, Kernel Mechanics, IPC Lifecycle, Test Determinism)
**Researched:** 2026-10-05
**Confidence:** HIGH

## Critical Pitfalls

### Pitfall 1: Uninterruptible Blocking Reads on `/dev/uhid` (#48)

**What goes wrong:**
Calling `stopReader()` fails to unblock `readerThread` parked in `FileInputStream.read()`. The thread leaks and stays parked forever until device teardown, accumulating multiple zombie reader threads over repeated keyboard attach/detach cycles.

**Why it happens:**
Java's `Thread.interrupt()` does not interrupt standard blocking JVM file I/O (`FileInputStream`). In the Linux kernel (`drivers/hid/uhid.c`), a blocking read waits on the kernel waitqueue `uhid->waitq` and only wakes when a kernel event is queued.

**How to avoid:**
Use `android.system.Os.poll()` or non-blocking I/O with a finite timeout (e.g. 100ms) on the file descriptor. Loop while `!readerStop.get()`, checking the stop flag between poll timeouts. This guarantees `reader.join()` terminates promptly and cleanly.

**Warning signs:**
- `reader.join(READER_JOIN_MS)` hits its timeout and logs warnings.
- Profiler thread dumps show multiple parked `uhid-reader` threads.

**Phase to address:**
Phase 1: Core UHID & Kernel Driver Fixes

---

### Pitfall 2: Stalling on `UHID_OPEN` on OPEN-less OEM Devices (#49)

**What goes wrong:**
`createDevice()` stalls for the entire 2000ms `openTimeoutMs` on every device attach, introducing severe cursor latency or freezes when crossing screen edges on customized Android OEM ROMs.

**Why it happens:**
The Linux kernel guarantees `UHID_START` during probe when the driver binds (`hid_hw_start`), but `UHID_OPEN` is queued only when a userspace process opens the device node. On some OEM ROMs (ColorOS, EMUI, customized vendor input filters), an opener may not immediately exist or emit `OPEN`.

**How to avoid:**
Exit the readiness wait window on `wait.sawStart.get() || wait.sawOpen.get()`. Treat `START` as the kernel-guaranteed readiness signal, with `OPEN` as an optional early confirmation.

**Warning signs:**
- Device creation logs show timeout expiration before input is allowed.
- Noticeable multi-second lag when mouse enters Android screen on first touch.

**Phase to address:**
Phase 1: Core UHID & Kernel Driver Fixes

---

### Pitfall 3: Treating `/dev/uhid` as a Byte Stream (#50)

**What goes wrong:**
Methods like `waitForStart()` and `drainPayload()` read a 4-byte header and skip the remaining bytes. This silently destroys or corrupts the next queued event or blocks indefinitely.

**Why it happens:**
`/dev/uhid` is a message queue, not a byte stream. The Linux kernel's `uhid_char_read()` copies the requested bytes and immediately `kfree`s the entire queued `uhid_event` struct. A short read permanently discards the rest of that message.

**How to avoid:**
Purge `waitForStart()` and `drainPayload()`. Always read whole 4376-byte `uhid_event` structs in a single atomic read operation matching the kernel's ABI.

**Warning signs:**
- Dropped kernel events or desynchronized event loops on UHID readers.
- Tests passing against `ByteArrayInputStream` mocks that fail against real Linux kernel fds.

**Phase to address:**
Phase 1: Core UHID & Kernel Driver Fixes

---

### Pitfall 4: Flaky Wall-Clock Timing & Arbitrary Sleeps in JVM Tests (#51)

**What goes wrong:**
Tests call `Thread.sleep(25)` or assert `elapsedMs < 800`. On busy CI runners, scheduler contention causes tests to randomly fail, creating build flakes and slowing PR merges.

**Why it happens:**
Developers use real sleeps to wait for background threads or asynchronous pipelines instead of controlling time explicitly.

**How to avoid:**
Use `kotlinx-coroutines-test` (`runTest`), virtual clocks (`UhidReadinessConfig.nanoTime`/`sleeper`), and synchronous latches or pre-written pipe buffers. Never rely on wall-clock measurements in unit tests.

**Warning signs:**
- `Thread.sleep` calls appearing in `app/src/test/java/`.
- Flaky tests on GitHub Actions ubuntu-latest runners.

**Phase to address:**
Phase 2: Deterministic Testing & CI Guardrails

---

### Pitfall 5: Stale Client Binder Death Tearing Down Active Devices (#52)

**What goes wrong:**
When a client reconnects and provides a new binder token, an in-flight death notification from the old, replaced token can arrive and immediately tear down the virtual devices that the new client just created.

**Why it happens:**
`InputInjectorService.attachClient` swallows `unlinkToDeath` errors and triggers teardown unconditionally without checking whether the dead token is still current.

**How to avoid:**
Assign a monotonic generation ID to each attached client token. When `binderDied()` fires, check if the token matches the active generation before triggering any cleanup.

**Warning signs:**
- Unexpected device disconnects immediately following reconnect or rotation.

**Phase to address:**
Phase 3: Lifecycle, IPC & Review Follow-ups

---

### Pitfall 6: OEM Display Insets & Lifecycle Crashes on Tablets (#54)

**What goes wrong:**
App crashes immediately upon connecting on devices like Huawei MatePad SE 11.

**Why it happens:**
Tablets with custom OEM ROMs (HarmonyOS / EMUI) often have non-standard display cutouts, null window metrics during initial configuration, or enforce background service start restrictions.

**How to avoid:**
Defensively guard all display metric calculations with safe defaults, add comprehensive try-catch wrappers around display insets resolution, and verify foreground service start ordering.

**Warning signs:**
- Issue #54 reports: "Whenever it tries to connect to my computer, it crashes" on MatePad.

**Phase to address:**
Phase 4: Form-Factor & OEM Compatibility

---
*Pitfalls research: 2026-10-05*

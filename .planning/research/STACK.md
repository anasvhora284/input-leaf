# Stack Research

**Domain:** Android Software KVM (Input Leap / Deskflow / Synergy Client, Linux UHID, Shizuku IPC)
**Researched:** 2026-10-05
**Confidence:** HIGH

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Linux Kernel UHID (`/dev/uhid`) | Kernel 4.4+ / UAPI | Virtual Hardware Device Emulation | Bypasses Android framework input filtering and latency; emulates native physical USB mouse, keyboard, and digitizer. |
| Android POSIX I/O (`android.system.Os`) | API 26+ | Polling and non-blocking fd I/O | Enables `Os.poll()` on `/dev/uhid` file descriptor with timeout so reader threads can cleanly stop without blocking indefinitely (#48). |
| USB HID Digitizer Specification | HID 1.11 (Page 0x0D) | Absolute Stylus / Drawing Tablet Emulation | Standard USB HID digitizer report format with In-Range (0x32), Tip Switch (0x42), and Barrel Switch (0x44), natively recognized by Android InputReader. |
| Synergy 3 Binary Wire Protocol | v1.6 / v1.7 | Cross-platform KVM client communication | Supported by Synergy 3.7+ desktop servers; uses signed 16-bit relative delta packets (`DMRM`) and Mac Carbon keycodes. |
| Jetpack Compose Material 3 | 1.5.0-alpha29 (BOM 2026.08.00) | Expressive UI, Dialogs, and Settings | Clean reactive UI state rendering for pointer mode selection and server configuration. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `dev.rikka.shizuku:api` | 13.1.5 | Unrooted system-level binder IPC | Primary execution path for unrooted devices with ADB Wireless Debugging. |
| `com.github.topjohnwu.libsu:core` | 6.0.0 | Root service execution as UID 0 | Primary execution path for rooted devices running KernelSU, Magisk, or APatch. |
| `androidx.datastore:datastore-preferences` | 1.2.1 | Thread-safe preference storage | Persisting server lists, pointer device mode, and TLS fingerprints across app restarts. |
| `androidx.leanback:leanback` / Manifest metadata | API 26+ | Android TV Leanback Banner & Navigation | Declaring TV banner, declaring touchscreen non-mandatory, and supporting DPAD navigation. |

### Development & Testing Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `kotlinx-coroutines-test` (1.11.0) | Virtual time coroutine testing | Replaces real wall-clock sleeps with deterministic `runTest` and `advanceUntilIdle()`. |
| Virtual Clock Seams (`UhidReadinessConfig`) | Injectable clock for UHID timeouts | Removes `Thread.sleep` from readiness tests; asserts virtual nanoseconds explicitly (#51). |
| Kotlinx Kover (0.9.9) | JVM bytecode coverage verification | Tracks 100% patch test coverage on core protocol and UHID modules. |

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| `Os.poll()` timeout on `/dev/uhid` | `Thread.interrupt()` on `FileInputStream` | Never: Java `FileInputStream.read()` is not interruptible; kernel wait queue blocks until device event or closed fd (#48). |
| Readiness exit on `sawStart || sawOpen` | Wait strictly for `UHID_OPEN` | Never on OEM ROMs: kernel guarantees `UHID_START` during probe; `UHID_OPEN` is userspace-dependent and never arrives on some ROMs (#49). |
| Separate `ConnectionCoordinator` | Logic directly in `ConnectionService` | Monolithic service is untestable on JVM without mocks; coordinator enables 100% pure JVM unit test coverage (#12). |
| Native `HidPen` absolute digitizer | Accessibility gesture injection for stylus | Stylus via accessibility has 20ms+ latency and lacks pressure/barrel button fidelity. |

## What NOT to Use

- `Thread.sleep()` in JVM tests: Violates project testing principles, introduces CI flakes, and masks concurrency bugs.
- `waitForStart()` / `drainPayload()` byte-skipping on `/dev/uhid`: `/dev/uhid` is a message queue, not a byte stream; short reads or byte-skipping corrupts or discards subsequent kernel events (#50).
- Unbounded in-memory report queues: Queuing reports before device readiness without backpressure leaks memory and drops events silently.
- Dagger / Hilt dependency injection: Introduces kapt/ksp build overhead and annotation bloat for an app with a focused architecture.

---
*Stack research: 2026-10-05*

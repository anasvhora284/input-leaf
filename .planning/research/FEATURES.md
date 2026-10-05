# Feature Research

**Domain:** Android Software KVM (Input Leap / Deskflow / Synergy Client)
**Researched:** 2026-10-05
**Confidence:** HIGH

## Feature Landscape

### Table Stakes (Users Expect These)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Clean UHID Lifecycle Teardown | Devices and threads must terminate on disconnect without parking background threads (#48) | MEDIUM | Use non-blocking / polled read with timeout so `reader.join()` exits cleanly and no threads are leaked across sessions. |
| Zero-Delay Attach on All ROMs | Fast cursor crossing without burning 2000ms timeouts on OEM devices (#49) | LOW | Exit readiness wait immediately upon `sawStart || sawOpen`; do not stall waiting for userspace `OPEN` that never arrives. |
| Deterministic Test Execution | Fast local `./gradlew testDebugUnitTest` without random failures (#51) | MEDIUM | Replace all `Thread.sleep` and wall-clock time assertions with virtual clock seams (`UhidReadinessConfig`) and coroutine test scopes. |
| MatePad / EMUI Stability | Tablets like Huawei MatePad SE 11 must not crash during handshake/service binding (#54) | MEDIUM | Safeguard against OEM display insets nullability, background service restrictions, and IPC binding race conditions. |
| Saved Server Persistence | Manually added and favorited server addresses must survive app restarts (#53) | LOW | Persist configured and discovered servers in DataStore; restore and merge with active mDNS scan results. |

### Differentiators (Competitive Advantage)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Multi-Pointer Mode Selection (Mouse vs. Pen) | Artists and tablet users can switch between a standard mouse cursor and an absolute drawing tablet/stylus (`HidPen`) | MEDIUM | Expose a settings toggle in UI (`PointerDevice.MOUSE`, `PointerDevice.PEN`, `PointerDevice.TOUCH`); route protocol motion events accordingly. |
| Synergy 3.x Protocol Compatibility | Connects seamlessly to both modern Synergy 3.7+ servers as well as Input Leap and Deskflow | HIGH | Decode signed 16-bit `DMRM` relative delta packets, handle `DSOP` completion before `CIAK`, and support Mac Carbon keycode translation. |
| Android TV Form-Factor Support | Users can control Android TV boxes and smart TVs using their desktop mouse and keyboard | MEDIUM | Add Leanback banner metadata in `AndroidManifest.xml`, declare `touchscreen` non-mandatory, and map desktop arrow keys to Android DPAD keycodes. |
| Decoupled `ConnectionCoordinator` | Eliminates state synchronization bugs in `ConnectionService` and guarantees 100% testable state transitions | HIGH | Pure Kotlin coordinator managing connection generations, retry backoff, keepalive timeouts, and event routing without Android dependencies. |

### Anti-Features (Avoid)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Arbitrary `Thread.sleep` in tests | Easy way to wait for async events | Flaky in CI, slow execution, masks race conditions | Use coroutine virtual time (`runTest`) and explicit synchronous test latches. |
| Stream-oriented `/dev/uhid` reading | Seems natural to read 4-byte header and skip body | Kernel drops entire event on short read and invalidates queue state (#50) | Always read complete 4376-byte `uhid_event` struct in single atomic read syscall. |
| Cloud Relay / WebRTC Relay | Simplifies connection across different networks | Introduces latency (>50ms), compromises keystroke security, requires remote servers | Strictly maintain local LAN architecture over Wi-Fi / Ethernet. |

## Feature Dependencies

```
[Decoupled ConnectionCoordinator] (#12)
    └──enables──> [Zero-Flake Deterministic JVM Tests] (#51)

[Stoppable UHID Reader] (#48)
    └──combines_with──> [Dual START/OPEN Readiness Gate] (#49)
                             └──powers──> [Stable UHID Device Lifecycle]

[Pointer Device Selection]
    └──routes_to──> [HidMouse (Relative)] OR [HidPen (Absolute Digitizer)]

[Synergy 3.x Codec] (#47, #53)
    └──enhances──> [InputLeapConnection Protocol Pipeline]
```

### Dependency Notes

- **Stoppable Reader (#48) + Readiness Gate (#49):** Together solve all known attach/detach latency issues and thread leaks on custom and OEM Android kernels.
- **ConnectionCoordinator (#12):** Provides the clean architectural separation needed to test retry logic, input routing, and connection generations with 100% coverage on the JVM.
- **Pointer Device Selection:** Connects the UI preference layer to either `HidMouse` or the newly implemented `HidPen` absolute digitizer.

---
*Features research: 2026-10-05*

# Codebase Concerns

**Analysis Date:** 2026-10-05

## Tech Debt

**`ConnectionService` God Object:**
- Issue: `ConnectionService.kt` is 950+ lines long and consolidates too many responsibilities: socket lifecycle, reconnection backoff, UI notification updates, settings observation, cursor overlay positioning, screen dimension tracking, and input injector selection.
- Why: Rapid feature development and evolutionary fixes (e.g., race conditions, UHID idle timeouts) were added directly into the service orchestrator.
- Impact: Difficult to unit-test `ConnectionService` comprehensively without mocking numerous Android framework dependencies; high coupling makes refactoring risky.
- Fix approach: Refactor into modular components as outlined in `ARCHITECTURE_IMPROVEMENTS.md`: extract pure `ConnectionStateMachine`, a dedicated `KeepAliveMonitor`, a `CursorCoordinator`, and a `RetryPolicy`.

**Lack of Dependency Injection (DI):**
- Issue: No DI framework (such as Hilt, Koin, or manual factory containers). Classes directly instantiate concrete dependencies (e.g., `AppPreferences`, `InputLeapConnection`, `TlsFingerprintManager`).
- Why: Kept the project lightweight with zero annotation processing overhead.
- Impact: Testing components in isolation requires passing numerous constructor arguments or creating custom test doubles.
- Fix approach: Introduce a lightweight dependency container or modular service locator to decouple service and view model instantiation.

**Byte Buffer Protocol Coupling:**
- Issue: `ProtocolParser` and `ProtocolWriter` are tightly coupled to custom `ByteArray` extensions and packet slice operations rather than abstracted streaming codecs (`InputStream`/`OutputStream` or Okio).
- Why: Direct porting from upstream C++ Input Leap binary packet specifications.
- Impact: Difficult to implement packet fuzzing or stream pipeline optimizations without allocating temporary byte arrays.
- Fix approach: Introduce a `FrameCodec` interface with standard stream-based read/write semantics.

## Known Bugs & Platform Quirks

**AOSP Pointer Center Seeding Race:**
- Symptoms: When the mouse cursor crosses from the desktop into the Android screen, the cursor momentarily strands or snaps to the center of the Android display rather than entering smoothly at the screen edge.
- Trigger: AOSP kernel driver seeds newly activated pointer devices at `(display_width / 2, display_height / 2)`.
- Workaround/Mitigation: Implemented `HidMouseEnterWarp.kt` and `onHidMouseEnter()` in `IInputInjector.aidl` so the privileged process holding `/dev/uhid` immediately warps the pointer to the entry coordinates upon device attachment before Android processes subsequent events.
- Root cause: AOSP default initialization behavior for new relative pointer devices.

**Notification Shade Overlay Restriction:**
- Symptoms: The visual cursor overlay disappears when pulling down the Quick Settings or notification shade.
- Trigger: Moving cursor over system UI panels.
- Mitigation: In Accessibility mode, `AccessibilityService` provides overlay coverage. In pure Shizuku mode, standard overlay windows (`TYPE_APPLICATION_OVERLAY`) are restricted by Android OS security policies from rendering over notifications.
- Root cause: Hard security restriction in Android WindowManager.

**OEM Battery Optimization Process Termination:**
- Symptoms: Connection drops abruptly when the device screen locks or the phone remains idle for several minutes.
- Trigger: Aggressive battery management on MIUI, OxygenOS/ColorOS, EMUI, and OneUI terminating background socket threads.
- Mitigation: App prompts user for `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Unrestricted battery) and maintains a persistent foreground service with `FOREGROUND_SERVICE_CONNECTED_DEVICE`.

## Security Considerations

**Plaintext Traffic on Untrusted LANs:**
- Risk: If desktop server does not have TLS enabled, all keystrokes (passwords, messages) and clipboard data are transmitted unencrypted over the local Wi-Fi.
- Current mitigation: App defaults to TLS with TOFU (Trust-On-First-Use) fingerprint pinning and displays explicit warnings/dialogs when falling back to plaintext TCP.
- Recommendations: Disallow unencrypted connections by default or add an explicit "Strict TLS Only" setting toggle.

**Privileged Injection Abuse (`/dev/uhid` & Shell Access):**
- Risk: The privileged daemon runs with root or ADB shell permissions and controls system-wide input injection.
- Current mitigation: Privileged service only binds to local app UID; `attachClient(IBinder token)` monitors client process death and immediately closes all virtual devices if the app crashes or terminates.
- Recommendations: Ensure client token validation remains strict across all IPC calls.

**mTLS Private Key Storage:**
- Risk: Compromised client private key could allow unauthorized network access.
- Current mitigation: Keys are encrypted at rest using AEAD (`AeadBlob.kt`) backed by keys from the Android KeyStore.

## Performance Bottlenecks

**Accessibility Gesture Dispatch Latency:**
- Problem: When running in Accessibility fallback mode, input injection suffers 15–25ms latency compared to <5ms in Shizuku/Root UHID mode.
- Cause: Android `dispatchGesture()` incurs inter-process context switching and gesture interpolation overhead.
- Recommendation: Encourage users to enable Shizuku or root for daily workstation productivity.

**Wheel Notch Flooding:**
- Problem: Fast scroll wheels on desktop mice emit high-frequency scroll packets that can saturate Android's event queue.
- Mitigation: `WheelNotchAccumulator.kt` batches and smooths sub-notch wheel deltas before generating UHID reports.

---

*Concerns analysis: 2026-10-05*
*Update after discovering new risks or technical debt*

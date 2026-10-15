# Architecture

**Analysis Date:** 2026-10-05

## Pattern Overview

**Overall:** Layered Event-Driven Client Architecture with Privileged IPC & Kernel Hardware Emulation

**Key Characteristics:**
- **Asynchronous Coroutine Event Loop**: Socket I/O, protocol parsing, and event forwarding run asynchronously on dedicated background dispatcher threads with zero blocking on the main UI thread.
- **Pluggable Privilege & Injection Pipeline**: Strategy pattern decouples input handling via an `InputInjector` interface into Shizuku (ADB shell), libsu (root UID 0), or Android Accessibility/IME fallbacks.
- **Kernel-Level UHID Emulation**: Mouse, keyboard, and stylus events are synthesized as raw USB HID reports sent over `/dev/uhid`, presenting virtual hardware devices directly to Android's `InputDispatcher`.
- **Reactive UI State Flow**: Jetpack Compose observes state emitted from `MainViewModel` and `ConnectionService` using Kotlin `StateFlow` and `SharedFlow`.

## Layers

**1. Presentation Layer (`app/src/main/java/com/inputleaf/android/ui`):**
- Purpose: Delivers user interface for onboarding, server discovery, status monitoring, and settings configuration.
- Contains: Jetpack Compose composables (`MainActivity.kt`, `MainScreen.kt`, `ServerListScreen.kt`, `SettingsScreen.kt`, `SetupScreen.kt`), navigation graph (`LeafNavigation.kt`), and `MainViewModel.kt`.
- Depends on: Orchestration layer (`ConnectionService`), storage layer (`AppPreferences`), and privilege utilities.
- Used by: End user via Android Activity lifecycle.

**2. Orchestration & Service Layer (`app/src/main/java/com/inputleaf/android/service`):**
- Purpose: Coordinates the active session lifecycle, network connection lifetime, foreground notification, and pointer state.
- Contains: `ConnectionService.kt` (Android Foreground Service), `ConnectionStateMachine` (state transition logic), `CursorOverlayService.kt` (floating cursor overlay using `SYSTEM_ALERT_WINDOW`), and `HidAttachmentController.kt`.
- Depends on: Network layer, privilege layer, injection layer, and storage layer.
- Used by: `MainActivity` and `MainViewModel` via service binding / intents.

**3. Network & Protocol Layer (`app/src/main/java/com/inputleaf/android/network`, `.../protocol`):**
- Purpose: Manages TCP sockets, TLS encryption, handshake negotiation, and binary wire protocol codecs.
- Contains: `InputLeapConnection.kt`, `ProtocolParser.kt`, `ProtocolWriter.kt`, `ProtocolConstants.kt`, `ServerScanner.kt` (mDNS), and `TlsFingerprintManager.kt`.
- Depends on: Domain models (`model/`), Java standard networking (`java.net.Socket`, `SSLSocket`), and Android NsdManager.
- Used by: `ConnectionService`.

**4. Privilege & IPC Layer (`app/src/main/java/com/inputleaf/android/privilege`, `.../shizuku`):**
- Purpose: Negotiates privilege acquisition (Shizuku vs. root vs. accessibility) and provides cross-process Binder communications.
- Contains: `PrivilegeSelector.kt`, `RootAccess.kt`, `PrivilegedUserServiceHost.kt`, `IInputInjector.aidl`, and `RootInputInjectorService.kt`.
- Depends on: Shizuku API, libsu, Android Binder IPC.
- Used by: `ConnectionService` to obtain an active `InputInjector`.

**5. Input Injection & UHID Layer (`app/src/main/java/com/inputleaf/android/inject`, `.../shizuku/uhid`):**
- Purpose: Translates protocol events into hardware-level USB HID reports or Android system injection calls.
- Contains:
  - Interface: `InputInjector.kt`
  - Shizuku/Root UHID drivers: `HidMouse.kt`, `HidKeyboard.kt`, `HidPen.kt`, `UhidChannel.kt`, `UhidProtocol.kt`, `EvdevToHid.kt`
  - Accessibility/IME fallback: `AccessibilityInputInjector.kt`, `AccessibilityInputService.kt`, `InputLeafIME.kt`
  - Key translation: `KeysymResolver.kt`, `KeyMapUtils.kt`, `KeysymUnicodeTable.kt`
- Depends on: Linux `/dev/uhid` device node, Android Accessibility APIs, Android InputMethodService.
- Used by: `ConnectionService`.

**6. Storage & Cryptography Layer (`app/src/main/java/com/inputleaf/android/storage`):**
- Purpose: Persists application preferences and securely stores cryptographic materials (mTLS keys and certificates).
- Contains: `AppPreferences.kt` (DataStore), `ClientCertificateStore.kt`, `AeadBlob.kt` (AEAD envelope encryption).
- Depends on: Android KeyStore, Android DataStore Preferences.
- Used by: UI layer and Network layer.

## Data Flow

**1. Connection & Handshake Flow:**
1. User taps "Connect" in `MainScreen` or `ServerListScreen`.
2. `MainViewModel` binds to `ConnectionService` and dispatches `startConnection(serverIp, screenName)`.
3. `ConnectionService` enters `ConnectionState.Connecting` and instantiates `InputLeapConnection`.
4. `InputLeapConnection` probes transport (TLS vs Plain) and initiates socket connection to TCP port 24800.
5. TLS certificate is verified against pinned fingerprint in `TlsFingerprintManager` (or prompts user for TOFU trust).
6. Protocol handshake runs: sends `Barrier` / `Synergy` hello, receives server hello, negotiates protocol version (1.6 / 1.7), and sends `DINF` (Display Info: screen resolution and coordinate bounds).
7. Handshake succeeds; state transitions to `ConnectionState.Connected`.

**2. Mouse Movement & Click Flow (UHID Path):**
1. PC user moves mouse cursor past desktop border onto Android screen space.
2. Desktop server sends binary `MMOV` (mouse move) or `MDWN`/`MUPP` (mouse button down/up) frame.
3. `InputLeapConnection.readerLoop` reads frame from `DataInputStream`, passes bytes to `ProtocolParser`, and emits `InputLeapEvent.MouseMove` or `InputLeapEvent.MouseDown`.
4. `ConnectionService` receives event via `events` flow collection.
5. Event forwarded to `ShizukuInputInjector` / `HidMouseState`.
6. Relative movement delta or button bitmask converted into standard USB HID mouse report.
7. Report written via `UhidChannel.writeReport()` to kernel `/dev/uhid`.
8. Android kernel delivers input event to Android `InputDispatcher`, smoothly moving the native system cursor and executing clicks.

**3. Keyboard Keystroke Flow:**
1. Desktop server transmits `KDN1` (key down) or `KUP1` (key up) with keysym and modifier mask.
2. `ProtocolParser` converts bytes into `InputLeapEvent.KeyDown` / `InputLeapEvent.KeyUp`.
3. `KeysymResolver` translates the X11 keysym into a Linux evdev keycode.
4. `EvdevToHid` translates evdev code into USB HID usage code.
5. `HidKeyboard` compiles the active 8-byte HID keyboard report (modifiers byte + up to 6 pressed keys).
6. Report written to `/dev/uhid`, triggering native Android key events (including shortcuts like `Alt+Tab`, `Meta`, `Ctrl+C`).

## Key Abstractions

**`InputInjector` (`app/src/main/java/com/inputleaf/android/inject/InputInjector.kt`):**
- Purpose: Unified contract for injecting input events regardless of the underlying execution privilege.
- Implementations: `ShizukuInputInjector` (UHID + system calls), `AccessibilityInputInjector` (gesture + IME fallback).

**`InputLeapEvent` (`app/src/main/java/com/inputleaf/android/model/InputLeapEvent.kt`):**
- Purpose: Domain model representing all possible inbound protocol messages from the desktop server.
- Subclasses: `MouseMove`, `MouseDown`, `MouseUp`, `MouseWheel`, `KeyDown`, `KeyUp`, `KeyRepeat`, `ClipboardData`, `KeepAlive`, `ResolutionChange`.

**`ConnectionState` (`app/src/main/java/com/inputleaf/android/model/ConnectionState.kt`):**
- Purpose: Represents the high-level connection lifecycle observed by the UI and service.
- Subclasses: `Disconnected`, `Connecting`, `Connected`, `Disconnecting`, `Failed`.

**`UhidChannel` (`app/src/main/java/com/inputleaf/android/shizuku/uhid/UhidChannel.kt`):**
- Purpose: Low-level file descriptor controller communicating with Linux kernel `/dev/uhid` character device.
- Methods: `open()`, `createDevice(descriptor)`, `writeReport(report)`, `close()`.

## Entry Points

**`MainActivity` (`app/src/main/java/com/inputleaf/android/ui/MainActivity.kt`):**
- Location: Android launcher activity (`android.intent.action.MAIN`).
- Responsibility: Sets up window insets (edge-to-edge), initializes Compose content hierarchy, and manages app lifecycle.

**`ConnectionService` (`app/src/main/java/com/inputleaf/android/service/ConnectionService.kt`):**
- Location: Android foreground service with `connectedDevice` type.
- Responsibility: Long-running background manager maintaining socket connectivity, state transitions, and input injection.

**`AccessibilityInputService` (`app/src/main/java/com/inputleaf/android/inject/AccessibilityInputService.kt`):**
- Location: Android Accessibility Service (`android.permission.BIND_ACCESSIBILITY_SERVICE`).
- Responsibility: OS-bound accessibility hook enabling gesture dispatching when running in unprivileged mode.

**`InputLeafIME` (`app/src/main/java/com/inputleaf/android/inject/InputLeafIME.kt`):**
- Location: Android Input Method Service (`android.permission.BIND_INPUT_METHOD`).
- Responsibility: Virtual soft keyboard used to inject text and character keys into active text inputs.

**`RootInputInjectorService` & `InputInjectorService` (`app/src/main/java/com/inputleaf/android/shizuku/`):**
- Location: AIDL IPC service (`IInputInjector.aidl`).
- Responsibility: Privileged daemon running under root or ADB shell UID that binds directly to `/dev/uhid`.

---

*Architecture analysis: 2026-10-05*
*Update after major architectural changes*

# External Integrations

**Analysis Date:** 2026-10-05

## APIs & External Services

**Desktop KVM Server (Input Leap / Deskflow / Barrier):**
- Role: Primary desktop server providing mouse, keyboard, and clipboard input streams
- Communication: Proprietary stateful binary TCP protocol over default port 24800 (`app/src/main/java/com/inputleaf/android/network/InputLeapConnection.kt`, `app/src/main/java/com/inputleaf/android/protocol/`)
- Security & Encryption:
  - TLS 1.2/1.3 with Trust-On-First-Use (TOFU) certificate validation (`app/src/main/java/com/inputleaf/android/network/TlsFingerprintManager.kt`)
  - Optional Mutual TLS (mTLS) with client certificate authentication using self-signed RSA-2048/4096 or custom PKCS#12 certificates (`app/src/main/java/com/inputleaf/android/network/ClientCertificateMaterial.kt`, `SelfSignedRsaCertificate.kt`)
  - Plaintext fallback when TLS is not enabled on the server
- Handshake & Messages:
  - Version negotiation (`Barrier` / `Synergy` protocol v1.6 / v1.7)
  - Screen dimensions exchange (`DINF` message with width, height, coordinate bounds)
  - Mouse coordinate streams (`MMOV`, `MDWN`, `MUPP`, `MWHL`)
  - Key events (`KDN1`, `KUP1`, `1CRA` keysym/keycode events)
  - Heartbeat / Keep-alive (`CALV` keepalive ping/pong)

**GitHub Releases (App Updates):**
- Role: In-app release checking and APK distribution (`app/src/main/java/com/inputleaf/android/update/UpdateService.kt`)
- Endpoint: `https://api.github.com/repos/anasvhora284/input-leaf/releases/latest`
- Format: JSON metadata containing tag name, release notes, asset download URLs
- Auth: Unauthenticated public HTTPS GET requests; handles HTTP 403 rate limits gracefully

## Hardware & System Integrations

**Linux Kernel UHID Subsystem (`/dev/uhid`):**
- Role: Kernel user-space HID emulation for hardware-grade input injection
- Interface: Binary IOCTL / character device protocol (`app/src/main/java/com/inputleaf/android/shizuku/uhid/UhidProtocol.kt`, `UhidChannel.kt`)
- Devices Emulated:
  - Virtual USB Mouse (`HidMouse.kt`) - Relative pointer deltas, standard 5-button mouse, smooth vertical/horizontal scroll
  - Virtual USB Keyboard (`HidKeyboard.kt`) - Full 104-key USB HID keyboard with modifier reporting
  - Virtual Absolute Pen / Tablet (`HidPen.kt`) - Absolute coordinate positioning with tip and barrel buttons
- Privileges Required: Read/write access to `/dev/uhid`, provided via ADB shell (Shizuku) or root (libsu)

**Rikka Shizuku IPC:**
- Role: Unrooted privileged system access provider
- Interface: Android Binder / AIDL IPC (`IInputInjector.aidl`)
- Implementation: `ShizukuInputInjector.kt` binds `InputInjectorService.kt` through `Shizuku.bindUserService`
- Permissions: Shell UID 2000 capabilities (`INJECT_EVENTS`, `/dev/uhid` access)

**libsu Root Service (KernelSU / Magisk / APatch):**
- Role: Direct root execution without requiring external apps
- Interface: `com.topjohnwu.superuser.ipc.RootService` running `RootInputInjectorService.kt` as UID 0
- Handshake: Automatic detection via `RootAccess.isRootAvailable()` and binding via `RootService.bind()`

**Android Accessibility Framework:**
- Role: Fallback non-privileged injection without Shizuku or root
- Components: `AccessibilityInputService.kt` and `AccessibilityInputInjector.kt`
- Capabilities: Gesture dispatch (`GestureDescription` / `dispatchGesture`) for touch emulation; limited system shortcut support

**Android Input Method Framework (IME):**
- Role: Keyboard text input helper for accessibility mode
- Component: `InputLeafIME.kt` (`android.view.InputMethod`)
- Capabilities: Injects non-ASCII unicode text and key events directly via `InputConnection.commitText`

## Network Discovery

**Android Network Service Discovery (mDNS / DNS-SD):**
- Role: Discovers Input Leap and Deskflow servers on the local subnet automatically
- Implementation: `ServerScanner.kt` using `android.net.nsd.NsdManager`
- Service Types: `_inputleap._tcp`, `_barrier._tcp`, `_synergy._tcp`
- Scope: Local Wi-Fi / Ethernet broadcast domain (multicast DNS)

## Data Storage & Cryptography

**DataStore Preferences:**
- Technology: `androidx.datastore:datastore-preferences`
- File: `input_leaf_preferences.preferences_pb` in app private storage directory
- Storage: Server addresses, screen name, active input method, pointer speed, TLS fingerprint pins

**Secure Key & Certificate Storage:**
- Implementation: `ClientCertificateStore.kt`, `AeadBlob.kt`
- Cryptography: Android KeyStore backing AES-256-GCM / ChaCha20-Poly1305 AEAD envelopes for sensitive PKCS#12 client credentials
- Persistence: Private internal storage files encrypted at rest

---

*Integrations analysis: 2026-10-05*
*Update after major integration changes*

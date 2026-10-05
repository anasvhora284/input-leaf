# Codebase Structure

**Analysis Date:** 2026-10-05

## Directory Layout

```
input-leaf-android/
├── .github/                      # CI workflows and issue templates
│   └── workflows/ci.yml          # GitHub Actions CI matrix and test automation
├── app/                          # Main Android application module
│   ├── build.gradle.kts          # App module Gradle build definition
│   ├── proguard-rules.pro        # R8 / ProGuard rules
│   ├── input-leaf.jks            # Release signing keystore
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # App components, permissions, and service declarations
│       │   ├── aidl/             # AIDL IPC interface definitions
│       │   ├── java/             # Kotlin application source files
│       │   └── res/              # Android XML resources, drawables, strings, themes
│       ├── test/                 # JVM unit tests (JUnit4, Robolectric, Truth, Mockito)
│       └── androidTest/          # Android instrumentation tests (Compose UI tests)
├── docs/                         # End-user documentation, guides, and screenshots
│   ├── deskflow-android.md       # Setup guide for Deskflow server
│   ├── input-leap-android.md     # Setup guide for Input Leap server
│   ├── TESTING.md                # Testing strategy and guide
│   └── screenshots/              # UI screenshots for README and documentation
├── gradle/                       # Gradle wrapper and central version catalog
│   ├── wrapper/                  # Gradle wrapper JAR and properties
│   └── libs.versions.toml        # Shared library versions and dependencies
├── scripts/                      # Code generation and CI automation scripts
│   ├── generate_keysym_unicode_table.py # Generates keysym lookup tables
│   ├── codecov_skip_unprobed.py  # Fixes JaCoCo unprobed lines for Codecov
│   └── keysyms.txt               # Raw keysym data definition
├── .planning/                    # GSD workflow memory, codebase maps, and planning artifacts
├── build.gradle.kts              # Root project build file
├── settings.gradle.kts           # Gradle module inclusion and repository configuration
├── gradle.properties             # JVM args, AndroidX flags, Kotlin settings
├── codecov.yml                   # Codecov configuration and target thresholds
└── README.md                     # Project overview, installation, and user instructions
```

## Directory Purposes

**`app/src/main/java/com/inputleaf/android/inject/`:**
- Purpose: Input injection contracts and fallback injection mechanisms
- Key files:
  - `InputInjector.kt`: Primary injection interface
  - `AccessibilityInputInjector.kt`: Android Accessibility API implementation
  - `AccessibilityInputService.kt`: Accessibility service component
  - `InputLeafIME.kt`: Soft keyboard input method service for text injection
  - `KeysymResolver.kt`, `KeyMapUtils.kt`: X11 keysym to Android/Linux keycode translation
  - `PointerDevice.kt`: Mouse vs. Pen pointer type abstractions

**`app/src/main/java/com/inputleaf/android/model/`:**
- Purpose: Core immutable domain data classes and event models
- Key files:
  - `InputLeapEvent.kt`: Sealed class hierarchy of all inbound protocol events
  - `ConnectionState.kt`: Sealed class representing client connection lifecycle states
  - `ServerInfo.kt`: Discovered or configured server metadata

**`app/src/main/java/com/inputleaf/android/network/`:**
- Purpose: Socket transport, TLS validation, network discovery, and connection policy
- Key files:
  - `InputLeapConnection.kt`: Socket connection manager and asynchronous event stream reader
  - `TlsFingerprintManager.kt`: SHA-256 fingerprint verification and Trust-On-First-Use (TOFU) pinning
  - `ServerScanner.kt`: mDNS / DNS-SD discovery via `NsdManager`
  - `TransportProber.kt`, `ConnectionTransportPolicy.kt`: Automatic TLS vs. plain TCP probe
  - `ClientCertificateMaterial.kt`, `SelfSignedRsaCertificate.kt`: Mutual TLS (mTLS) client auth

**`app/src/main/java/com/inputleaf/android/privilege/`:**
- Purpose: Privilege discovery, selection, and root service bootstrapping
- Key files:
  - `PrivilegeKind.kt`: Enum distinguishing Shizuku, Root, and Accessibility
  - `PrivilegeSelector.kt`: Strategy for selecting the best available privilege
  - `RootAccess.kt`: Magisk/KernelSU root status checking
  - `RootInputInjectorService.kt`: libsu `RootService` running as UID 0

**`app/src/main/java/com/inputleaf/android/protocol/`:**
- Purpose: Input Leap / Barrier binary wire protocol codecs
- Key files:
  - `ProtocolConstants.kt`: Packet headers, command opcodes, and version strings
  - `ProtocolParser.kt`: Binary packet decoder converting byte buffers to `InputLeapEvent`
  - `ProtocolWriter.kt`: Binary packet encoder constructing outgoing handshake frames

**`app/src/main/java/com/inputleaf/android/service/`:**
- Purpose: Android foreground service management and OS integration
- Key files:
  - `ConnectionService.kt`: Core foreground service maintaining the active connection
  - `CursorOverlayService.kt`: Visual cursor pointer rendered via system overlay window
  - `HidAttachmentController.kt`: State gate managing attachment/detachment of virtual devices
  - `PointerSpeed.kt`: Conversion and compensation for Android system pointer speed

**`app/src/main/java/com/inputleaf/android/shizuku/`:**
- Purpose: Privileged Shizuku IPC implementation and kernel UHID device drivers
- Key files:
  - `InputInjectorService.kt`: AIDL service implementation running in privileged process
  - `ShizukuInputInjector.kt`: Client proxy implementing `InputInjector`
  - `HiddenInputManager.kt`: Reflection access to hidden Android system methods
  - Subdirectory `uhid/`:
    - `UhidChannel.kt`: File descriptor manager for `/dev/uhid`
    - `UhidProtocol.kt`: Linux kernel UHID ioctl constants and structs
    - `HidMouse.kt`: Mouse HID report builder and descriptor
    - `HidKeyboard.kt`: Keyboard HID report builder and descriptor
    - `HidPen.kt`: Absolute stylus/tablet HID report builder and descriptor
    - `HidMouseState.kt`: Mouse state tracking, boundary clamping, and coordinate scaling
    - `EvdevToHid.kt`: Linux evdev to USB HID usage code mapping

**`app/src/main/java/com/inputleaf/android/storage/`:**
- Purpose: Persistence and cryptographic key storage
- Key files:
  - `AppPreferences.kt`: DataStore key-value preferences manager
  - `ClientCertificateStore.kt`: PKCS#12 client certificate storage
  - `AeadBlob.kt`: Authenticated AES-GCM envelope encryption

**`app/src/main/java/com/inputleaf/android/ui/`:**
- Purpose: Jetpack Compose UI screens, view models, and styling
- Key files:
  - `MainActivity.kt`: Main Android activity
  - `MainScreen.kt`: Primary home dashboard with connection toggle
  - `MainViewModel.kt`: UI view model orchestrating service and state flows
  - `ServerListScreen.kt`: Server management and discovery list
  - `SettingsScreen.kt`: Comprehensive settings screen
  - `SetupScreen.kt`: Privilege setup and permission troubleshooting guides
  - `LeafNavigation.kt`: Navigation routes and items
  - `theme/`: Material 3 Expressive theme, typography, and dynamic colors
  - `components/`: Modular Compose cards, dialogs, and widgets

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/inputleaf/android/ui/MainActivity.kt`: User UI entry point
- `app/src/main/java/com/inputleaf/android/service/ConnectionService.kt`: Foreground service entry point
- `app/src/main/java/com/inputleaf/android/shizuku/InputInjectorService.kt`: Privileged process entry point

**Configuration:**
- `gradle/libs.versions.toml`: Centralized version catalog
- `app/build.gradle.kts`: Android build settings and dependencies
- `codecov.yml`: Code coverage thresholds and ignore list

**Core Logic:**
- `app/src/main/java/com/inputleaf/android/network/InputLeapConnection.kt`: Network connection logic
- `app/src/main/java/com/inputleaf/android/protocol/ProtocolParser.kt`: Protocol decoding
- `app/src/main/java/com/inputleaf/android/shizuku/uhid/HidMouse.kt`: UHID mouse driver
- `app/src/main/java/com/inputleaf/android/shizuku/uhid/HidKeyboard.kt`: UHID keyboard driver

**Testing:**
- `app/src/test/java/com/inputleaf/android/`: Complete suite of JVM unit tests
- `app/src/test/java/com/inputleaf/android/testutil/LoopbackServer.kt`: Mock server for loopback tests
- `app/src/androidTest/java/com/inputleaf/android/`: Compose UI and service instrumented tests

---

*Structure analysis: 2026-10-05*
*Update after major directory or architectural changes*

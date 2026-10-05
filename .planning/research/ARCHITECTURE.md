# Architecture Research

**Domain:** Android Software KVM (Input Leap / Deskflow / Synergy Client, Linux UHID, Shizuku IPC)
**Researched:** 2026-10-05
**Confidence:** HIGH

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      UI & PRESENTATION                       │
│  ┌───────────────────────┐       ┌───────────────────────┐  │
│  │   Jetpack Compose     │◄─────►│    MainViewModel      │  │
│  │   Screens & Dialogs   │       │   (StateFlow / UI)    │  │
│  └───────────────────────┘       └───────────┬───────────┘  │
├──────────────────────────────────────────────┼──────────────┤
│               SERVICE & ORCHESTRATION LAYER  │              │
│  ┌───────────────────────────────────────────▼───────────┐  │
│  │  ConnectionService (Android Foreground Service)       │  │
│  │  - Android Lifecycle, Notifications, Service Binding  │  │
│  │  - CursorOverlayService & Display Dimension Watching  │  │
│  └───────────────────────┬───────────────────────────────┘  │
│                          │ delegates state & routing        │
│  ┌───────────────────────▼───────────────────────────────┐  │
│  │  ConnectionCoordinator (Pure Kotlin Domain Logic) #12 │  │
│  │  - Connection Generations & Reconnect Backoff Policy  │  │
│  │  - Input Routing Decisions & Keepalive Heartbeat Gate │  │
│  └───────────────────────┬───────────────────────────────┘  │
├──────────────────────────┼──────────────────────────────────┤
│             NETWORK & PROTOCOL LAYER                        │
│  ┌───────────────────────▼───────────┐                      │
│  │  InputLeapConnection              │                      │
│  │  - TLS Prober & Fingerprint TOFU  │                      │
│  │  - Synergy 3 / Input Leap Codecs  │                      │
│  └───────────────────────┬───────────┘                      │
├──────────────────────────┼──────────────────────────────────┤
│           PRIVILEGE & HARDWARE EMULATION LAYER               │
│  ┌───────────────────────▼───────────┐                      │
│  │  IInputInjector AIDL (Shizuku / Root UID 0)              │
│  │  - Token-generation lifecycle watch (#52)                │
│  │  - Stoppable UhidChannel via Os.poll (#48)               │
│  │  - Readiness Gate on START || OPEN (#49)                 │
│  └───────────┬───────────────────────┬──────────────────────┘
│              ▼                       ▼                      
│   Virtual HidMouse (Rel)   Virtual HidPen (Abs Stylus)      
└─────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Implementation Strategy |
|-----------|----------------|-------------------------|
| `ConnectionCoordinator` (#12) | Decides when to reconnect, route input, retry, or disconnect. | Pure Kotlin class, zero Android framework dependencies, fully unit-tested on JVM with mock-free tests. |
| `ConnectionService` | Android lifecycle glue, foreground notification, and window overlay manager. | Thin wrapper delegating all business decisions to `ConnectionCoordinator`. |
| `UhidChannel` (#48, #49, #50) | Communicates with `/dev/uhid` character device. | Bounded `Os.poll()` read loop that responds to stop signals without blocked reader thread leaks; dual `START || OPEN` exit condition. |
| `HidPen` | Emulates physical USB digitizer tablet with stylus tip and barrel switches. | Standard USB HID Digitizer Report Descriptor; scales absolute screen coordinates `(0..maxX, 0..maxY)`. |
| `SynergyCodec` (#47, #53) | Parses Synergy 3.7+ proprietary handshake and delta messages. | Decodes signed 16-bit `DMRM` motion frames and maps Mac Carbon keycodes to Linux evdev. |
| `ClientDeathWatcher` (#52) | Watches client binder tokens and cleans up virtual hardware if client crashes. | Stamped client generation counter ensuring stale death callbacks from replaced tokens do not destroy new active devices. |

## Data Flow & Event Processing

1. **Protocol Frame Ingestion:**
   `InputLeapConnection` reads TCP socket packets → feeds bytes to `ProtocolParser` → emits typed `InputLeapEvent` to Kotlin `SharedFlow`.
2. **Coordinator Routing Decision:**
   `ConnectionCoordinator` verifies the event generation matches the active session. If active and input enabled, routes to the designated injector.
3. **Hardware Dispatch:**
   - If `PointerDevice.MOUSE`: translates to relative deltas `(dx, dy)` → passes through `HidMouseState` clamping → writes to `/dev/uhid` virtual mouse.
   - If `PointerDevice.PEN`: translates to absolute coordinates `(x, y)` → builds digitizer report with tip/in-range flags → writes to `/dev/uhid` virtual pen.
   - If `PointerDevice.TOUCH`: falls back to `AccessibilityInputInjector` gesture dispatching.

---
*Architecture research: 2026-10-05*

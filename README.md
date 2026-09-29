# Input Leaf 🍃

**Use your PC's mouse and keyboard to control your Android phone or tablet over Wi-Fi/LAN.** Input Leaf is a free, open-source Android client for **Input Leap** and compatible **Deskflow** setups — no USB cables and no root required.

[![Latest Release](https://img.shields.io/github/v/release/anasvhora284/input-leaf?display_name=tag&sort=semver&style=flat&color=3DDC84)](https://github.com/anasvhora284/input-leaf/releases/latest)
[![GitHub Stars](https://img.shields.io/github/stars/anasvhora284/input-leaf?style=flat&color=blue)](https://github.com/anasvhora284/input-leaf/stargazers)
[![CI Status](https://img.shields.io/github/actions/workflow/status/anasvhora284/input-leaf/ci.yml?branch=master&style=flat)](https://github.com/anasvhora284/input-leaf/actions/workflows/ci.yml)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0%2B-brightgreen?logo=android&logoColor=white)](https://github.com/anasvhora284/input-leaf)
[![Root Status](https://img.shields.io/badge/Root-Not_Required-informational)](https://shizuku.rikka.app/)
[![License](https://img.shields.io/github/license/anasvhora284/input-leaf)](LICENSE)

**[Download APK](https://github.com/anasvhora284/input-leaf/releases/latest) · [Website](https://inputleaf.anasvhora.tech/) · [Quick Start](#quick-start) · [Input Leap Guide](docs/input-leap-android.md) · [Deskflow Guide](docs/deskflow-android.md) · [Troubleshooting](#troubleshooting) · [Issues](https://github.com/anasvhora284/input-leaf/issues)**

> PC mouse + keyboard -> Local Network (Wi-Fi/LAN) -> Android phone / tablet  
> Move your desktop cursor off the edge of your monitor to smoothly glide onto your Android screen.

---

## Overview

Input Leaf is an open-source Android client designed for software KVM switches. It connects seamlessly to **Input Leap** and compatible **Deskflow** servers running on your desktop or laptop (Windows, macOS, or Linux).

Instead of switching hands between your physical keyboard and phone touch screen, or constantly pairing Bluetooth devices, Input Leaf turns your phone or tablet into an additional screen in your desktop setup.

### Why Input Leaf?

- **No root required** — Works out-of-the-box using Shizuku or Android Accessibility APIs. Rooted devices can skip Shizuku and grant `su` for the same native HID path.
- **Ultra-low latency** — Shizuku engine injects native system-level input events directly for zero perceptible lag.
- **Full mouse and keyboard sharing** — Smooth cursor control, left/right/middle click, wheel scrolling, and hardware modifier keys (`Ctrl`, `Alt`, `Shift`, `Meta`).
- **Compatible with Input Leap & Deskflow** — Speaks the standard open-source KVM protocol over TCP.
- **100% local and private** — All communication stays on your local area network (LAN); no cloud relays or external servers.
- **mDNS server auto-discovery** — Finds compatible servers on your local Wi-Fi automatically.
- **Encrypted TLS connections** — Supports TLS encryption with Trust-On-First-Use (TOFU) certificate pinning.
- **Battery efficient and resilient** — Automatic exponential-backoff reconnects when network interruptions occur.
- **Material 3 Expressive design** — Dynamic color on Android 12+, light and dark themes, and edge-to-edge screens.

---

## Official Software & Lineage

Input Leaf acts as the **client** on your Android device. You will need a compatible **server** application running on your PC:

| Software | Role | Official Links | Description |
| :--- | :--- | :--- | :--- |
| **Input Leap** | Desktop Server | • [Official GitHub Repository](https://github.com/input-leap/input-leap)<br>• [Download Releases](https://github.com/input-leap/input-leap/releases)<br>• [Input Leap Wiki](https://github.com/input-leap/input-leap/wiki) | Free, open-source software KVM sharing mouse and keyboard between computers. Successor to Barrier. |
| **Deskflow** | Desktop Server | • [Official Website](https://deskflow.org/)<br>• [Official GitHub Repository](https://github.com/deskflow/deskflow)<br>• [Download Releases](https://github.com/deskflow/deskflow/releases) | Modern cross-platform keyboard and mouse sharing software. |
| **Barrier** | Predecessor | • [Official GitHub Repository](https://github.com/debauchee/barrier) | The upstream predecessor of Input Leap. Compatible with legacy setups. |
| **Shizuku** | Android Tool | • [Official Website](https://shizuku.rikka.app/)<br>• [GitHub Repository](https://github.com/RikkaApps/Shizuku) | Grants system-level API access to Input Leaf without root permissions. |

---

## How It Works

```text
┌──────────────────────────────────────────┐
│             Desktop / Laptop             │
│       (Windows / macOS / Linux)          │
│                                          │
│   Input Leap Server  /  Deskflow Server  │
└────────────────────┬─────────────────────┘
                     │
                     │  TCP Port 24800 (Wi-Fi / Ethernet LAN)
                     │  Optional TLS / mTLS Encryption
                     ▼
┌──────────────────────────────────────────┐
│          Android Phone / Tablet          │
│                                          │
│             Input Leaf App               │
│                    │                     │
│         ┌──────────┴──────────┐          │
│         ▼                     ▼          │
│   Shizuku or root       Accessibility    │
│  (System Injection)     (Stock Fallback) │
│  • Lowest latency     • Zero extra apps  │
│  • Full shortcuts     • Touch emulation  │
└──────────────────────────────────────────┘
```

When your mouse crosses the configured border of your desktop monitor, the server sends coordinates and keystrokes over your local network to Input Leaf, which translates and injects them into Android in real time.

---

## Quick Start

### 1. Install Server on your PC
Download and install either:
- **[Input Leap Releases](https://github.com/input-leap/input-leap/releases)** (Windows, macOS, Linux), or
- **[Deskflow Releases](https://github.com/deskflow/deskflow/releases)** (Windows, macOS, Linux).

### 2. Configure the Server Grid
1. In the server application, choose **Server (share this computer's mouse and keyboard)**.
2. Click **Configure Server...** to open the screen grid layout.
3. Drag a new screen from the top right into the grid and place it adjacent to your desktop screen (for example, to the right or below).
4. Double-click the new screen and set the **Screen name** to match your Android device's Screen Name (view or customize it in Input Leaf under **Settings -> Screen name**; it defaults to your sanitized device model, such as `pixel-8` or `android-phone`).
5. Click **OK** and press **Start**.

> **Detailed Guides:**
> - [Complete Input Leap on Android Setup Guide](docs/input-leap-android.md)
> - [Complete Deskflow on Android Setup Guide](docs/deskflow-android.md)

### 3. Install Input Leaf on Android
1. Download the latest `input-leaf_<version>_universal.apk` from [GitHub Releases](https://github.com/anasvhora284/input-leaf/releases/latest).
2. Open the APK on your Android device and tap **Install**.

### 4. Choose an Input Injection Engine
Open Input Leaf and complete the quick onboarding wizard:
- **Shizuku (Recommended on unrooted devices):** Open the [Shizuku app](https://shizuku.rikka.app/), start it via Wireless Debugging, and allow Input Leaf when prompted.
- **Root (rooted devices):** Grant the Magisk/KernelSU `su` prompt. You do not need Shizuku if root is granted.
- **Accessibility Service (Fallback):** Enable the Input Leaf Accessibility Service and Keyboard in Android **Settings -> Accessibility**. Native HID still needs Shizuku or root.

### 5. Connect
1. Input Leaf will automatically discover the server on your local Wi-Fi. You can also tap **+** to enter the PC's IP address manually.
2. Tap **Connect**.
3. Push your PC mouse cursor across the configured monitor edge — your cursor will appear on your Android screen.

---

## Shizuku vs. Accessibility Mode

Input Leaf offers two distinct input engines to match your device and preferences:

| Feature / Capability | Shizuku (Recommended) | Accessibility (No Extra App) |
| :--- | :---: | :---: |
| **Root Required** | No | No |
| **External Setup** | One-time Shizuku setup (Wireless Debugging) | None (Enable in Android Settings) |
| **Input Latency** | **Ultra-Low (< 5ms)** | Low (~15–25ms) |
| **Mouse Precision** | Native Absolute Cursor Mapping | Gesture / Pointer Emulation |
| **Mouse Buttons** | Left, Right, Middle Click | Left Click, Context Menus |
| **Scroll Wheel** | Smooth, continuous scrolling | Step scrolling |
| **Modifier Keys** | `Ctrl`, `Alt`, `Shift`, `Meta` (Super/Win) | Standard typing + Basic Modifiers |
| **System Shortcuts** | Full (`Alt+Tab`, `Meta+Enter`, Home, Back) | Limited by Android Accessibility APIs |
| **Best For** | Power users, daily workstation setups | Quick setups, restricted devices |

---

## Supported Android Shortcuts & Hotkeys

When using **Shizuku Mode**, Input Leaf passes through hardware-level keyboard shortcuts directly to Android:

| Desktop Key / Shortcut | Android Action |
| :--- | :--- |
| <kbd>Super</kbd> / <kbd>Windows</kbd> / <kbd>Cmd</kbd> | **Home Screen** |
| <kbd>Esc</kbd> or <kbd>Alt</kbd> + <kbd>←</kbd> | **Back Button** |
| <kbd>Alt</kbd> + <kbd>Tab</kbd> | **Recent Apps / App Switcher** |
| <kbd>Ctrl</kbd> + <kbd>C</kbd> / <kbd>Ctrl</kbd> + <kbd>V</kbd> | **Copy & Paste** |
| <kbd>Ctrl</kbd> + <kbd>A</kbd> | **Select All** |
| <kbd>Ctrl</kbd> + <kbd>Z</kbd> | **Undo** |
| <kbd>Print Screen</kbd> | **Take Screenshot** |
| <kbd>Volume Up</kbd> / <kbd>Down</kbd> / <kbd>Mute</kbd> | **Media Volume Controls** |
| <kbd>Media Play</kbd> / <kbd>Pause</kbd> / <kbd>Next</kbd> | **Media Playback Controls** |

---

## Screenshots

Material 3 Expressive UI with dynamic color (Android 12+) and a leaf-green fallback theme.

### Onboarding

<table align="center">
  <tr>
    <td align="center" width="33%">
      <img src="docs/screenshots/01_onboarding_welcome.jpg" width="220" alt="Onboarding: Welcome"><br>
      <b>Welcome</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/02_onboarding_input_method.jpg" width="220" alt="Onboarding: Choose an Input Method"><br>
      <b>Choose an Input Method</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/03_onboarding_accessibility.jpg" width="220" alt="Onboarding: Accessibility Fallback"><br>
      <b>Accessibility Fallback</b>
    </td>
  </tr>
</table>

### Home

<table align="center">
  <tr>
    <td align="center" width="33%">
      <img src="docs/screenshots/04_home_disconnected.jpg" width="220" alt="Home: Disconnected"><br>
      <b>Disconnected</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/05_home_connecting.jpg" width="220" alt="Home: Connecting"><br>
      <b>Connecting</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/06_home_connected.jpg" width="220" alt="Home: Connected & Ready"><br>
      <b>Connected & Ready</b>
    </td>
  </tr>
</table>

### Servers

<table align="center">
  <tr>
    <td align="center" width="33%">
      <img src="docs/screenshots/07_servers_discovered.jpg" width="220" alt="Servers: Discovered Servers"><br>
      <b>Discovered Servers</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/08_servers_favorite.jpg" width="220" alt="Servers: Favorites"><br>
      <b>Favorites</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/11_servers_connected.jpg" width="220" alt="Servers: Connected Server"><br>
      <b>Connected Server</b>
    </td>
  </tr>
  <tr>
    <td align="center" width="33%">
      <img src="docs/screenshots/09_servers_add_manual.jpg" width="220" alt="Servers: Add Manually"><br>
      <b>Add Manually</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/10_servers_trust_fingerprint.jpg" width="220" alt="Servers: Trust on First Use"><br>
      <b>Trust on First Use</b>
    </td>
  </tr>
</table>

### Permissions

<table align="center">
  <tr>
    <td align="center" width="33%">
      <img src="docs/screenshots/12_permissions_shizuku.jpg" width="220" alt="Permissions: Shizuku Ready"><br>
      <b>Shizuku Ready</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/13_permissions_root.jpg" width="220" alt="Permissions: Root Access"><br>
      <b>Root Access</b>
    </td>
  </tr>
</table>

### Settings

<table align="center">
  <tr>
    <td align="center" width="33%">
      <img src="docs/screenshots/14_settings_connection_display.jpg" width="220" alt="Settings: Connection & Display"><br>
      <b>Connection & Display</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/15_settings_security.jpg" width="220" alt="Settings: Security"><br>
      <b>Security</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/16_settings_about.jpg" width="220" alt="Settings: About & Community"><br>
      <b>About & Community</b>
    </td>
  </tr>
  <tr>
    <td align="center" width="33%">
      <img src="docs/screenshots/17_dialog_connection_security.jpg" width="220" alt="Settings: Connection Security"><br>
      <b>Connection Security</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/18_dialog_input_method.jpg" width="220" alt="Settings: Input Method"><br>
      <b>Input Method</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/19_dialog_cursor_style.jpg" width="220" alt="Settings: Cursor Style"><br>
      <b>Cursor Style</b>
    </td>
  </tr>
  <tr>
    <td align="center" width="33%">
      <img src="docs/screenshots/20_dialog_screen_name.jpg" width="220" alt="Settings: Screen Name"><br>
      <b>Screen Name</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/21_dialog_author.jpg" width="220" alt="Settings: Author Info"><br>
      <b>Author Info</b>
    </td>
  </tr>
</table>

### Light Theme

<table align="center">
  <tr>
    <td align="center" width="33%">
      <img src="docs/screenshots/22_light_home.jpg" width="220" alt="Light Theme: Home"><br>
      <b>Home</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/23_light_servers.jpg" width="220" alt="Light Theme: Servers"><br>
      <b>Servers</b>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/24_light_permissions.jpg" width="220" alt="Light Theme: Permissions"><br>
      <b>Permissions</b>
    </td>
  </tr>
  <tr>
    <td align="center" width="33%">
      <img src="docs/screenshots/25_light_settings.jpg" width="220" alt="Light Theme: Settings"><br>
      <b>Settings</b>
    </td>
  </tr>
</table>

---

## Troubleshooting

<details>
<summary><b>1. Input Leaf cannot find or connect to my PC server</b></summary>

- **Network Isolation:** Verify that both devices are on the same Wi-Fi network and that your router does not have "AP Isolation" or "Client Isolation" enabled.
- **Firewall Rules:** Ensure that the desktop server application has permission to accept inbound connections on **TCP Port 24800** in Windows Defender Firewall or `ufw`/`iptables` on Linux.
- **Screen Name Mismatch:** Double check that the **Screen Name** configured in your desktop server's grid matches the client name set in Input Leaf Settings (case-sensitive).
- **Direct IP Entry:** If mDNS auto-discovery fails, find your PC's local IP address (`ipconfig` on Windows or `ip a` on Linux) and add it manually in Input Leaf.
</details>

<details>
<summary><b>2. Keyboard works, but system shortcuts (Alt+Tab, Home) do nothing</b></summary>

- Android Accessibility Service mode does not have system-level permissions to trigger navigation hotkeys.
- Switch to **Shizuku Mode** for full hardware keyboard emulation and system shortcut support.
</details>

<details>
<summary><b>3. Cursor does not appear over notifications or Quick Settings panel</b></summary>

- Android's security architecture restricts standard overlay windows (`TYPE_APPLICATION_OVERLAY`) from rendering over system panels like Quick Settings, the notification shade, and the lockscreen.
- If you need pointer interactions in the notification shade, enable Input Leaf's **Accessibility Service**, which provides accessibility-level overlay coverage.
</details>

<details>
<summary><b>4. Connection drops when the screen locks or app is in the background</b></summary>

- Android OEMs often enforce aggressive battery-saving policies.
- In Android **Settings -> Apps -> Input Leaf -> Battery**, set battery usage to **Unrestricted** and allow background activity.
</details>

<details>
<summary><b>5. Shizuku says "Not Running" after restarting the phone</b></summary>

- On non-rooted devices, Android disables the Shizuku wireless debugging process upon reboot.
- Open the Shizuku app, tap **Pairing** or **Start** under Wireless Debugging to re-enable it.
</details>

---

## Frequently Asked Questions (FAQ)

<details>
<summary><b>Is Input Leaf completely free and open source?</b></summary>
Yes. Input Leaf is 100% free, ad-free, and licensed under the permissive Apache License 2.0.
</details>

<details>
<summary><b>Does Input Leaf send any data to external servers?</b></summary>
No. All mouse, keyboard, and handshake packets travel strictly between your PC and Android device across your local Wi-Fi or Ethernet network.
</details>

<details>
<summary><b>Can I connect multiple Android devices to the same PC?</b></summary>
Yes. You can add multiple screens in the Input Leap / Deskflow server grid (for example, one phone on the left, one tablet on the right) and connect each device running Input Leaf.
</details>

<details>
<summary><b>Which Android architectures are supported?</b></summary>
Releases provide universal APKs as well as targeted builds for `arm64-v8a`, `armeabi-v7a`, and `x86_64`. Universal works on all supported Android 8.0+ devices.
</details>

---

## Contributing

Contributions, feature suggestions, and bug reports are welcome:

1. Fork the repository on GitHub.
2. Create a feature branch (`git checkout -b feat/my-new-feature`).
3. Commit your changes with descriptive messages (`git commit -m "feat: add support for custom hotkey mapping"`).
4. Push to your branch (`git push origin feat/my-new-feature`).
5. Open a **[Pull Request](https://github.com/anasvhora284/input-leaf/pulls)**.

Check out our [Issue Tracker](https://github.com/anasvhora284/input-leaf/issues) or start a discussion on the [Community Forum](https://github.com/anasvhora284/input-leaf/discussions).

---

## Author & Acknowledgments

- **Author:** [Anas Vhora](https://anasvhora.tech) ([@anasvhora284](https://github.com/anasvhora284))
- Thanks to all **[Input Leaf contributors](https://github.com/anasvhora284/input-leaf/graphs/contributors)** for their valuable support and code contributions.
- Thanks to the upstream developers and contributors of [Input Leap](https://github.com/input-leap/input-leap), [Deskflow](https://github.com/deskflow/deskflow), [Barrier](https://github.com/debauchee/barrier), and [Shizuku](https://shizuku.rikka.app/).

---

## License

Input Leaf is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.

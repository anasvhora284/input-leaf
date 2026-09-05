# Input Leap on Android: Control an Android Phone with a PC Mouse and Keyboard

Input Leaf is an open-source Android client for [Input Leap](https://github.com/input-leap/input-leap). It lets an Android phone or tablet participate in an Input Leap software-KVM setup so a computer's mouse and keyboard can be used on Android over the local network.

## What you need

- A Windows, macOS, or Linux computer running Input Leap.
- An Android phone or tablet on the same local network.
- Input Leaf installed on Android.
- Either Shizuku or Android Accessibility mode for input injection.

## How the setup works

```text
PC / Laptop
Input Leap server
      │
      │  Wi-Fi / Ethernet LAN
      ▼
Android phone / tablet
Input Leaf client
```

Input Leap handles the shared mouse and keyboard session on the computer side. Input Leaf receives the input on Android and injects it using the selected Android input method.

## Setup

1. Install [Input Leap](https://github.com/input-leap/input-leap) on your computer.
2. Configure the Android device as a screen/client in Input Leap.
3. Install the [latest Input Leaf APK](https://github.com/anasvhora284/input-leaf/releases/latest).
4. Put the computer and Android device on the same LAN/Wi-Fi network.
5. Open Input Leaf and follow the setup wizard.
6. Choose Shizuku for the best system-level input experience, or Accessibility mode for a simpler no-extra-app setup.
7. Connect to the Input Leap server.
8. Move the computer cursor across the configured screen edge to switch control to Android.

## Shizuku or Accessibility?

**Shizuku** is recommended when you want the closest experience to a physical mouse and keyboard, including better support for system-level keyboard shortcuts.

**Accessibility mode** is useful when you do not want to install Shizuku. It works through Android Accessibility APIs and a virtual keyboard, but Android system shortcuts and some hardware-level behavior are more limited.

## Troubleshooting

### Android cannot discover the Input Leap server

Make sure both devices are on the same network, the Input Leap server is running, and the computer firewall allows the server connection. Input Leaf also supports manually entering a server address.

### Mouse works but keyboard shortcuts do not

Use Shizuku mode. Accessibility/virtual-keyboard mode cannot reproduce every hardware-level Android shortcut.

### The connection stops in the background

Some Android manufacturers aggressively restrict background applications. Allow Input Leaf to run in the background and disable battery optimization for the app when necessary.

## Download

[Download Input Leaf for Android](https://github.com/anasvhora284/input-leaf/releases/latest)

For the complete feature list and screenshots, see the [Input Leaf README](https://github.com/anasvhora284/input-leaf) or the [Input Leaf website](https://inputleaf.anasvhora.tech/).

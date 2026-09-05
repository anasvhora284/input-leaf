# Deskflow on Android: Android Client for Mouse and Keyboard Sharing

Input Leaf is an open-source Android client for the same style of software-KVM workflow used by Deskflow and Input Leap. It allows a compatible desktop server to send mouse and keyboard input to an Android phone or tablet over a local network.

## Use case

If you want to **use your computer mouse and keyboard on an Android phone**, a software KVM can be more convenient than switching to Bluetooth peripherals. Input Leaf acts as the Android endpoint while Deskflow runs on the computer.

```text
Computer + Deskflow
        │
        │ local network
        ▼
Android + Input Leaf
```

## Quick setup

1. Install [Deskflow](https://github.com/deskflow/deskflow) on the computer.
2. Configure the Android device as the appropriate screen/client.
3. Install [Input Leaf](https://github.com/anasvhora284/input-leaf/releases/latest) on Android.
4. Ensure the computer and Android device can communicate over the same LAN.
5. Complete Input Leaf's setup wizard.
6. Select Shizuku for the best input-injection experience, or Accessibility mode when Shizuku is not available.
7. Connect and move the computer cursor to the configured screen edge.

## Input method options

### Shizuku

Shizuku provides system-level input injection without requiring root on supported setups. It is the recommended option for low-latency input and system keyboard shortcuts.

### Accessibility

Accessibility mode avoids the need for a separate Shizuku installation. It is easier to start with, but Android's Accessibility and virtual-keyboard model limits some hardware-level shortcuts.

## Troubleshooting

- **Server not discovered:** verify LAN connectivity, firewall rules, server configuration, and the server address.
- **Keyboard shortcuts missing:** use Shizuku mode for better system-level shortcut support.
- **Background disconnects:** review Android battery optimization and background activity restrictions for Input Leaf.

## Download Input Leaf

[Get the latest Android APK](https://github.com/anasvhora284/input-leaf/releases/latest)

See the [main README](https://github.com/anasvhora284/input-leaf) for the full feature list, compatibility notes, screenshots, and troubleshooting information.

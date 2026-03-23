# InputLeaf Android v1.2.0 Release Notes

**Release Date:** March 23, 2026

## What's New

### Bug Fixes

#### Network Discovery Improvements
- **Fixed Wi-Fi network discovery** - Improved IP address detection using a prioritized hybrid approach:
  1. First tries Wi-Fi interfaces (`wlan0`, `wlan1`, `swlan0`, `ap0`, `eth0`)
  2. Falls back to WifiManager API for compatibility
  3. Last resort: any private network interface (excluding cellular)
- Added comprehensive logging for network interface debugging
- Properly filters out IPv6 addresses and cellular interfaces

#### Mouse/Keyboard Toggle Fix
- **Fixed mouse/keyboard enable/disable toggles** - The toggle switches in the Home screen now actually work!
  - When mouse is disabled: No mouse movement, clicks, or scroll events are processed; cursor overlay is hidden
  - When keyboard is disabled: No key events are processed
  - Changes take effect immediately without needing to reconnect

### Technical Improvements
- Added real-time preference observers in ConnectionService for mouse/keyboard states
- Improved input event filtering in the event loop
- Better cursor overlay management based on mouse enabled state

## Download

| Architecture | File | Size |
|--------------|------|------|
| Universal (all devices) | `input-leaf_1.2.0_universal.apk` | ~11.8 MB |
| ARM64 (most modern phones) | `input-leaf_1.2.0_arm64-v8a.apk` | ~11.8 MB |
| ARM32 (older phones) | `input-leaf_1.2.0_armeabi-v7a.apk` | ~11.8 MB |
| x86_64 (emulators, ChromeOS) | `input-leaf_1.2.0_x86_64.apk` | ~11.8 MB |
| x86 (older emulators) | `input-leaf_1.2.0_x86.apk` | ~11.8 MB |

**Recommended:** Use `input-leaf_1.2.0_universal.apk` if unsure which to download.

## Requirements
- Android 8.0 (API 26) or higher
- Shizuku app installed and running (for input injection)
- Same Wi-Fi network as your InputLeap/Barrier server

## Known Issues
- Server discovery requires port 24800 to be open on the server's firewall
- Self-signed certificate warning is expected (APK is signed with development key)

## Changelog from v1.1.0
- fix: Improve network discovery for both Wi-Fi and hotspot modes
- fix: Mouse/keyboard toggles now properly filter input events
- chore: Update version to 1.2.0
- chore: Add custom APK naming (input-leaf_version_arch.apk)

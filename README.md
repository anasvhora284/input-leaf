# Input Leaf

**Input Leaf** is an open-source Android client for [Input Leap](https://github.com/input-leap/input-leap) — a KVM software switch that lets you control your Android device using your PC or laptop's existing mouse and keyboard.

Simply move your cursor to the edge of your screen on your PC, and it seamlessly crosses over to your Android device — no extra hardware, no USB cables, just your local network.

> 🖱️ **PC/Laptop mouse & keyboard → controls your Android device**

---

## ⚠️ Important Dependencies

To use Input Leaf, you **must** have the following set up:

1. **[Input Leap](https://github.com/input-leap/input-leap)** — The server must be running on your PC or laptop. Input Leaf connects to it as a client over your local network.
2. **[Shizuku](https://shizuku.rikka.app/)** — Required to securely inject mouse and keyboard events into your Android device without needing root access.

---

## Features

- **Seamless Input Sharing** — Use your PC's mouse and keyboard to control your Android device, just like an extra monitor.
- **Modern UI/UX** — Beautiful Material interface with color-coded connection statuses.
- **Easy Toggling** — Quickly enable or disable input control on the fly.
- **Server Discovery** — Automatically scan your local network for active Input Leap servers.
- **Quick Favorites** — Save frequently used servers for one-tap connections from the home screen.
- **Guided Setup** — Built-in wizard for configuring Shizuku and the required system permissions.
- **No Root Required** — Works entirely through Shizuku, keeping your device secure.

---

## How It Works

```
[ PC / Laptop ]  ──── Local Network ────  [ Android Device ]
  Input Leap Server                          Input Leaf App
  (mouse + keyboard)          →          (receives input events)
```

1. Run Input Leap on your PC and add your Android device as a screen.
2. Install Input Leaf on your Android device and connect to the server.
3. Move your cursor past the screen edge on your PC — it jumps to your Android device.

---

## Screenshots

| | | |
|:---:|:---:|:---:|
| | ![Splash Screen](docs/screenshots/01_splash.jpg) | |
| ![Shizuku Setup](docs/screenshots/02_shizuku.jpg) | ![Overlay Permission](docs/screenshots/04_overlay.jpg) | ![Allow Background Activity](docs/screenshots/03_battery.jpg) |
| ![Home Screen](docs/screenshots/05_home.jpg) | ![Settings Screen](docs/screenshots/06_settings.jpg) | |

---

## Getting Started / Installation

Ready to build Input Leaf from source?

Check out the **[Installation & Build Wiki](https://github.com/anasvhora284/input-leaf/blob/master/docs/WIKI_INSTALL_GUIDE.md)** for step-by-step instructions on setting up your build environment (JDK, Gradle, ADB) and compiling the APK.

---

## Contributing

Contributions, issues, and feature requests are welcome! Feel free to open an issue or submit a pull request.

---

## License

This project is open-source. See the [LICENSE](LICENSE) file for details.

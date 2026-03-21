# Input Leaf

Input Leaf is an open-source Android extension of Input Leap (a KVM software switch). It allows you to share your mouse and keyboard and control your computer's input directly from your Android device.

## ⚠️ Important Dependencies

To use Input Leaf, you absolutely **must** have the following set up:
1. **[Input Leap](https://github.com/input-leap/input-leap)**: This Android application acts as a client/extension. You must have the Input Leap server running on your computer.
2. **[Shizuku](https://shizuku.rikka.app/)**: Required to securely inject mouse and keyboard events into your Android device without requiring root access.
## Features
- **Modern UI/UX**: Includes a beautiful material interface with color-coded connection statuses.
- **Easy Toggling**: Quickly enable or disable mouse and keyboard input on the fly.
- **Server Discovery**: Automatically scan your network for active Input Leap servers.
- **Quick Favorites**: Save recurring servers for one-tap connections from the home screen.
- **Guided Setup**: Built-in wizard for configuring Shizuku and required system permissions.

## Getting Started / Installation

Are you planning to compile and run Input Leaf from source on your Linux desktop?

Check out the **[Installation & Build Wiki](docs/WIKI_INSTALL_GUIDE.md)** for detailed step-by-step instructions on setting up your compiler (JDK, Gradle, ADB) and building the APK.

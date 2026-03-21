# 📖 Input Leaf Installation & Build Wiki (Linux)

This guide walks you through setting up your Linux development environment to build and run the Input Leaf Android application from source.

## 1. Prerequisites (JDK)

You need to install the **Java Development Kit (JDK) 17** to compile the Android project.

### On Ubuntu/Debian:
```bash
sudo apt update
sudo apt install openjdk-17-jdk -y
```

### On Fedora:
```bash
sudo dnf install java-17-openjdk-devel
```

### On Arch Linux:
```bash
sudo pacman -S jre17-openjdk jdk17-openjdk
```

Verify the installation:
```bash
java -version
```
*(Ensure the output shows "17.x.x")*

---

## 2. Installing Android Tools & ADB

You need the Android SDK tools (or Android Studio) to compile the app, as well as `adb` to easily install it on your Android phone.

**Install ADB (Android Debug Bridge):**
```bash
sudo apt install adb -y
```

*(Optional)* You can install Android Studio from [developer.android.com](https://developer.android.com/studio) which comes pre-bundled with the Android SDK, or install the command-line tools manually via `sdkmanager`.

---

## 3. Installing Gradle (Optional)

Input Leaf uses the **Gradle Wrapper (`gradlew`)**, meaning Gradle is bundled inside the project and you don't strictly *need* to install Gradle globally using your OS packager.

However, if you ever need it globally installed, we highly recommend using **SDKMAN!**:
```bash
# 1. Install SDKMAN
curl -s "https://get.sdkman.io" | bash

# 2. Open a new terminal or run:
source "$HOME/.sdkman/bin/sdkman-init.sh"

# 3. Install Gradle
sdk install gradle
```

---

## 4. Building the Application

Once your prerequisites are all set up:

1. Open your terminal and navigate to the root directory where you cloned the Input Leaf repository.
2. Make sure the Gradle wrapper is executable:
   ```bash
   chmod +x gradlew
   ```
3. Run the Gradle build command to assemble a Debug APK:
   ```bash
   ./gradlew clean assembleDebug
   ```
   > *Note: The first time you run this, Gradle will download several Android dependencies which may take a few minutes depending on your internet connection.*

**Where is the app?**
Once successful, the generated APK file is located in:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 5. Installing the App on Your Device

1. Enable **Developer Options** and **USB Debugging** on your Android device (inside phone Settings).
2. Connect your device to your computer via USB (or pair it using Wireless Debugging).
3. Verify your computer sees the device:
   ```bash
   adb devices
   ```
4. Install the outputted APK file using ADB:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

*Shortcut: You can optionally combine steps 4 and 5 by just typing `./gradlew installDebug` while your phone is plugged in.*

---

## 6. App Setup

When you launch "Input Leaf" for the first time, a streamlined Onboarding Wizard will guide you through granting the proper permissions.

You will need to run / activate **[Shizuku](https://shizuku.rikka.app/)** to allow the application to properly inject mouse and keyboard inputs onto your device framework securely.

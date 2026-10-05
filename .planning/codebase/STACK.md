# Technology Stack

**Analysis Date:** 2026-10-05

## Languages

**Primary:**
- Kotlin 2.4.10 - All Android application code, view models, network client, services, and tests (`app/src/main/java`, `app/src/test/java`, `app/src/androidTest/java`)

**Secondary:**
- AIDL (Android Interface Definition Language) - IPC interface definition for privileged input injection (`app/src/main/aidl/com/inputleaf/android/shizuku/IInputInjector.aidl`)
- Python 3.x - Code generation and CI utility scripts (`scripts/generate_keysym_unicode_table.py`, `scripts/codecov_skip_unprobed.py`)
- Bash / Shell - Build and CI execution scripts (`gradlew`, `.github/workflows/ci.yml`)

## Runtime

**Environment:**
- Android 8.0 (API level 26) through Android 16 (API level 37)
- Java Virtual Machine (JVM) Target: Java 17 (`JavaVersion.VERSION_17`)
- Android NDK / Linux kernel interface: `/dev/uhid` (kernel UHID character device for hardware mouse/keyboard/pen emulation)

**Package Manager:**
- Gradle with Gradle Wrapper (`gradle/wrapper/gradle-wrapper.properties`)
- Version Catalog: `gradle/libs.versions.toml`
- Dependency Repositories: Google Maven, Maven Central, JitPack

## Frameworks

**Core:**
- Android Jetpack - Modern Android architecture components
- Jetpack Compose (BOM `2026.08.00`, Material 3 `1.5.0-alpha29`) - Declarative reactive UI and Material 3 Expressive theming
- AndroidX DataStore (`1.2.1`) - Asynchronous key-value settings and preferences persistence
- Kotlinx Coroutines (`1.11.0`) - Asynchronous programming, structured concurrency, and reactive Flow pipelines
- Rikka Shizuku (`13.1.5`) - Inter-process communication to execute privileged system APIs with ADB permissions
- TopJohnWu libsu (`6.0.0`) - Root shell and privileged root service execution (`RootService`) for rooted environments

**Testing:**
- JUnit 4 (`4.13.2`) - Standard test runner for unit and instrumented tests
- Google Truth (`1.4.5`) - Fluent assertion library
- Mockito Core (`5.23.0`) - JVM mocking framework
- Robolectric (`4.14.1`) - Fast JVM-based Android framework unit testing
- Kotlinx Coroutines Test (`1.11.0`) - Coroutine testing utilities (`runTest`, `StandardTestDispatcher`)
- AndroidX Test Core / Ext JUnit / Runner / Rules (`1.7.0` / `1.3.0`) - Android testing support
- AndroidX Espresso (`3.7.0`) - UI instrumentation testing
- Jetpack Compose UI Test (`compose-ui-test-junit4`) - Declarative UI testing

**Build/Dev:**
- Android Gradle Plugin (AGP `9.4.0`) - Android build system
- Kotlin Compose Compiler Plugin (`2.4.10`) - Compose code generation
- Kotlinx Kover (`0.9.9`) - Code coverage measurement and XML/HTML report generation
- ProGuard / R8 (`app/proguard-rules.pro`) - Code shrinking and obfuscation rules (minification disabled in debug and release builds by default)

## Key Dependencies

**Critical:**
- `dev.rikka.shizuku:api` & `dev.rikka.shizuku:provider` (`13.1.5`) - Enables rootless privileged input event injection via Android system services
- `com.github.topjohnwu.libsu:core` & `com.github.topjohnwu.libsu:service` (`6.0.0`) - Executes privileged `RootInputInjectorService` for devices with Magisk/KernelSU
- `androidx.compose.material3:material3` (`1.5.0-alpha29`) - UI widgets, navigation, dialogs, and dynamic color styling
- `androidx.datastore:datastore-preferences` (`1.2.1`) - Thread-safe, reactive settings storage via DataStore
- `org.jetbrains.kotlinx:kotlinx-coroutines-android` (`1.11.0`) - Coroutine dispatchers for main thread and background socket I/O

**Infrastructure:**
- `androidx.core:core-ktx` (`1.19.0`) - Kotlin extensions for Android framework APIs
- `androidx.lifecycle:lifecycle-runtime-ktx` & `lifecycle-viewmodel-compose` (`2.11.0`) - ViewModel lifecycle integration with Compose
- `com.jaredrummler:android-device-names` (`2.1.1`) - Human-readable device model formatting for default client screen names
- `org.json:json` (`20240303`) - Real JSON parser for JVM unit testing without stubbing Android's `org.json`

## Configuration

**Environment:**
- Device system settings: `Settings.System.POINTER_SPEED`, battery optimization ignore requests
- App Preferences: DataStore file `input_leaf_preferences.preferences_pb` in app private storage
- Local Keystore: `app/input-leaf.jks` for debug/release APK signing

**Build:**
- `settings.gradle.kts` - Gradle project configuration and repository declarations
- `build.gradle.kts` - Root project build file and Kover report aggregation
- `app/build.gradle.kts` - App module configuration, SDK levels, ABI splits, dependencies, and packaging
- `gradle/libs.versions.toml` - Single source of truth for library versions and plugin coordinates
- `codecov.yml` - Code coverage reporting rules and exclusion masks

## Platform Requirements

**Development:**
- Linux, macOS, or Windows with JDK 17 (Temurin recommended)
- Android SDK Platform `android-37` (compileSdk 37, targetSdk 34, minSdk 26)
- Android SDK Build-Tools `36.0.0`
- Python 3.8+ (for utility scripts and CI coverage helper)

**Production:**
- Android 8.0 (Oreo / API 26) or higher
- Architectures supported: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` (Universal APK or ABI-split APKs)
- Network: Local Area Network (Wi-Fi or Ethernet LAN) with TCP connectivity to PC server on port 24800

---

*Stack analysis: 2026-10-05*
*Update after major dependency changes*

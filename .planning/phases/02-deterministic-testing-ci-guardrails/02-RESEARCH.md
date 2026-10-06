# Phase 2: Deterministic Testing & CI Guardrails - Research

**Phase**: 02-deterministic-testing-ci-guardrails  
**Date**: 2026-10-06  
**Status**: Completed  
**Traceability**: Issues #13, #51; Requirements TEST-01, TEST-02, TEST-03

---

## 1. Executive Summary

Phase 2 focuses on eliminating test flakiness, enforcing deterministic execution across JVM suites, introducing an isolated smoke test pipeline without real hardware/network dependencies, and locking in 100% patch test coverage for core UHID and protocol modules.

### Key Pain Points & Mandates
1. **Flaky Wall-Clock Timing (#51, TEST-01)**:
   - `InputInjectorServiceEnterTest.kt` re-introduced background thread sleep (`Thread.sleep(25)`) and wall-clock assertions (`System.nanoTime()`), violating `docs/TESTING.md`.
   - `HidAttachmentControllerTest.kt` relied on `Thread.sleep(40)` and `delay(10)` to test race conditions between attach and detach.
   - `UhidChannelReadinessTest.kt` had a minor residual `Thread.sleep(1)` inside `TestClock.sleep`.
2. **Missing Isolated Android Smoke Test Pipeline (#13, TEST-02)**:
   - Instrumented testing currently requires an emulator and previously launched `MainActivity` with real network scanning and service checks.
   - An explicit, lightweight dependency provider pattern (`AppDependencies.kt`, `TestAppDependencies.kt`, `MainViewModelFactory.kt`) is needed to allow `MainActivity` and `MainViewModel` to run deterministically in CI without hardware or LAN scanning.
   - A dedicated workflow `.github/workflows/android-smoke.yml` must trigger on `workflow_dispatch`, `schedule` (nightly), and `release` events.
3. **Coverage & CI Enforcement (#13, #51, TEST-03)**:
   - `codecov.yml` previously ignored `app/src/main/java/com/inputleaf/android/shizuku/**`, which unintentionally excluded UHID drivers from patch coverage rules.
   - Kover verification rules (`koverVerifyDebugJvm`) need to be wired into Gradle build configuration and CI pipeline to guard core protocol and UHID drivers.

---

## 2. Technical Findings & Deep Dives

### 2.1 Deterministic Clock Injection in Unit Tests (TEST-01)

#### `InputInjectorServiceEnterTest.kt`
- **Location**: Lines 44-53.
- **Problem**:
  ```kotlin
  thread(isDaemon = true) {
      Thread.sleep(25)
      kernel.write(typeWord(UhidProtocol.UHID_OPEN))
      kernel.flush()
  }
  val startedAt = System.nanoTime()
  assertThat(service.openVirtualMouse()).isTrue()
  val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
  assertThat(elapsedMs).isLessThan(800)
  ```
- **Solution**:
  Like `UhidChannelReadinessTest.kt`, `UhidChannel` already accepts `UhidReadinessConfig(sleeper = clock::sleep, nanoTime = clock::nanoTime)`.
  Pre-write `UHID_OPEN` into the `PipedOutputStream` before calling `service.openVirtualMouse()`.
  Use an explicit `TestClock` to record and assert virtual elapsed time.
  Completely eliminate the feeder thread and wall-clock timer.

#### `HidAttachmentControllerTest.kt`
- **Location**: Lines 18-34.
- **Problem**:
  ```kotlin
  val first = async {
      gate.applyLatest { wanted ->
          if (wanted && applied.isEmpty()) {
              Thread.sleep(40)
          }
          applied += wanted
      }
  }
  delay(10)
  gate.setWanted(false)
  gate.setWanted(true)
  val second = async {
      gate.applyLatest { wanted -> applied += wanted }
  }
  first.await()
  second.await()
  ```
- **Solution**:
  Use `CompletableDeferred<Unit>()` signals (`firstStarted`, `releaseFirst`) inside a `runTest` scope.
  When the first job enters its `applyLatest` block:
  1. `firstStarted.complete(Unit)` signals to the test body that the first lock/execution is in progress.
  2. The first job awaits `releaseFirst.await()`.
  3. The test body awaits `firstStarted.await()`, modifies `gate.setWanted(false)` then `gate.setWanted(true)`, dispatches `second`, and then calls `releaseFirst.complete(Unit)`.
  4. Both jobs complete deterministically with zero sleeps or arbitrary delays.

#### `UhidChannelReadinessTest.kt`
- Replace `Thread.sleep(1)` in `TestClock.sleep` with `Thread.yield()` so the pipe-draining thread executes without introducing wall-clock dependency.

---

### 2.2 Dependency Provider Architecture (#13, TEST-02)

To satisfy Issue #13's strict constraints:
- Do not introduce general DI frameworks like Hilt/Dagger (explicitly ruled out in `REQUIREMENTS.md` Out of Scope).
- Introduce a lightweight dependency provider in `com.inputleaf.android.ui`:
  1. `AppDependencies.kt`:
     - Defines an interface `AppDependencies` with factories/accessors for:
       - `serverScanner: ServerScanner`
       - `preferences: AppPreferences`
       - `certificateStore: ClientCertificateStore`
       - `permissionProvider: PermissionStatusProvider`
       - `connectionService: ConnectionService?`
     - Holds a companion object or global provider reference `var current: AppDependencies = DefaultAppDependencies(app)`.
     - Supports `AppDependencies.reset()` or scoped overrides for instrumentation tests.
  2. `MainViewModelFactory.kt`:
     - Implements `ViewModelProvider.Factory`.
     - Injects dependencies from `AppDependencies.current` into `MainViewModel`.
  3. `MainViewModel.kt`:
     - Refactored constructor to receive dependencies directly (with a default fallback constructor for production backwards-compatibility).
  4. `TestAppDependencies.kt` (in `androidTest`):
     - Provides fakes with no-op scanning (`emptyFlow()`), fake permissions (all granted or configurable), and no real service binding.
  5. `MainActivitySmokeTest.kt`:
     - Sets `AppDependencies.current = TestAppDependencies(context)`.
     - Runs cleanly on emulators without network access or privileged daemons.

---

### 2.3 Smoke Test Workflow (`.github/workflows/android-smoke.yml`)

Create `.github/workflows/android-smoke.yml` matching the exact specifications in Issue #13:
- Triggers:
  ```yaml
  name: Android Smoke Test
  on:
    workflow_dispatch:
    schedule:
      - cron: '0 3 * * *' # Nightly at 03:00 UTC
    release:
      types: [prereleased, released]
  ```
- Workflow structure:
  - Check out code.
  - Set up JDK 17 & Android SDK.
  - Set up Gradle.
  - Generate CI keystore.
  - Run reactivecircus emulator runner with API 36 x86_64 emulator.
  - Execute `./gradlew :app:connectedDebugAndroidTest`.
  - Upload test report artifacts on failure.

---

### 2.4 Coverage Guardrails & Codecov Config (TEST-03)

#### Codecov Configuration (`codecov.yml`)
- Current problem:
  ```yaml
  ignore:
    - "app/src/main/java/com/inputleaf/android/shizuku/**"
  ```
  This ignores the entire `shizuku` package, including `shizuku/uhid/`.
- Fix:
  Only ignore IPC service layers that require live Shizuku/root daemons on real hardware:
  ```yaml
  ignore:
    - "app/src/main/java/com/inputleaf/android/ui/**"
    - "app/src/main/java/com/inputleaf/android/service/**"
    - "app/src/main/java/com/inputleaf/android/shizuku/InputInjectorService.kt"
    - "app/src/main/java/com/inputleaf/android/shizuku/ShizukuInputInjector.kt"
    - "app/src/main/java/com/inputleaf/android/shizuku/InputInjectorProcess.kt"
    - "app/src/main/java/com/inputleaf/android/shizuku/ShizukuAvailability.kt"
  ```
  `app/src/main/java/com/inputleaf/android/shizuku/uhid/**` is explicitly kept subject to the 100% patch coverage target.

#### Kover Verification Rules (`build.gradle.kts`)
- Add verification rule under `kover.reports.variant("debugJvm")`:
  ```kotlin
  verify {
      rule("UHID and Protocol Coverage") {
          bound {
              minValue = 80
              coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.INSTRUCTION
              aggregationForGroup = kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
          }
      }
  }
  ```
- Wire `:koverVerifyDebugJvm` into `.github/workflows/ci.yml`.

---

## 3. Verification Plan

1. **Deterministic Testing**:
   - Run `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.InputInjectorServiceEnterTest"`.
   - Run `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.service.HidAttachmentControllerTest"`.
   - Run `./gradlew :app:testDebugUnitTest --tests "com.inputleaf.android.shizuku.uhid.UhidChannelReadinessTest"`.
   - Verify 0 instances of `Thread.sleep` in `app/src/test/java/`.
2. **Smoke Test & Dependencies**:
   - Compile both debug and debugAndroidTest: `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`.
   - Verify workflow syntax with yamllint or python schema validator.
3. **Coverage Guardrails**:
   - Run `./gradlew :koverVerifyDebugJvm`.
   - Verify `codecov.yml` unignores `uhid/**`.

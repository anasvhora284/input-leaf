# Testing

Input Leaf uses a small, fast JVM test suite as the main feedback loop for local development and pull requests. Android emulator tests and coverage enforcement will be added separately so they do not slow down this required check.

## Requirements

- JDK 17
- Android SDK platform 34 and build tools 34.0.0
- The checked-in Gradle Wrapper (`./gradlew`)

## Run the fast suite

From the repository root, run:

```sh
./gradlew :app:testDebugUnitTest :uhid-server:test
```

The same command runs in the `fast-jvm` GitHub Actions job. The app task runs local Android JVM tests. The UHID task runs plain Java JVM tests.

## Test locations and conventions

### Android app

Place app JVM tests under:

```text
app/src/test/java/com/inputleaf/android/<feature>/
```

Mirror the production package, name classes after the subject with a `Test` suffix, and follow the existing JUnit 4, Truth, and behavior-oriented naming conventions. Put deterministic file fixtures in `app/src/test/resources/`.

### UHID server

Place UHID JVM tests under:

```text
uhid-server/src/test/java/com/inputleaf/uhid/
```

The UHID module is Java-only. Keep its tests in Java so test coverage does not add the Kotlin plugin or runtime to the generated JAR and DEX pipeline. Use JUnit 4 and Truth.

Socket lifecycle paths in `UhidServer.run()` are not covered by JVM unit tests because `android.net.LocalServerSocket` and `LocalSocket` come from a compile-only Android stub that throws at runtime. Keep explicit `finally` cleanup around both sockets rather than adding brittle stub-dependent tests.

## Test design principles

- Test observable results, emitted events, persisted values, errors, and protocol bytes rather than private methods or collaborator call order.
- Prefer pure JVM tests over emulator tests when Android behavior is not the subject of the test.
- Control time, asynchronous work, network responses, and fixture data so tests are deterministic.
- Use byte streams, loopback sockets, and small fakes instead of external services, LAN devices, privileged APIs, or physical hardware.
- Give each test independent state and explicit cleanup.
- Do not add retries or arbitrary sleeps to hide flaky behavior.
- Keep fixtures local to a test unless sharing clearly reduces duplication.

## Suite boundaries

The required pull-request suite must not depend on:

- an Android emulator or connected device;
- a Deskflow installation or external server;
- LAN or Internet access during tests;
- Shizuku, accessibility, IME, or `/dev/uhid` access;
- APK signing or release secrets.

A small emulator smoke suite and coverage guardrails are planned as later, separate work. Lint and formatting are not part of the required test command because the repository does not currently configure dedicated formatting or static-analysis tooling.

## Current baseline

The initial baseline was verified with JDK 17 and Android SDK 34 when the fast CI workflow was introduced:

- `:app:testDebugUnitTest` passes and runs the app’s Kotlin behavior tests.
- `:uhid-server:test` passes and runs the UHID module’s Java behavior tests.

Before making changes, run the complete fast suite and treat failures as real regressions or document them explicitly. Do not skip, mute, or retry failing tests merely to produce a green build. GitHub Actions retains available test reports when the `fast-jvm` job fails.

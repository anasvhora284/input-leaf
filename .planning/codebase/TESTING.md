# Testing Patterns

**Analysis Date:** 2026-10-05

## Test Framework

**Runner:**
- JUnit 4 (`junit:junit:4.13.2`) as primary test runner
- Robolectric (`org.robolectric:robolectric:4.14.1`) for tests requiring Android context or system resources on JVM
- AndroidJUnitRunner (`androidx.test.runner.AndroidJUnitRunner`) for on-device instrumentation tests

**Assertion Library:**
- Google Truth (`com.google.truth:truth:1.4.5`) - Fluent assertions (`assertThat(...)`)
- Mockito Core (`org.mockito:mockito-core:5.23.0`) - JVM mocking

**Coverage Engine:**
- Kotlinx Kover (`0.9.9`) - Configured in `app/build.gradle.kts` with `debugJvm` variant
- CI Coverage Script: `scripts/codecov_skip_unprobed.py` adjusts unprobed bytecode lines before Codecov upload

**Run Commands:**
```bash
# Run all JVM unit tests
./gradlew testDebugUnitTest

# Run a single unit test class
./gradlew testDebugUnitTest --tests "com.inputleaf.android.protocol.ProtocolParserTest"

# Run a specific test method
./gradlew testDebugUnitTest --tests "com.inputleaf.android.protocol.ProtocolParserTest.parseMouseMove"

# Run unit tests and generate Kover XML coverage report
./gradlew testDebugUnitTest :koverXmlReportDebugJvm

# Run connected Android instrumentation tests (requires running emulator/device)
./gradlew connectedDebugAndroidTest
```

## Test File Organization

**Location:**
- JVM Unit Tests: `app/src/test/java/com/inputleaf/android/`
- Test Fixtures & Mocks: `app/src/test/java/com/inputleaf/android/testutil/`
- Test Resources (Certificates & KeyStores): `app/src/test/resources/`
- Instrumented Android Tests: `app/src/androidTest/java/com/inputleaf/android/`

**Naming:**
- Unit test files end with `Test.kt` (e.g., `ProtocolParserTest.kt`, `HidMouseTest.kt`, `ConnectionStateMachineTest.kt`)
- Shared fixtures end with `Fixture.kt` or `TestUtil.kt` (e.g., `ClientCertificateTestFixture.kt`, `LoopbackServer.kt`)

## Test Structure

**Suite Organization:**
```kotlin
package com.inputleaf.android.protocol

import com.google.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class ProtocolParserTest {

    private lateinit var parser: ProtocolParser

    @Before
    fun setUp() {
        parser = ProtocolParser()
    }

    @Test
    fun parseMouseMove_validCoordinates_emitsEvent() {
        // Arrange
        val payload = byteArrayOf(...)
        
        // Act
        val event = parser.parse(payload)
        
        // Assert
        assertThat(event).isInstanceOf(InputLeapEvent.MouseMove::class.java)
        val mouseMove = event as InputLeapEvent.MouseMove
        assertThat(mouseMove.x).isEqualTo(100)
        assertThat(mouseMove.y).isEqualTo(200)
    }
}
```

**Coroutine Testing Patterns:**
- Use `kotlinx.coroutines.test.runTest` for testing suspending functions and StateFlow emissions:
```kotlin
@Test
fun connection_handshake_emitsConnectedState() = runTest {
    val connection = InputLeapConnection(...)
    // Advance virtual time if delay() is called
    // Assert on flow emissions
}
```

**Network & Integration Testing (`LoopbackServer`):**
- Real TCP and TLS interactions are tested entirely on the JVM using `LoopbackServer.kt` (`app/src/test/java/com/inputleaf/android/testutil/LoopbackServer.kt`).
- It binds to an ephemeral local port (`127.0.0.1`), initializes a mock TLS server keystore (`test_tls_server.p12`), and verifies:
  - Mutual TLS client certificate validation (`MutualTlsLoopbackTest.kt`)
  - Fallback from TLS failure to plain TCP (`PlainServerTlsErrorTest.kt`)
  - Real byte-level handshake packets and response decoding

**Mocking Guidelines:**
- Prefer real objects and test doubles over heavy Mockito mocks where possible.
- Use `LoopbackServer` for network rather than mocking `Socket`.
- For Android components requiring context (SharedPreferences, Resources), use Robolectric:
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppPreferencesTest {
    // Uses ApplicationProvider.getApplicationContext()
}
```

---

*Testing analysis: 2026-10-05*
*Update after major test framework changes*

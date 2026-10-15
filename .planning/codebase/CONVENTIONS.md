# Coding Conventions

**Analysis Date:** 2026-10-05

## Naming Patterns

**Files:**
- Source files: `PascalCase.kt` matching the primary class or interface name (e.g., `InputLeapConnection.kt`, `ConnectionService.kt`, `HidMouse.kt`)
- Composables: `PascalCase.kt` matching the root UI composable (e.g., `MainScreen.kt`, `ServerListScreen.kt`)
- AIDL files: `PascalCase.aidl` matching the interface name (e.g., `IInputInjector.aidl`)
- Unit test files: `PascalCaseTest.kt` placed in parallel test package mirrors (e.g., `ProtocolParserTest.kt`, `HidMouseTest.kt`)
- Build scripts: `kebab-case` or Gradle convention (`build.gradle.kts`, `settings.gradle.kts`, `libs.versions.toml`)

**Functions:**
- Standard functions: `camelCase` (e.g., `connect()`, `injectHidKey()`, `applyPointerSpeed()`)
- Compose `@Composable` functions: `PascalCase` (e.g., `MainScreen()`, `FeatureToggleCard()`, `SettingsRow()`)
- Flow and State accessors: `camelCase` with backing private property prefixed with underscore (e.g., `private val _events = MutableSharedFlow...`, `val events: SharedFlow = _events`)

**Variables & Constants:**
- Variables: `camelCase` (e.g., `screenWidth`, `currentMouseX`, `connectJob`)
- Constants: `UPPER_SNAKE_CASE` (e.g., `TAG`, `KEEPALIVE_POLL_MS`, `HANDSHAKE_READ_TIMEOUT_MS`)
- Private constants: Defined at file level with `private const val`

**Types:**
- Classes and Interfaces: `PascalCase` (e.g., `InputInjector`, `ServerScanner`)
- Sealed Classes / Interfaces: `PascalCase` representing domain state hierarchies (e.g., `ConnectionState`, `InputLeapEvent`)
- Enums: `PascalCase` for enum name, `UPPER_SNAKE_CASE` for entries (e.g., `PrivilegeKind.SHIZUKU`, `ConnectionTransportPolicy.AUTO`)

## Code Style

**Formatting:**
- Indentation: 4 spaces (no tabs)
- Kotlin style: Official Kotlin Coding Conventions
- Line length: Soft limit of 100-120 characters
- Trailing commas: Used in multi-line parameter lists and arguments
- Braces: Standard 1TBS (opening brace on same line)

**Null Safety & Defensive Coding:**
- No unsafe `!!` operators: Prefer `requireNotNull()`, `checkNotNull()`, or safe-calls `?.let { ... }`
- Avoid `lateinit var` where possible: Used primarily for Android lifecycle injections (e.g., `prefs: AppPreferences` in `Service.onCreate`)
- Strict explicit types at public API boundaries

**Concurrency & Coroutines:**
- Structured Concurrency: Never use `GlobalScope`. Always bind jobs to a lifecycle-managed `CoroutineScope` with a `SupervisorJob()`
- Dispatchers:
  - `Dispatchers.Main` for UI state updates and Android service bindings
  - `Dispatchers.IO` for network socket reads, TLS handshakes, and file/storage access
- Thread confinement: State flow mutations dispatched on intended thread; atomic/volatile markers used for cross-thread variables (e.g., `@Volatile private var screenWidth = 0`)

**Error Handling:**
- Defensive logging with `runCatching { Log.d(TAG, message) }` to prevent crashes in unit tests running on JVM without Robolectric
- Specific exception handling over generic catch blocks: catch `SSLException`, `SocketTimeoutException`, `IOException`
- Resource cleanup: Always ensure sockets, file descriptors, and jobs are closed/cancelled in `finally` blocks or via `.use { ... }`

## Import Organization

**Order:**
1. Android framework packages (`android.*`)
2. AndroidX libraries (`androidx.*`)
3. Project domain & internal packages (`com.inputleaf.android.*`)
4. Third-party libraries (`rikka.shizuku.*`, `com.topjohnwu.*`)
5. Kotlin standard libraries and coroutines (`kotlin.*`, `kotlinx.coroutines.*`)
6. Java standard library (`java.*`, `javax.*`)

**Rules:**
- No wildcard imports (`import foo.*` is discouraged)
- Separate distinct import packages with blank lines

---

*Conventions analysis: 2026-10-05*
*Update after major style guide changes*

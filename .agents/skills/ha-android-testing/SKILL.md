---
name: ha-android-testing
description: Home Assistant Android testing guidance. Use when writing or reviewing unit tests, Robolectric tests, Flow tests with Turbine, screenshot tests, fakes from testing-unit, or module-wide test rules.
---

# HA Android Testing

Use this skill when writing, changing, or reviewing tests.

```bash
./gradlew test                          # Unit tests (:common:test for one module)
./gradlew validateDebugScreenshotTest   # Screenshot tests
```

After an intentional UI change, update the reference screenshots (stored under `src/screenshotTestFullDebug/reference` in `:app`, `src/screenshotTestDebug/reference` in `:common` and `:wear`) with `./gradlew updateDebugScreenshotTest updateFullDebugScreenshotTest`. Rendering differs subtly between hosts, so if CI still fails on thresholds, a maintainer triggers the `Update Screenshots` workflow to regenerate them on the CI host — don't chase pixel diffs locally.

Known local false positive: `ServerDiscoveryScreenshotTest` (onboarding server discovery screen) fails local validation because of host rendering differences. Running validate and update locally is fine — just ignore this test's failures (CI is the source of truth), and after an update run, revert its regenerated reference images instead of including them in the change, unless the screen intentionally changed.

## Frameworks

- JUnit Jupiter for unit tests; JUnit 4 only when Robolectric requires it.
- MockK for mocking — but prefer real objects or fakes when you can.
- Robolectric for Android APIs; prefer it over instrumentation tests. Instrumentation tests are a last resort or for verifying system behavior across API levels.
- Every Robolectric test class needs both annotations, otherwise Robolectric boots the real `HomeAssistantApplication`, enabling StrictMode and FailFast and leaking process-wide state that can crash the test JVM:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MyTest { ... }
```

## Module-Wide Test Rules

Rules that must apply to every test in a module are JUnit Platform `TestExecutionListener`s registered through `ServiceLoader` (`src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener`). They run for both JUnit 4 (Vintage) and Jupiter tests — use this mechanism instead of per-class setup when introducing a new cross-cutting rule:

- `TestStateResetPlatformListener` (per test module) resets process-wide singletons before every test: it installs a FailFast handler that rethrows as `AssertionError`, so any FailFast trigger surfaces as a test failure instead of crashing the JVM, and resets `SdkVersion`.
- `ConsoleLogPlatformListener` (from `:testing-unit`) plants a Timber tree that prints to stderr so logs are visible during tests.

## Shared Test Utilities: `:testing-unit`

Code needed by tests in several modules goes in `:testing-unit` (which must stay independent from `:common`). Check it before writing a new helper. It provides among others:

- Main dispatcher swap — replaces `Dispatchers.Main` with a `TestDispatcher`. Pick the helper matching the test's framework: `MainDispatcherJUnit5Extension` for JUnit Jupiter, applied with `@ExtendWith(MainDispatcherJUnit5Extension::class)` on the class (or `@JvmField @RegisterExtension val ext = MainDispatcherJUnit5Extension()` on a field); `MainDispatcherJUnit4Rule` for JUnit 4 / Robolectric, applied with `@get:Rule val mainDispatcherRule = MainDispatcherJUnit4Rule()`. Both default to a `StandardTestDispatcher`. Apply the swap **only** when the code under test actually runs on the Main dispatcher, typically because it launches on `viewModelScope` (which uses `Dispatchers.Main.immediate`). Without it those tests throw "Module with the Main dispatcher had failed to initialize" since there is no Android main looper on the JVM. Don't add it to tests that never touch Main (a plain repository, use case, or pure suspend function) — it's noise there. Use the default `StandardTestDispatcher`; do not use `UnconfinedTestDispatcher`. Reaching for it to make a test pass hides ordering the test should assert explicitly (advance the scheduler with `runTest`/`advanceUntilIdle`), and needing it usually signals a design problem in the code under test — fix that instead.
- `FakeClock` — controllable `kotlin.time.Clock`.
- `TestSharedFlow` — non-suspending `SharedFlow` test double that avoids cross-scheduler deadlocks.
- `stringResource(...)` on `AndroidComposeTestRule`, `seedFakeAndroidId()`, and fakes for Wear OS clients.

## Flows: Turbine

Turbine is available in all modules and must be used for testing Flows:

- Use `turbineScope` with `testIn` for multi-collector tests; assert with `awaitItem`/`awaitComplete`/`expectNoEvents`.
- Never synchronize on Flow emissions with `CountDownLatch`, `Thread.sleep`, `verify(timeout = ...)`, or raw `launch`/`async`.
- Flows wrapped with `shareIn` never complete — use `expectNoEvents()` + `cancelAndConsumeRemainingEvents()` instead of `awaitComplete()`.

## Conventions

- Tests mirror the source structure in `src/test/kotlin/`.
- Name tests with GIVEN-WHEN-THEN sentences:

```kotlin
@Test
fun `Given user authenticated when opening app then show dashboard`() { ... }
```

- Test public interfaces and behavior, not implementation details. All public APIs and business logic should have unit tests.
- Never widen visibility or use reflection just for a test: don't expose internal functions to test them — test through the public entry point (`onCreate`, the ViewModel API). When access is truly unavoidable, use `@VisibleForTesting` (for example a secondary constructor taking a `CoroutineScope` or `Clock`).
- Never use `Thread.sleep` in tests — it makes them slow and flaky. Run coroutines under `runTest` so the `TestDispatcher` fakes time.
- In Jupiter, use `@ParameterizedTest` when tests repeat with only a value change; in JUnit 4, extract a private function and call it from separately named tests. Merge near-duplicate single-assertion tests into one meaningful test, and add a small helper in the test file for repeated setup.
- Keep all of a feature's tests in one class, even when mixing Robolectric-dependent and plain unit tests.
- Screens are tested in isolation: Compose interaction tests verify each interaction invokes the right callback and that elements show/hide per state (see `TagReaderScreenTest`); prefer matching on visible text over test tags. Screenshot tests cover looks only, never logic — and must render the real composable (not a simplified stand-in) across its meaningful states (loading, empty, error, multi-server). Navigation tests must cover back and forward stack behavior.
- Test concurrency with `TestDispatcher` — see the `ha-android-concurrency` skill.

# Home Assistant Companion for Android Agent Guide

You are helping develop the Home Assistant Companion for Android, the official Android app for the Home Assistant home automation platform. The app centers around a WebView for Home Assistant's frontend, enhanced with native features like background sensors, notifications, widgets, Wear OS, and Android Automotive. The project is entirely written in Kotlin and must stay that way.

Detailed developer documentation lives at https://developers.home-assistant.io/docs/android (sources at https://github.com/home-assistant/developers.home-assistant).

## Essential Commands

```bash
./gradlew assembleDebug                                    # Debug build (full and minimal flavors)
./gradlew test                                             # Unit tests (:common:test for one module)
./gradlew :build-logic:convention:ktlintFormat ktlintFormat  # Format code, run before committing
./gradlew ktlintCheck :build-logic:convention:ktlintCheck --continue  # Check code style
./gradlew lint --continue                                  # Android linter
./gradlew validateDebugScreenshotTest                      # Screenshot tests
./gradlew alldependencies --write-locks                    # Update dependency locks after any dependency change
```

## Architecture

- Gradle modules: `:app` (mobile), `:automotive` (reuses `:app` sources), `:wear`, `:common` (shared data layer, sensors, WebSocket, REST API), `:testing-unit` (shared test utilities, independent from `:common`), `:lint` (custom lint rules), and `build-logic` (convention plugins via `includeBuild`).
- `:app` and `:automotive` have two flavors: `full` (Google Play Services) and `minimal` (FOSS). Keep code in the `main` source set flavor-agnostic whenever possible.
- Key technologies: Jetpack Compose (all new UI), Hilt (DI), Kotlin Coroutines and Flow (all concurrency), Room, Retrofit + OkHttp, Kotlinx.serialization, Timber (logging).
- Source lives under `src/main/kotlin/io/homeassistant/companion/android/`, packaged by feature (`sensors/`, `notifications/`, `settings/`, `onboarding/`, `widgets/`, `frontend/`, ...). `frontend/` hosts the frontend screen (the WebView rendering the Home Assistant frontend) and the external bus that communicates with it; `webview/` is the legacy implementation being removed.
- Min SDK versions are defined in `gradle/libs.versions.toml` (`androidSdk-min`, `androidSdk-automotive-min`, `androidSdk-wear-min`).

## Development Standards

- Kotlin only, all code and comments in English.
- Use named constants instead of magic numbers or strings, and sealed classes/interfaces instead of strings or enums for logic control.
- Use immutable data classes; produce changes through `copy()` or functions returning new instances.
- Keep functions small (under 50 lines) with meaningful names; default to `private`/`internal` visibility.
- Document every public function, focusing on functionality rather than implementation details.
- All displayed strings go in the value files in `:common`, English only, accessed via `stringResource`.
- Gate new features on the server version, for example `serverManager.getServer()?.version?.isAtLeast(2025, 6, 0) == true`.
- Never use `System.currentTimeMillis`; inject `kotlin.time.Clock` through Hilt.
- When JetBrains IDE (Android Studio) capabilities are available to you, prefer them over raw text edits and grep: use Rename refactoring for renames, Find Usages for impact analysis, Safe Delete, and Change Signature. They understand Kotlin semantics across all Gradle modules, where plain text search misses generated code, XML references, and overloads.

## Project Skills

Detailed guidance lives in project skills under `.agents/skills/`. Load the matching skill before detailed implementation or review:

- `ha-android-architecture`: modules, flavors, layers, repositories, use cases, Hilt, storage and encryption, and networking.
- `ha-android-ui`: Compose screens, ViewModels, state/events, HATheme design system, navigation, and widgets.
- `ha-android-kotlin-style`: constants, strong types, immutability, time types, naming, and documentation.
- `ha-android-concurrency`: coroutines, dispatchers, thread safety, and lifecycle scoping.
- `ha-android-logging-errors`: Timber, sensitive data, FailFast, and exception handling.
- `ha-android-testing`: JUnit, MockK, Robolectric, Turbine, screenshot tests, and test naming.
- `ha-android-review`: reviewing a diff for correctness, style, convention adherence, and security.
- `ha-android-committing`: finalizing a change — format, tests, changelog, branch naming, and pull requests.
- `ha-android-skill-maintenance`: updating AGENTS.md or the skills when guidance is missing, stale, or corrected.

## Pull Requests

When creating a pull request, use `.github/pull_request_template.md` as the PR body. Keep PRs small and focused. If the change is visible to end users, add it to the changelog in `app/src/main/res/xml/changelog_master.xml`. Before committing, format with ktlint and run the tests.

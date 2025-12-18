# AI instructions

This file provides guidance to any AI tool when working with code/review in this repository.

## Project Overview

This is the **Home Assistant Companion for Android**, an official Android app for the Home Assistant home automation platform. The app centers around a WebView for Home Assistant's PWA frontend, enhanced with native Android features like background sensor collection, notifications, widgets, and Wear OS support and Android Automotive.
This project is entirely made with Kotlin and it should stay like this.

## Build Commands

### Building the App

```bash
# Debug build (full and minimal)
./gradlew assembleDebug

# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :common:test
```

### Code Quality

```bash
# Format code with KTLint
./gradlew :build-logic:convention:ktlintFormat ktlintFormat

# Check code style
./gradlew ktlintCheck :build-logic:convention:ktlintCheck --continue

# Run Android linter
./gradlew lint --continue

# Update lint baseline (when Android Gradle Plugin is updated)
./gradlew updateLintBaseline
```

### Dependencies

```bash
# Update dependency locks after adding/updating dependencies
./gradlew alldependencies --write-locks

# View all dependencies
./gradlew alldependencies
```

Whenever you add or update dependencies in the project (whether through `gradle/libs.versions.toml` or direct module declarations), run the dependency lock update command.

### Testing

```bash
# Run unit tests
./gradlew test

# Run screenshot tests
./gradlew validateDebugScreenshotTest
```

## Architecture

### Multi-Module Structure

- **`:app`** - Main mobile application (min SDK is defined in `gradle/libs.versions.toml` under the name `androidSdk-min`)
- **`:automotive`** - Android Automotive version (min SDK is defined in `gradle/libs.versions.toml` under the name `androidSdk-automotive-min`, reuses `:app` sources)
- **`:wear`** - Wear OS application (min SDK is defined in `gradle/libs.versions.toml` under the name `androidSdk-wear-min`, dedicated app requiring full mobile app for onboarding)
- **`:common`** - Shared code across all apps (data layer, sensors, utilities, WebSocket, REST API)
- **`:testing-unit`** - Shared test utilities (must remain independent from `:common`)
- **`:lint`** - Custom lint rules
- **`build-logic`** - Gradle convention plugins via `includeBuild`

### App Flavors (`:app` and `:automotive` only)

- **`full`** - Includes Google Play Services (location tracking, FCM push notifications, Wear OS communication). Distributed via Play Store.
- **`minimal`** - FOSS version without Google Play Services (distributed via F-Droid, manual APK downloads, Meta Quest, OEM automotive builds, Amazon Appstore).

Code should be flavor-agnostic in the `main` source set whenever possible.

### Key Technologies

- **UI**: Jetpack Compose for all new UI (legacy XML/databinding/viewbinding still exists)
- **DI**: Hilt for dependency injection
- **Concurrency**: Kotlin Coroutines and Flow exclusively
- **Database**: Room for local storage
- **Preferences**: SharedPreferences with `LocalStorage` abstraction
- **Networking**: Retrofit (REST API), OkHttp (WebSocket to Home Assistant Core)
- **Serialization**: Kotlinx.serialization
- **Logging**: Timber for logging throughout the app. Import: `import timber.log.Timber`
- **Theming**: Use `io.homeassistant.companion.android.common.compose.theme.HATheme` for new components (Material Design based). We have a design system in place for the whole Home Assistant project.
    - The components using this Design System are prefixed with `HA*` like `HAButton`.
    - Colors are injected through `io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme` using the tokens define in `io.homeassistant.companion.android.common.compose.theme.HAColorScheme`
- **FailFast** - Custom utility for offensive programming in debug builds (located in `:common`)


### App Source Structure

Source code is in Kotlin under `src/main/kotlin/io/homeassistant/companion/android/`. Main feature areas include:

- `assist/` - Voice assistant integration
- `sensors/` - Background sensor collection
- `notifications/` - FCM and local notifications
- `settings/` - App configuration
- `onboarding/` - Initial setup flow
- `thread/` - Thread network support
- `matter/` - Matter smart home protocol
- `qs/` - Quick Settings tiles
- `controls/` - Device controls
- `launch/` - App launcher logic
- `vehicle/` - Android Auto and Automotive specific content
- `widgets/` - All the Android widgets (all new widgets must use Jetpack Glance for declarative UI)
- `webview/` - Main Activity of the application with the implementation of the external bus to communicate with the Home Assistant Frontend.


## Development Practices

### Code Style

- **Language**: All code in English, Kotlin only
- **Formatter**: KTLint enforces style via `.editorconfig`
- **Constants**: Use named constants instead of magic numbers/strings. Organize alongside classes (outside companion objects when possible), or in dedicated `*Constants.kt` files with `object` namespacing.
- **Strong Types**: Use sealed classes/interfaces over enums or strings for logic control. Use Kotlin `Duration`/`Instant` instead of primitive types for time.
- **TODOs**: Avoid TODOs. If required, link to GitHub issue: `// TODO Missing feature (linked issue #404)`
- Use small descriptive functions (not more than 50 lines) with meaningful names.
- Use proper abstraction layer to not leak domains to others like data to ui or the way around.
- We should use MVVM most of the time and when the logic becomes too complex we can use MVI using Flow and a single viewState.
- ViewModel should not use any compose/view classes but instead rely on Flow to exposes states.
- Extract complex logic from viewModel into UseCase classes
- Interaction with data layer like APIs, Storage should be made through repositories
- For any new storages we should use DataStore (encrypted) but if it can fits in existing implementation use the local storage classes with SharedPreferences.
- We are aiming at One Activity with one navigation graph (containing smaller sub graph dedicated to features), but today we do have some legacy that doesn't follow this rule. New screens need to be in the navigation graph, old screens need to be migrated
- The whole UI needs to use Jetpack compose only, no XML is allowed.
- Every public functions needs to be properly documented. The documentation should focus on the functionality instead of the details of the implementation. The implementation details should only be mentioned if it had an impact on the usage of the function itself like for instance the usage of a synchronisation mechanism that would prevent parallel execution or what it can throws.
- All displayed strings should be stored in the value files in `common` and only in english.
- New features should not impact the usability of the application, instead make sure to test the version of the server before using the new features. Like
```kotlin
if (serverManager.getServer()?.version?.isAtLeast(2025, 6, 0) == true) {
```
- Most of the methods/functions should be private to the file or class and only exposing the things that are relevant. Before making a method/function public check if it can be internal only.
- Use immutable classes exposing copy function or functions that returns a new instance of the class with the modified value to store data.
- Never use strings for logic
- When calling a function with parameters of the same type or primitive types use named parameters instead of relying on the order of the parameters.

### File Organization

- **Package by feature**, not by layer (e.g., `settings/` contains ViewModels, UI, repositories for settings)
- **Naming conventions**:
    - ViewModels: `*ViewModel.kt`
    - Repositories: `*Repository.kt` (interface) and `*RepositoryImpl.kt` (implementation)
    - UseCases: `*UseCase.kt` or describe the action (e.g., `GetUserDashboardUseCase.kt`)
    - Composables: Descriptive names matching the UI component (e.g., `SettingsScreen.kt`, `UserCard.kt`)
    - Data classes: `*Data.kt` for DTOs, domain models named directly (e.g., `User.kt`)

- **Constants**: Place near usage when possible:
  ```kotlin
  // Good - outside companion object, at file level
  private const val MAX_RETRIES = 3

  // For shared constants across module
  object NetworkConstants {
      const val MAX_RETRIES = 3
  }
  ```

### Common Patterns

#### Repository Pattern
Repositories act as the single source of truth for data, abstracting data sources (API, Database, SharedPreferences):

```kotlin
interface UserRepository {
    suspend fun getUser(id: String): User
}

class UserRepositoryImpl @Inject constructor(
    private val api: UserApi,
    private val dao: UserDao,
) : UserRepository {
    // Implementation
}
```

#### UseCase Pattern
UseCases encapsulate complex business logic that would otherwise bloat ViewModels:

```kotlin
class GetUserDashboardUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val statsRepository: StatsRepository,
) {
    suspend operator fun invoke(userId: String): Result<Dashboard> =
        // Complex logic here
}
```

#### ViewModel Pattern
ViewModels expose UI state via Flow and handle user interactions:

```kotlin
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboard: GetUserDashboardUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onRefresh() {
        viewModelScope.launch {
            // Handle refresh
        }
    }
}
```

### Documentation/Comments

Documentation within code is written for developers, including potentially non-English native speakers. Avoid using abbreviations, add references whenever it makes sense.
- **Comment Style**:
    - Use clear, descriptive comments
    - Explain the "why" not just the "what"
    - Use progressive disclosure (simple explanation first, complex details later)


### Logging

Be careful while logging to not leak any information from the user of the application in release mode.
No periods at end of messages.
If the log contains complex and heavy logic make sure to only make it when necessary.

**Error logging best practices**:
```kotlin
// Good - provides context
Timber.e(exception, "Failed to load user dashboard for userId=$userId")

// Bad - no context
Timber.e(exception)

// Good - conditional logging for expensive operations
if (BuildConfig.DEBUG) {
    Timber.d("Complex debug info: ${expensiveOperation()}")
}

// Bad - leaks user data
Timber.d("User logged in: ${user.email}")

// Good - sanitized
Timber.d("User logged in: userId=${if(BuildConfig.DEBUG) user.id else "redacted"}")
```


### Security

- **GitHub Actions**: Always use the most restrictive permissions. Don't use write when you only need read permission or even none.
- **Secrets**: Never commit tokens or secrets to Git. Use GitHub Secrets for CI/CD
- **Data encryption**: Encrypt data within the app whenever possible
    - Use EncryptedSharedPreferences for sensitive data
    - Use DataStore with encryption for new storage
- **Dependencies**: Use well-known/maintained libraries or stick to the Android SDK
- **User data**: Be careful when logging to not leak any user information in release mode

### Strict Mode

- Strict Mode issues can be ignored using `io.homeassistant.companion.android.common.util.IgnoreViolationRules`.

### Date/Clock

Never use `System.currentTimeMillis` use `kotlin.time.Clock`, for testing purpose expose the clock as a parameters of a function/attribute.
Use the `Clock` available from `Hilt`.

### Dependency Injection

- Use Hilt to instantiate any components instead of relying on manual instantiation.
- Use **Hilt** throughout
- Prefer **custom qualifier annotations** over `@Named`:
  ```kotlin
  @Inject @NamedKeyChain lateinit var keyChainRepository: KeyChainRepository
  ```
  Define qualifiers like:
  ```kotlin
  @Qualifier
  @Retention(AnnotationRetention.RUNTIME)
  annotation class NamedKeyChain
  ```

### Error Handling & Fail Fast

- Don't silently ignore exceptions - always log with `Timber`
- Use the `FailFast` API for offensive programming in debug builds:
  ```kotlin
  val value = FailFast.failOnCatch(
      message = { "Error description with context" },
      fallback = "fallbackValue"
  ) { riskyOperation() }
  ```
  This crashes debug builds but gracefully falls back in production.
- **Never catch** `kotlin.coroutines.cancellation.CancellationException` - let it propagate to properly cancel coroutines
- Always try to capture the most precise exception type, not a global like `catch(e: Exception)`
- Exceptions should contain meaningful messages and use explicit exception types instead of generic ones

### Testing

- **Framework**: JUnit Jupiter for unit tests (or 4 when necessary to use Robolectric)
- **Mocking**: MockK (Use real objects when you can or fakes)
- **Android APIs**: Robolectric (requires JUnit 4 compatibility)
- **Test Location**: Tests should mirror source structure in `src/test/kotlin/` directory
- **Test Naming**: Use GIVEN-WHEN-THEN structure with descriptive sentences:
  ```kotlin
  @Test
  fun `Given user authenticated when opening app then show dashboard`() { ... }
  ```
- **Focus**: Test public interfaces, not implementation details
- **Shared Code**: Add to `:testing-unit` if needed across modules
- **Coverage Goal**: All public APIs and business logic should have unit tests
- When using JUnit Jupiter use `@ParametrizedTest` when tests are repeating with only a value change. In JUnit 4 extract the content of the test into a private function and create multiple tests with different names using the private function.

Everything should be tested with unit tests where practical. Screens need to be tested in isolation, especially interaction with the screen to verify it triggers the right method. Screen looks should be tested using screenshot tests; these tests should not test the logic.

Instrumentation tests should only be used when there is no other solution or to test the behavior of the system on different APIs.
All the navigation within the app needs to be tested including the back and forward stack.

### Concurrency

- Tie all new coroutines scope to Android lifecycle (`viewModelScope`, `lifecycleScope`)
- Ensure thread-safe concurrent access (you can use mutex but usage of synchronised block is forbidden and prefer any solution that doesn't block threads but suspend instead)
- Ensure that method/functions are main thread safe whenever they can otherwise document carefully.
- Debugging race conditions is hard - design carefully upfront
- Never block a thread
- Never uses `runBlocking`
- Make sure to test concurrency with unit tests using `TestDispatcher`.
- Heavy operation needs to be done in a dedicated dispatcher `Default` by default or `IO` if it a blocking call using network or any kind of storage.
  Only use `Main` dispatcher for critical calls to the system API that requires the main thread limit the instructions in this dispatcher to the strict minimum.

### Networking & API

- **Retrofit** for REST APIs, **OkHttp** for WebSocket connections
- All API interfaces should be in `data/` layer within `:common` Gradle module
- Use `suspend` functions for API calls

### Navigation

- The app uses Jetpack Navigation Compose for screen navigation
- **Goal**: One Activity with one main navigation graph containing smaller sub-graphs per feature
- **Current state**: Legacy Activities exist but all new screens must use Navigation Compose
- Navigation graphs are defined in the `:app` module
- Each feature can have its own sub-graph that integrates into the main graph
- Always test navigation flows including back and forward stack behavior
- Use type-safe navigation using `data class` or `object class` annotated with `@Serializable` from Kotlinx serialization

Example navigation structure:
```kotlin
@Serializable
data class FeatureRoute(val value: Int)

// Feature-specific navigation graph
fun NavGraphBuilder.featureNavGraph() {
    composable<FeatureRoute> {
        FeatureScreen(
            value = it.toRoute<FeatureRoute>().value,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToDetails = { id -> navController.navigate("feature_details/$id") }
        )
    }
}
```

### Compose UI Guidelines

- **State hoisting**: Lift state to the appropriate level
  ```kotlin
  // Good
  @Composable
  fun SettingsScreen(
      uiState: SettingsUiState,
      onToggleSetting: (String) -> Unit,
  ) { /* ... */ }

  @Composable
  fun SettingsScreen(
      viewModel: SettingsViewModel,
  ) {
      val state by viewModel.state.collectAsStateWithLifecycle()
      SettingsScreen(state, viewModel::onToggleSetting)
   }

  // Bad - Composable that only takes a viewModel
  @Composable
  fun SettingsScreen(viewModel: SettingsViewModel) { /* ... */ }
  ```

- **Preview functions**: Add `@Preview` for all major composables and uses `io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview`
  ```kotlin
  @Preview
  @Composable
  private fun SettingsScreenPreview() {
      HAThemeForPreview {
          SettingsScreen(
              uiState = SettingsUiState.Default,
              onToggleSetting = {},
          )
      }
  }
  ```

- **Design System**: Always use `io.homeassistant.companion.android.common.compose.theme.HATheme` components (HAButton, HAText, etc.) instead of Material components directly
- **Colors**: Access via `io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme.current` instead of hardcoded colors
- **Strings**: Use `stringResource(R.string.*)` - never hardcode displayed text

### PRs and Contributions

- **Keep PRs small** - easier to review, faster to merge
- **Small functions/classes** - single responsibility, easy to name
- **Composition over inheritance** - more flexible and testable
- **Leverage Kotlin compiler** - use `when` with sealed classes (no `else` branch) to catch missing cases at compile time
- Use PR templates from [pull_request_template.md](.github/pull_request_template.md)
- While reviewing be kind and respectful to everyone, give hints instead of orders. Gives examples to explain issues
- When the PR contains a visible change or behavior change for the end users it should be added to the changelog in `app/src/main/res/xml/changelog_master.xml`.

### Version Control & Git

- **Branch naming**:
    - Feature: `feature/add-dark-mode`
    - Bug fix: `fix/crash-on-rotation`

- **Before committing**:
    1. Run `./gradlew ktlintFormat` to format code
    2. Run `./gradlew test` to ensure tests pass
    3. Update changelog if user-facing changes

### Debugging & Development

- **Timber in debug builds**: Timber logs are automatically enabled in debug builds
- **Strict Mode**: Issues can be ignored using `IgnoreViolationRule` injected in the Application class
- **LogCat filtering**: Use tags to filter logs effectively (Timber uses class name as tag by default)
- **Network debugging**: Use OkHttp interceptors for logging network requests in debug builds
- **Database inspection**: Use Android Studio's App Inspection tool to view Room database

## Deep Linking

The app supports `homeassistant://` URLs for navigation. See user documentation at https://companion.home-assistant.io/docs/integrations/url-handler/

## Wear OS Communication

The Wear OS app uses the Messaging API to retrieve credentials from the mobile app (only works with `full` flavor). After setup, communication is direct with Home Assistant via WebSocket and webhooks.

## Widgets

- All new widgets **must** use Jetpack Glance for declarative widget development
- Glance provides a Compose-like API for building Android widgets
- Legacy widgets using RemoteViews exist but should not be used for new features
- Widget code is located in `app/src/main/kotlin/io/homeassistant/companion/android/widgets/`
- Widgets should follow the same state management patterns as screens (ViewModels, Repositories)

## Server Commands

The Home Assistant server can send commands to the app through `io.homeassistant.companion.android.notifications.MessagingManager`. These commands enable server-side control of app functionality such as:
- Triggering notifications
- Updating sensors
- Controlling app behavior
- Deep linking to specific screens

Commands are received and processed asynchronously. Always validate command inputs and handle errors gracefully.

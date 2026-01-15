# Frontend Screen Refactor Design

## Overview

Refactor the frontend/WebView functionality from the legacy `WebViewActivity` (Presenter pattern, 2100+ lines) to a modern Compose-based implementation using ViewModel, following the patterns established in the onboarding flow.

## Motivation

- **Modernization**: Move from Presenter to ViewModel/Compose to align with the codebase
- **Testability**: Enable proper unit testing with ViewModels
- **Maintainability**: Break down the monolithic Activity into focused components
- **Feature enablement**: Easier to implement future features with clean architecture

## Approach

**Clean break with feature parity milestones**: Build the new `FrontendScreen` from scratch with minimal functionality first, then add features incrementally until parity is reached.

A `WIPFeature.USE_COMPOSE_FRONTEND` flag (DEBUG-only) enables incremental PRs without affecting release builds.

## Package Structure

```
app/src/main/kotlin/.../frontend/
├── navigation/
│   └── FrontendNavigation.kt          # Updated with conditional routing
├── FrontendScreen.kt                   # Main Compose screen
├── FrontendViewModel.kt                # State management, WebViewClient
├── FrontendViewState.kt                # Sealed interface for UI states
└── externalbus/
    ├── ExternalBusMessage.kt           # Base class/common types
    ├── ExternalBusRepository.kt
    ├── ExternalBusRepositoryImpl.kt
    ├── incoming/                        # Messages FROM frontend
    │   ├── ConnectionStatusMessage.kt
    │   ├── ConfigGetMessage.kt
    │   ├── AssistMessage.kt
    │   ├── TagWriteMessage.kt
    │   ├── MatterThreadMessages.kt
    │   ├── BarcodeScanMessage.kt
    │   ├── ImprovMessages.kt
    │   ├── ExoPlayerMessages.kt
    │   ├── HapticMessage.kt
    │   ├── ThemeUpdateMessage.kt
    │   └── EntityAddToMessages.kt
    └── outgoing/                        # Messages TO frontend
        ├── ConfigResponse.kt
        ├── NavigateToMessage.kt
        ├── ShowSidebarMessage.kt
        ├── ResultMessage.kt
        └── EntityAddToResponse.kt

app/src/main/kotlin/.../webview/error/   # Shared error handling
├── ConnectionError.kt                   # Moved from onboarding
└── ConnectionErrorScreen.kt             # Moved from onboarding
```

## WIP Feature Flag

```kotlin
// WIPFeature.kt
object WIPFeature {
    /**
     * Enables the new Compose-based frontend screen.
     * When true, FrontendRoute navigates to FrontendScreen (Compose).
     * When false, FrontendRoute navigates to WebViewActivity (legacy).
     */
    val USE_COMPOSE_FRONTEND: Boolean = BuildConfig.DEBUG && true
}
```

## Navigation Routing

```kotlin
// FrontendNavigation.kt
internal fun NavGraphBuilder.frontendScreen(navController: NavController) {
    if (WIPFeature.USE_COMPOSE_FRONTEND) {
        composable<FrontendRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<FrontendRoute>()
            FrontendScreen(
                serverId = route.serverId,
                path = route.path,
            )
        }
    } else {
        // Legacy: Bridge to WebViewActivity
        composable<FrontendRoute> {
            val route = it.toRoute<FrontendRoute>()
            navController.navigate(FrontendActivityRoute(route.serverId, route.path))
            navController.context.getActivity()?.finish()
        }

        activity<FrontendActivityRoute> {
            activityClass = WebViewActivity::class
        }
    }
}
```

## View State

```kotlin
// FrontendViewState.kt
internal sealed interface FrontendViewState {
    data class Loading(
        val serverId: Int,
        val url: String,
        val path: String? = null,
    ) : FrontendViewState

    data class Content(
        val serverId: Int,
        val url: String,
        val serverHandleInsets: Boolean = false,
        val statusBarColor: Color? = null,
        val backgroundColor: Color? = null,
    ) : FrontendViewState

    data class Error(
        val serverId: Int,
        val url: String,
        val error: ConnectionError,
    ) : FrontendViewState
}
```

Server switching triggers a transition back to `Loading` state with the new server's details.

## Shared Error Handling

`ConnectionError` and `ConnectionErrorScreen` are moved from `onboarding/connection/` to `webview/error/` for sharing between onboarding and frontend.

The `ConnectionErrorScreen` accepts an `actions` slot, allowing different actions:
- Onboarding: "Back" button
- Frontend: "Retry", "Settings", "Switch URL" buttons

## ViewModel Structure

```kotlin
@HiltViewModel
internal class FrontendViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverManager: ServerManager,
    @NamedKeyChain private val keyChainRepository: KeyChainRepository,
) : ViewModel() {

    private val serverId: Int = savedStateHandle.toRoute<FrontendRoute>().serverId
    private val initialPath: String? = savedStateHandle.toRoute<FrontendRoute>().path

    private val _viewState = MutableStateFlow<FrontendViewState>(...)
    val viewState: StateFlow<FrontendViewState> = _viewState.asStateFlow()

    val webViewClient: TLSWebViewClient = object : TLSWebViewClient(keyChainRepository) {
        // Error handling updates _viewState
    }

    fun onGetExternalAuth(callback: String, force: Boolean) { /* ... */ }
    fun onRetry() { /* ... */ }
    fun switchServer(serverId: Int) { /* ... */ }
    fun nextServer() { /* ... */ }
    fun previousServer() { /* ... */ }
}
```

The `TLSWebViewClient` is owned by the ViewModel (same pattern as `ConnectionViewModel`), keeping business logic out of the Screen.

## Screen Structure

Two-layer pattern matching onboarding:

```kotlin
// Entry point with ViewModel
@Composable
internal fun FrontendScreen(
    serverId: Int,
    path: String?,
    viewModel: FrontendViewModel = hiltViewModel(),
)

// Pure UI for testing/previews
@Composable
internal fun FrontendScreen(
    viewState: FrontendViewState,
    webViewClient: WebViewClient,
    onGetExternalAuth: (callback: String, force: Boolean) -> Unit,
    onRetry: () -> Unit,
    onSwitchServer: (serverId: Int) -> Unit,
    onNextServer: () -> Unit,
    onPreviousServer: () -> Unit,
    onOpenSettings: () -> Unit,
)
```

Insets are handled using the existing `SafeHAWebView` pattern from `WebViewContentScreen`, supporting both server-handled insets (2025.12+) and app-rendered overlays.

## External Bus Refactoring

After the MVP, the external bus will be refactored from generic JSON to typed messages:

- **IncomingExternalBusMessage**: Messages from the frontend (sealed hierarchy)
- **OutgoingExternalBusMessage**: Messages to the frontend (sealed hierarchy)

Each message type in its own file or grouped by feature (e.g., `ExoPlayerMessages.kt`).

## Milestones

### Milestone 1: MVP (This PR)
- WIP feature flag
- Basic FrontendScreen/ViewModel
- Load WebView with server URL
- Handle authentication (getExternalAuth)
- Error handling with shared ConnectionErrorScreen
- Proper inset handling
- Full unit and compose tests

### Milestone 2: External Bus Typed Messages
- Create incoming/outgoing message hierarchies
- Migrate from JSON parsing to kotlinx.serialization
- Polymorphic deserialization for message types

### Milestone 3+: Feature Parity
- Gestures (swipe actions)
- ExoPlayer integration
- Matter/Thread support
- Improv scanning
- Haptic feedback
- Theme updates
- And remaining WebViewActivity features...

### Final: Deep Links
- Handle homeassistant:// URL scheme
- Navigation to specific paths/entities

## Testing Strategy

| Test Type | Files | Purpose |
|-----------|-------|---------|
| Unit | `FrontendViewModelTest.kt` | State transitions, auth flow, server switching |
| Compose | `FrontendScreenTest.kt` | UI interactions, state rendering |
| Screenshot | `FrontendScreenScreenshotTest.kt` | Visual regression |

All tests follow the patterns established in the onboarding tests.

## Migration Notes

- `WebViewActivity` remains functional via the WIP flag
- No changes to release builds until migration is complete
- Incremental PRs can be merged to main
- Each milestone is independently shippable behind the flag

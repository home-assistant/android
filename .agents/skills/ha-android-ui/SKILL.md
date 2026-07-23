---
name: ha-android-ui
description: Home Assistant Android UI guidance. Use when building or reviewing Compose screens, ViewModels, screen state, navigation, dialogs, widgets, or anything using HATheme and the design system.
---

# HA Android UI

Use this skill when creating or changing screens, ViewModels, navigation, or widgets.

The screen pattern is documented in depth at https://developers.home-assistant.io/docs/android/ui_architecture.

## Compose Only

- All new UI uses Jetpack Compose; no new XML, databinding, or viewbinding. Legacy XML screens are migrated over time.
- All new widgets use Jetpack Glance; legacy RemoteViews widgets must not be extended.

## Design System

- Use `io.homeassistant.companion.android.common.compose.theme.HATheme` and the `HA*` components (`HAButton`, `HAText`, `HATextField`, `HACheckbox`, `HALoading`, `HADropdownMenu`, `HAModalBottomSheet`, ...) instead of Material components directly. Apply the theme in `setContent`, not inside the screen composable.
- Never mix Material 2 and Material 3 in a screen; use `HATextStyle` for typography, `HADimens.SPACE*` tokens for spacing and sizes — no magic numbers for padding.
- Access colors via `LocalHAColorScheme.current` with the tokens from `HAColorScheme`; never hardcode colors. Raw Material 3 elements get the wrong light/dark colors — if an `HA*` wrapper is missing, create it first (own PR, added to the Compose catalog with screenshots) instead of styling a Material component inline.
- Use Snackbars for errors and transient feedback, not Toasts.
- All displayed strings live in the value files in `:common`, English only, accessed with `stringResource(R.string.*)` — never hardcode displayed text. Only edit `common/src/main/res/values/strings.xml`: translations come from Lokalise and the translated `values-*/strings.xml` files are gitignored — never add or edit them.

## Screen Structure

Name composables descriptively after the UI component (`SettingsScreen.kt`, `UserCard.kt`). Split every screen into a stateful entry point and a stateless content composable, hoisting state:

```kotlin
@Composable
internal fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreenContent(uiState = state, onToggleSetting = viewModel::onToggleSetting)
}

@Composable
internal fun SettingsScreenContent(uiState: SettingsUiState, onToggleSetting: (String) -> Unit) {
    // Stateless: renders uiState and reports interactions through callbacks.
}
```

Recurring review feedback on screen composables:

- Keep composables small: split large screens into small private composables (see recent screens like `AssistSettingsScreen`), and move a composable to its own file when the file grows too big.
- Pass child composables only the parameters they need, never the whole ViewState.
- No logic in the UI layer: no filtering, sorting, or mapping collections inside a composable — the ViewState carries display-ready data (for example a prebuilt `HADropdownItem` list). `remember` any value that must be computed in composition so it doesn't rebuild on every recomposition.
- Name the stateless layer `*Screen`/`*Content`; `*View` is legacy.
- Set the semantic `Role` on custom clickable rows (e.g. `Modifier.clickable(role = Role.Checkbox)`) for accessibility.
- Disable/gate action buttons on input validity via the ViewState, and surface errors to the user (snackbar or state), with validation living in the ViewModel.
- Don't seed local UI state once from a value that can still change (`LaunchedEffect(Unit)` reading a flow-backed field) — derive it from the current state so it stays in sync.

Add a `@Preview` for major composables using `HAThemeForPreview`:

```kotlin
@Preview
@Composable
private fun SettingsScreenPreview() {
    HAThemeForPreview {
        SettingsScreenContent(uiState = SettingsUiState.Default, onToggleSetting = {})
    }
}
```

## ViewModel

- Use MVVM; when logic grows complex, move to MVI with a single `viewState` Flow. Extract complex logic into UseCase classes and interact with data through repositories.
- ViewModels never reference Compose or platform UI types; they expose state through Flow:

```kotlin
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboard: GetUserDashboardUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
}
```

- State classes are immutable (`val`, immutable collections, `copy()` for changes) and cheap to read: never compute in a `get()` that Compose evaluates on every recomposition — pre-compute when producing the state, in the ViewModel on a background dispatcher (`Default`), never on Main. Iterating a server's full entity list on the Main thread is a recurring review finding.
- One ViewState is the single source of truth: don't keep parallel maps or extra mutable fields in the ViewModel that mirror what the state already holds — read and update the state itself.
- ViewModels never expose Compose or platform types (no `Context`, no icon or lazy-list types); resolve those in the Compose layer and hoist callbacks instead of passing UI objects down.
- Model distinct screen modes as a sealed hierarchy (`Loading` / `Content` / `Error`) so `when` stays exhaustive.
- One-shot effects (navigation, snackbar) are events on a consumed-once Flow, never replayed state.

## Navigation

- Jetpack Navigation Compose, aiming for one Activity with one navigation graph made of feature sub-graphs. New screens must be in the graph; legacy Activities get migrated.
- Use type-safe routes with `@Serializable` data/object classes:

```kotlin
@Serializable
data class FeatureRoute(val value: Int)

fun NavGraphBuilder.featureNavGraph(navController: NavController) {
    composable<FeatureRoute> {
        FeatureScreen(
            value = it.toRoute<FeatureRoute>().value,
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
```

- All navigation needs tests, including back and forward stack behavior.

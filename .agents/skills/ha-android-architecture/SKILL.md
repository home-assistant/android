---
name: ha-android-architecture
description: Home Assistant Android module and layer architecture. Use when adding features, choosing where code lives, writing repositories or use cases, setting up Hilt injection, storing data securely, or calling Home Assistant APIs.
---

# HA Android Architecture

Use this skill when structuring a feature: which module and package it lives in, how its layers talk to each other, and how data is stored or fetched.

The full architecture documentation lives at https://developers.home-assistant.io/docs/android/architecture and https://developers.home-assistant.io/docs/android/ui_architecture.

## Modules and Flavors

- `:app` (mobile), `:automotive` (reuses `:app` sources), `:wear`, `:common` (shared data layer, sensors, WebSocket, REST API), `:testing-unit` (shared test utilities, must stay independent from `:common`), `:lint` (custom lint rules), `build-logic` (Gradle convention plugins).
- `:app` and `:automotive` build in two flavors: `full` (Google Play Services: location, FCM, Wear communication) and `minimal` (FOSS). Keep code in the `main` source set flavor-agnostic whenever possible.
- **Package by feature**, not by layer: `settings/` contains its ViewModels, UI, and repositories. Main feature areas under `src/main/kotlin/io/homeassistant/companion/android/`: `assist/`, `sensors/`, `notifications/`, `settings/`, `onboarding/`, `thread/`, `matter/`, `qs/` (Quick Settings tiles), `controls/` (device controls), `launch/`, `vehicle/` (Android Auto/Automotive), `widgets/`, and `frontend/`.

## Layers

Dependencies point downward only. From `ui_architecture`:

- **ViewModel** (UI layer): owns the screen state and coordination; see the `ha-android-ui` skill.
- **Handler**: logic that coordinates one or more managers. A handler is defined by depending on a manager; it may also use use cases and repositories, but never another handler — coordinate handlers in the ViewModel.
- **Manager**: owns the logic and in-memory state of exactly one feature concern. Depends only on repositories, use cases, and platform APIs — never on another manager.
- **UseCase**: a single reusable stateless operation, `operator fun invoke(...)`, may depend on repositories only.
- **Repository**: single source of truth for one data source (API, Room DAO, preferences). No UI concerns, and no repository → repository dependencies.

Naming follows the block: `*Repository` + `*RepositoryImpl`, `*UseCase`, `*Manager`, `*Handler`, `*ViewModel`. DTOs are `*Data.kt`; domain models are named directly (`User.kt`).

Only split a block into interface + `*Impl` when it buys something real: multiple implementations exist (per flavor, fake for tests when a mock won't do), or the contract is public while the implementation stays `internal` to its module and is bound through DI. Otherwise a simple injectable class is preferred — don't create the pair by reflex.

## Dependency Injection

- Use Hilt everywhere; never instantiate components manually.
- Scope blocks to how long their state must live: `@ViewModelScoped` for screen-session state, `@Singleton` for app-wide concerns. Stateful blocks injected in several places must be scoped so consumers share one instance.
- Never instantiate an `@AndroidEntryPoint` component (receiver, service) manually — Hilt field injection only happens through the framework lifecycle, so a hand-constructed instance crashes with `UninitializedPropertyAccessException`. Extract the shared logic into an injectable class or use an `@EntryPoint` interface instead.
- Prefer custom qualifier annotations over `@Named`:

```kotlin
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class NamedKeyChain

@Inject @NamedKeyChain lateinit var keyChainRepository: KeyChainRepository
```

## Storage

- Room for structured data, accessed through a repository via DAOs.
- For new storage use DataStore (encrypted); if it fits existing implementations, use the `LocalStorage` abstraction over SharedPreferences.
- Use EncryptedSharedPreferences for sensitive data. Encrypt data within the app whenever possible.

## Networking and APIs

- Retrofit for the REST API, OkHttp for the WebSocket connection to Home Assistant Core.
- All API interfaces live in the `data/` layer of `:common` and use `suspend` functions.
- Check `socketResponse.success` before decoding its `result`, and return `null`/empty on failure so callers get a consistent "no data" instead of a decode exception on an error payload.
- New features must not break usability on older servers — gate them on the server version:

```kotlin
if (serverManager.getServer()?.version?.isAtLeast(2025, 6, 0) == true) { /* use new feature */ }
```

## Incremental Migration

Prefer landing changes incrementally in small mergeable steps that keep the app working at every point. Only when a rework is too huge for that (for example `USE_FRONTEND_V2`, the WebViewActivity → FrontendScreen migration) does it go behind a flag in `WIPFeature.kt` (`app/src/main/kotlin/io/homeassistant/companion/android/WIPFeature.kt`), usually debug-only, with the legacy path still serving users until the replacement is ready. Migration PRs stay separate from feature PRs, and guidelines introduced after code existed are applied opportunistically — fix violations as you touch the code, don't mass-migrate.

## Intents and Contexts

- Always specify PendingIntent mutability; default to `FLAG_IMMUTABLE` unless mutability is genuinely required (missing flags throw on Android 12+, and unnecessary mutability widens the attack surface).
- Explicit Intents to exported components must match the target's intent filter — set the action (for example `ACTION_MAIN` when targeting `LaunchActivity`), otherwise Android's strict intent matching blocks the launch (widgets, shortcuts, device controls).
- Pass `context.applicationContext` to anything long-lived (monitors, managers, sessions); never retain a BroadcastReceiver or Activity context. In Hilt-built classes, inject `@ApplicationContext` instead of threading a context parameter through.

## Platform Integration Points

- `frontend/` hosts the frontend screen — the WebView rendering the Home Assistant frontend — and the external bus (`frontend/externalbus/`) communicating with it (see https://developers.home-assistant.io/docs/android/frontend_screen). `webview/` is the legacy implementation being removed; don't build on it.
- Server commands reach the app through `io.homeassistant.companion.android.notifications.MessagingManager`; always validate command inputs.
- Deep links use `homeassistant://` URLs (https://companion.home-assistant.io/docs/integrations/url-handler/).
- The Wear OS app retrieves credentials from the mobile app via the Messaging API (`full` flavor only), then talks to Home Assistant directly.

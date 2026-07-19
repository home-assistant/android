package io.homeassistant.companion.android.launch.link

import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.settings.server.ServerChooserItem

/**
 * Persistent UI state rendered by the [LinkActivity] screen while a deep link is being resolved.
 *
 * One-shot navigation (opening the WebView, onboarding or finishing) is not represented here but
 * emitted as [LinkNavigationEvent] instead.
 */
sealed interface LinkUiState {
    /** The link is being resolved; only the app icon background is shown. */
    data object Loading : LinkUiState

    /**
     * More than one server is registered: the user must pick one of [items] before the frontend
     * is opened at [target].
     */
    data class ChoosingServer(val items: List<ServerChooserItem>, val target: FrontendTarget) : LinkUiState
}

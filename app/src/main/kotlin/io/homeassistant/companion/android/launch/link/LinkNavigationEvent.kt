package io.homeassistant.companion.android.launch.link

import io.homeassistant.companion.android.frontend.navigation.FrontendTarget

/**
 * One-shot navigation events.
 */
sealed interface LinkNavigationEvent {
    /** Close without navigating anywhere. */
    data object Finish : LinkNavigationEvent

    /** Start the server onboarding flow for [serverUrl]. */
    data class OpenInvitation(val serverUrl: String) : LinkNavigationEvent

    /** Open the frontend at [target] on the server identified by [serverId]. */
    data class NavigateToWebView(val target: FrontendTarget, val serverId: Int) : LinkNavigationEvent
}

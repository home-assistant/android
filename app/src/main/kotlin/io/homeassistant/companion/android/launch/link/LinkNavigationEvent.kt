package io.homeassistant.companion.android.launch.link

/**
 * One-shot navigation events.
 */
sealed interface LinkNavigationEvent {
    /** Close without navigating anywhere. */
    data object Finish : LinkNavigationEvent

    /** Start the server onboarding flow for [serverUrl]. */
    data class OpenInvitation(val serverUrl: String) : LinkNavigationEvent

    /** Navigate the WebView at [path] on the server identified by [serverId]. */
    data class NavigateToWebView(val path: String, val serverId: Int) : LinkNavigationEvent
}

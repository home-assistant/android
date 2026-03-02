package io.homeassistant.companion.android.frontend.navigation

/**
 * One-shot navigation events for navigating to external screens.
 *
 * Use these for fire-and-forget navigation that should not persist in state (e.g., opening
 * Settings activity). Events are emitted via [SharedFlow] and consumed once by
 * [FrontendNavigationHandler].
 *
 * For persistent UI states that determine what's rendered within the frontend screen, use
 * [io.homeassistant.companion.android.frontend.FrontendViewState] instead.
 */
sealed interface FrontendNavigationEvent {
    /** Navigate to the app settings screen */
    data object NavigateToSettings : FrontendNavigationEvent

    /** Navigate to the voice assistant (Assist) screen */
    data class NavigateToAssist(
        val serverId: Int,
        val pipelineId: String?,
        val startListening: Boolean,
    ) : FrontendNavigationEvent
}

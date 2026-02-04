package io.homeassistant.companion.android.frontend.handler

import io.homeassistant.companion.android.frontend.error.FrontendConnectionError

/**
 * Events emitted by [FrontendMessageHandler].
 *
 * These events are triggered by messages received from the Home Assistant frontend
 * via the external bus.
 */
sealed interface FrontendHandlerEvent {

    /**
     * Frontend reported connection established.
     */
    data object Connected : FrontendHandlerEvent

    /**
     * Frontend reported disconnection from Home Assistant.
     */
    data object Disconnected : FrontendHandlerEvent

    /**
     * Frontend requested app configuration and the response was sent.
     */
    data object ConfigSent : FrontendHandlerEvent

    /**
     * User tapped the settings button in the frontend sidebar.
     */
    data object OpenSettings : FrontendHandlerEvent

    /**
     * Frontend theme changed (colors, dark mode, etc.).
     */
    data object ThemeUpdated : FrontendHandlerEvent

    /**
     * Received an unrecognized message type from the frontend.
     */
    data object UnknownMessage : FrontendHandlerEvent

    /**
     * Authentication failed with an error that should be displayed to the user.
     *
     * This occurs when the session is anonymous and external auth retrieval fails.
     */
    data class AuthError(val error: FrontendConnectionError) : FrontendHandlerEvent
}

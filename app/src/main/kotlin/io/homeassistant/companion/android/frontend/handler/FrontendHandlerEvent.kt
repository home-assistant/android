package io.homeassistant.companion.android.frontend.handler

import io.homeassistant.companion.android.frontend.download.DownloadResult
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.externalbus.incoming.HapticType

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
     * User tapped the companion app settings button in the frontend.
     */
    data object OpenSettings : FrontendHandlerEvent

    /**
     * User tapped the companion app assist settings button in the frontend.
     */
    data object OpenAssistSettings : FrontendHandlerEvent

    /**
     * User triggered the voice assistant from the frontend.
     */
    data class ShowAssist(val pipelineId: String?, val startListening: Boolean) : FrontendHandlerEvent

    /**
     * Frontend theme changed (colors, dark mode, etc.).
     */
    data object ThemeUpdated : FrontendHandlerEvent

    /**
     * Frontend requested haptic feedback.
     */
    data class PerformHaptic(val hapticType: HapticType) : FrontendHandlerEvent

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

    /**
     * A blob download completed (via the JS bridge [handleBlob] callback).
     *
     * The ViewModel should process the [result] to emit appropriate UI feedback.
     */
    data class DownloadCompleted(val result: DownloadResult) : FrontendHandlerEvent

    /**
     * Frontend requested the NFC tag-write flow to be launched.
     *
     * @param messageId The correlation id from the incoming `tag/write` message; used to respond back
     *   to the frontend once the flow completes.
     * @param tagId Optional pre-filled tag identifier. When null, the user is prompted to enter one.
     */
    data class WriteNfcTag(val messageId: Int, val tagId: String?) : FrontendHandlerEvent
}

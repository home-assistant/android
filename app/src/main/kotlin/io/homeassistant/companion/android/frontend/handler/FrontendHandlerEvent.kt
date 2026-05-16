package io.homeassistant.companion.android.frontend.handler

import android.net.Uri
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

    /**
     * Frontend requested the app to start scanning for improv (Wi-Fi onboarding) BLE devices.
     *
     * The ViewModel is responsible for the BLE-feature gate, the runtime permission flow
     * (Bluetooth + Location), and emitting
     * [io.homeassistant.companion.android.frontend.externalbus.outgoing.ImprovDiscoveredDeviceMessage]
     * for each device the scanner reports.
     */
    data object StartImprovScan : FrontendHandlerEvent

    /**
     * User picked an improv device in the frontend's list. The ViewModel should open the
     * Wi-Fi-credentials dialog seeded with [deviceName], drive the BLE provisioning flow, and
     * emit [io.homeassistant.companion.android.frontend.externalbus.outgoing.ImprovDeviceSetupDoneMessage]
     * once provisioning succeeds.
     *
     * @param deviceName Advertised name of the BLE device the user selected.
     */
    data class ConfigureImprovDevice(val deviceName: String) : FrontendHandlerEvent

    sealed interface ExoPlayerAction : FrontendHandlerEvent {

        /**
         * Start playing an HLS stream.
         *
         * @param messageId The external bus message ID for the result callback
         * @param url The HLS stream URL
         * @param muted Whether playback should start muted
         */
        data class PlayHls(val messageId: Int?, val url: Uri, val muted: Boolean) : ExoPlayerAction

        /** Stop playback and release the player. */
        data object Stop : ExoPlayerAction

        /**
         * Resize and reposition the player overlay.
         *
         * Values come from the frontend's `Element.getBoundingClientRect()` and are already
         * scaled to screen pixels (expressed as dp for Compose).
         *
         * @param left Left offset in dp
         * @param top Top offset in dp
         * @param right Right edge in dp
         * @param bottom Bottom edge in dp, or 0 if the frontend does not impose a height constraint
         */
        data class Resize(val left: Double, val top: Double, val right: Double, val bottom: Double) : ExoPlayerAction
    }
}

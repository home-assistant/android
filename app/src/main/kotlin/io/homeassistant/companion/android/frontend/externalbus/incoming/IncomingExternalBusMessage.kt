package io.homeassistant.companion.android.frontend.externalbus.incoming

import io.homeassistant.companion.android.common.util.UnknownJsonContent
import io.homeassistant.companion.android.common.util.UnknownJsonContentBuilder
import io.homeassistant.companion.android.common.util.UnknownJsonContentDeserializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule

/**
 * Base sealed interface for messages received from the Home Assistant frontend via the external bus.
 *
 * The external bus is a communication channel between the Home Assistant frontend
 * and the Android app, allowing the frontend to request native functionality like
 * NFC tag writing, Matter commissioning, haptic feedback, etc.
 *
 * @see <a href="https://developers.home-assistant.io/docs/frontend/external-bus">External bus documentation</a>
 * @see <a href="https://github.com/home-assistant/frontend/blob/dev/src/external_app/external_messaging.ts">Frontend implementation</a>
 */
@Serializable
sealed interface IncomingExternalBusMessage {

    /**
     * The message ID used to correlate responses.
     * May be null for fire-and-forget messages.
     */
    val id: Int?

    companion object {
        /**
         * Serializers module for polymorphic deserialization of incoming external bus messages.
         *
         * This module must be added to the Json builder to handle unknown message types gracefully.
         * Unknown types are deserialized as [UnknownIncomingMessage] instead of throwing an exception.
         */
        internal val serializersModule = SerializersModule {
            polymorphicDefaultDeserializer(IncomingExternalBusMessage::class) { className ->
                object : UnknownJsonContentDeserializer<UnknownIncomingMessage>() {
                    override val builder = UnknownJsonContentBuilder { content ->
                        UnknownIncomingMessage(className, content)
                    }
                }
            }
        }
    }
}

/**
 * Fallback message type for unknown or unhandled external bus message types.
 *
 * This allows the app to gracefully handle new message types from newer Home Assistant
 * versions without crashing. The raw JSON content is preserved for debugging or
 * forward compatibility.
 *
 * @property content The raw JSON content of the unknown message
 */
data class UnknownIncomingMessage(override val discriminator: String?, override val content: JsonElement) :
    IncomingExternalBusMessage,
    UnknownJsonContent {
    override val id: Int? = null
}

/**
 * Message indicating the frontend's connection status to the Home Assistant server.
 *
 * Sent when the frontend WebSocket connection state changes (connected/disconnected).
 */
@Serializable
@SerialName("connection-status")
data class ConnectionStatusMessage(override val id: Int? = null, val payload: ConnectionStatusPayload) :
    IncomingExternalBusMessage

@Serializable
data class ConnectionStatusPayload(val event: String) {
    val isConnected: Boolean
        get() = event == "connected"
}

/**
 * Message requesting the app's configuration and capabilities.
 *
 * The frontend sends this to discover what native features the app supports,
 * such as NFC tag writing, Matter commissioning, barcode scanning, etc.
 *
 * The app should respond with an [io.homeassistant.companion.android.frontend.externalbus.outgoing.OutgoingExternalBusMessage] containing the capabilities.
 */
@Serializable
@SerialName("config/get")
data class ConfigGetMessage(override val id: Int? = null) : IncomingExternalBusMessage

/**
 * Message requesting the app to open its settings.
 * No response is expected for this message
 */
@Serializable
@SerialName("config_screen/show")
data class OpenSettingsMessage(override val id: Int? = null) : IncomingExternalBusMessage

/**
 * Message requesting the app to open its assist settings
 * No response is expected for this message
 */
@Serializable
@SerialName("assist/settings")
data class OpenAssistSettingsMessage(override val id: Int? = null) : IncomingExternalBusMessage

/**
 * Message indicating that the frontend theme has changed.
 *
 * Sent when the user changes the theme in the frontend, allowing the app
 * to update the status bar and navigation bar colors to match.
 */
@Serializable
@SerialName("theme-update")
data class ThemeUpdateMessage(override val id: Int? = null) : IncomingExternalBusMessage

/**
 * Message requesting the app to open the voice assistant (Assist).
 *
 * Sent when the user triggers the voice assistant from the frontend UI.
 * No response is expected for this message.
 */
@Serializable
@SerialName("assist/show")
data class OpenAssistMessage(override val id: Int? = null, val payload: OpenAssistPayload = OpenAssistPayload()) :
    IncomingExternalBusMessage

@Serializable
data class OpenAssistPayload(
    @SerialName("pipeline_id") val pipelineId: String? = null,
    @SerialName("start_listening") val startListening: Boolean = true,
)

/**
 * Message requesting haptic feedback from the Home Assistant frontend.
 *
 * Sent when the user interacts with UI elements in the frontend that provide
 * tactile feedback (e.g., toggling a switch, long-pressing an entity).
 * This is a fire-and-forget message — no response is expected.
 */
@Serializable
@SerialName("haptic")
data class HapticMessage(override val id: Int? = null, val payload: HapticType) : IncomingExternalBusMessage

/**
 * Message requesting the app to open the NFC tag-write flow.
 *
 * The optional [TagWritePayload.tag] is a pre-filled tag identifier. When null or missing, the
 * user is prompted to enter/scan a tag manually. Once handled, a [io.homeassistant.companion.android.frontend.externalbus.outgoing.ResultMessage.success]
 * should be sent back to the frontend with the [id].
 */
@Serializable
@SerialName("tag/write")
data class TagWriteMessage(override val id: Int? = null, val payload: TagWritePayload = TagWritePayload()) :
    IncomingExternalBusMessage

@Serializable
data class TagWritePayload(val tag: String? = null)

/**
 * Message carrying blob data for a file download initiated by the frontend.
 *
 * Sent internally by JavaScript injected in
 * [io.homeassistant.companion.android.frontend.download.FrontendDownloadManager] via the external bus callback.
 * The blob is read as a data URI and passed in [data], along with a [filename] derived from the
 * original URL's content disposition or MIME type.
 */
@Serializable
@SerialName("handleBlob")
data class HandleBlobMessage(override val id: Int? = null, val data: String, val filename: String) :
    IncomingExternalBusMessage

/**
 * Message requesting to start playing an HLS stream via ExoPlayer.
 *
 * The frontend provides the stream URL and an optional muted flag.
 * The app should respond with a result message on success.
 */
@Serializable
@SerialName("exoplayer/play_hls")
data class ExoPlayerPlayHlsMessage(
    override val id: Int? = null,
    val payload: ExoPlayerPlayHlsPayload = ExoPlayerPlayHlsPayload(),
) : IncomingExternalBusMessage

@Serializable
data class ExoPlayerPlayHlsPayload(val url: String? = null, val muted: Boolean = false)

/**
 * Message requesting to stop ExoPlayer playback and release the player.
 *
 * This is a fire-and-forget message.
 */
@Serializable
@SerialName("exoplayer/stop")
data class ExoPlayerStopMessage(override val id: Int? = null) : IncomingExternalBusMessage

/**
 * Message requesting to resize and reposition the ExoPlayer overlay.
 *
 * Payload values come from `Element.getBoundingClientRect()` and are already scaled
 * to screen coordinates.
 */
@Serializable
@SerialName("exoplayer/resize")
data class ExoPlayerResizeMessage(
    override val id: Int? = null,
    val payload: ExoPlayerResizePayload = ExoPlayerResizePayload(),
) : IncomingExternalBusMessage

@Serializable
data class ExoPlayerResizePayload(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
)

/**
 * Message requesting the app to start scanning for nearby BLE devices that advertise the
 * Improv Wi-Fi service.
 *
 * The frontend sends this once when the user opens its "Add device" flow. The app responds
 * out-of-band with a stream of [io.homeassistant.companion.android.frontend.externalbus.outgoing.ImprovDiscoveredDeviceMessage]
 * commands as devices are discovered. No `result`-shaped response is expected.
 *
 * Will not be sent by the frontend when the device reports
 * [io.homeassistant.companion.android.frontend.externalbus.outgoing.ConfigResult.canSetupImprov] = `false`.
 */
@Serializable
@SerialName("improv/scan")
data class ImprovScanMessage(override val id: Int? = null) : IncomingExternalBusMessage

/**
 * Message requesting the app to begin Wi-Fi onboarding for the named improv device the user
 * picked from the discovery list.
 *
 * The app should open the credentials dialog for [ImprovConfigureDevicePayload.name], submit
 * the entered Wi-Fi credentials over BLE, and finally — once the device reports it has been
 * provisioned — emit
 * [io.homeassistant.companion.android.frontend.externalbus.outgoing.ImprovDeviceSetupDoneMessage]
 * and navigate the frontend to the matching `config_flow_start` URL.
 */
@Serializable
@SerialName("improv/configure_device")
data class ImprovConfigureDeviceMessage(override val id: Int? = null, val payload: ImprovConfigureDevicePayload) :
    IncomingExternalBusMessage

@Serializable
data class ImprovConfigureDevicePayload(val name: String)

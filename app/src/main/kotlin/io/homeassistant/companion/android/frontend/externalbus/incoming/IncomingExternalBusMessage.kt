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
            polymorphicDefaultDeserializer(IncomingExternalBusMessage::class) {
                object : UnknownJsonContentDeserializer<UnknownIncomingMessage>() {
                    override val builder = UnknownJsonContentBuilder { content ->
                        UnknownIncomingMessage(content)
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
data class UnknownIncomingMessage(override val content: JsonElement) :
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
 * Message requesting the app's to open its settings.
 * No response is expected for this message
 */
@Serializable
@SerialName("config_screen/show")
data class OpenSettingsMessage(override val id: Int? = null) : IncomingExternalBusMessage

/**
 * Message indicating that the frontend theme has changed.
 *
 * Sent when the user changes the theme in the frontend, allowing the app
 * to update the status bar and navigation bar colors to match.
 */
@Serializable
@SerialName("theme-update")
data class ThemeUpdateMessage(override val id: Int? = null) : IncomingExternalBusMessage

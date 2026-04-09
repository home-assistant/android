package io.homeassistant.companion.android.frontend.externalbus.incoming

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.modules.SerializersModule

/**
 * Represents the haptic feedback types sent by the Home Assistant frontend.
 *
 * Each type maps to a specific Android haptic feedback constant or vibration pattern.
 * The frontend sends these as string identifiers via the external bus `haptic` message.
 *
 * Unknown haptic types from newer frontend versions are deserialized as [Unknown]
 * instead of failing, allowing graceful forward compatibility.
 *
 * @see <a href="https://developers.home-assistant.io/docs/frontend/external-bus/#trigger-haptic-haptic">Haptic feedback documentation</a>
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("hapticType")
sealed interface HapticType {
    @Serializable
    @SerialName("success")
    data object Success : HapticType

    @Serializable
    @SerialName("warning")
    data object Warning : HapticType

    @Serializable
    @SerialName("failure")
    data object Failure : HapticType

    @Serializable
    @SerialName("light")
    data object Light : HapticType

    @Serializable
    @SerialName("medium")
    data object Medium : HapticType

    @Serializable
    @SerialName("heavy")
    data object Heavy : HapticType

    @Serializable
    @SerialName("selection")
    data object Selection : HapticType

    @Serializable data object Unknown : HapticType

    companion object {
        internal val serializersModule = SerializersModule {
            polymorphicDefaultDeserializer(HapticType::class) { Unknown.serializer() }
        }
    }
}

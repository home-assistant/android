package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

private const val AREA = "area"
private const val DEVICE = "device"
private const val PARENT_DEVICE = "parent_device"

/**
 * Next owner in the naming context of a registry entry (its `next_name_part`), walking up from the
 * entity: the naming context goes on to the device or the parent device, and ends at the first
 * entry with an area of its own. Available since Home Assistant 2026.10
 * (https://github.com/home-assistant/core/pull/181551).
 */
@Serializable(with = NextNamePartSerializer::class)
sealed interface NextNamePart {
    /** The entry has an area of its own, the naming context ends with it. */
    data object Area : NextNamePart

    /** The device of the entity. */
    data object Device : NextNamePart

    /** The parent device of the child device. */
    data object ParentDevice : NextNamePart

    /** A part sent by the server that the app doesn't know. */
    data class Unknown(val value: String) : NextNamePart
}

private object NextNamePartSerializer : KSerializer<NextNamePart> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NextNamePart", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NextNamePart) = encoder.encodeString(
        when (value) {
            NextNamePart.Area -> AREA
            NextNamePart.Device -> DEVICE
            NextNamePart.ParentDevice -> PARENT_DEVICE
            is NextNamePart.Unknown -> value.value
        },
    )

    override fun deserialize(decoder: Decoder): NextNamePart = when (val value = decoder.decodeString()) {
        AREA -> NextNamePart.Area
        DEVICE -> NextNamePart.Device
        PARENT_DEVICE -> NextNamePart.ParentDevice
        else -> NextNamePart.Unknown(value)
    }
}

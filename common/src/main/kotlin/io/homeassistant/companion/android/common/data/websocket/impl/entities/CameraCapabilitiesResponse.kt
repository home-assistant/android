package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

private const val HLS = "hls"
private const val WEB_RTC = "web_rtc"

/**
 * Response for the `camera/capabilities` WebSocket command.
 *
 * The server reports which stream types that can be used for a camera entity. Consumers
 * should check for [CameraStreamType.WebRtc] before starting a WebRTC session and fall back to
 * HLS otherwise.
 */
@Serializable
data class CameraCapabilitiesResponse(val frontendStreamTypes: List<CameraStreamType> = emptyList())

/**
 * Stream type that can be uses for a camera entity, matching the `StreamType` enum of
 * Home Assistant Core.
 */
@Serializable(with = CameraStreamTypeSerializer::class)
sealed interface CameraStreamType {
    /** HTTP Live Streaming. */
    data object Hls : CameraStreamType

    /** WebRTC peer connection. */
    data object WebRtc : CameraStreamType

    /** A stream type the app doesn't know. */
    data class Unknown(val value: String) : CameraStreamType
}

private object CameraStreamTypeSerializer : KSerializer<CameraStreamType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CameraStreamType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CameraStreamType) = encoder.encodeString(
        when (value) {
            CameraStreamType.Hls -> HLS
            CameraStreamType.WebRtc -> WEB_RTC
            is CameraStreamType.Unknown -> value.value
        },
    )

    override fun deserialize(decoder: Decoder): CameraStreamType = when (val value = decoder.decodeString()) {
        HLS -> CameraStreamType.Hls
        WEB_RTC -> CameraStreamType.WebRtc
        else -> CameraStreamType.Unknown(value)
    }
}

package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

/**
 * Response for the `camera/capabilities` WebSocket command.
 *
 * The server reports which stream types the frontend can use for a camera entity. Consumers
 * should check for [CameraStreamTypes.WEB_RTC] before starting a WebRTC session and fall back to
 * HLS otherwise.
 */
@Serializable
data class CameraCapabilitiesResponse(val frontendStreamTypes: List<String> = emptyList())

/**
 * Known values of [CameraCapabilitiesResponse.frontendStreamTypes], matching the
 * `StreamType` enum of Home Assistant Core.
 */
object CameraStreamTypes {
    const val HLS = "hls"
    const val WEB_RTC = "web_rtc"
}

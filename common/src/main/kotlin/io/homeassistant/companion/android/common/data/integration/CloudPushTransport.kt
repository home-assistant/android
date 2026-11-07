package io.homeassistant.companion.android.common.data.integration

/**
 * Where Home Assistant sends push notifications when it cannot reach the device over the
 * WebSocket channel.
 *
 * This is independent of the WebSocket channel itself (`push_websocket_channel`, see
 * [DeviceRegistration.pushWebsocket]): Home Assistant prefers the WebSocket channel whenever the
 * device is connected and only falls back to the transport described here.
 */
sealed interface CloudPushTransport {

    /**
     * Firebase Cloud Messaging, reached through the push proxy built into the app. This is the
     * transport used when no other one registered an endpoint.
     */
    data object Firebase : CloudPushTransport

    /**
     * A push endpoint owned by another transport, for example a distributor chosen by the user.
     *
     * @property url Absolute URL Home Assistant posts the notification to. Home Assistant validates
     * it as a URL and rejects the registration otherwise, see
     * [SCHEMA_APP_DATA](https://github.com/home-assistant/core/blob/14f9b7e699d6de2b33901a3decb6a0ca9c8c21df/homeassistant/components/mobile_app/const.py#L105-L117).
     */
    data class Endpoint(val url: String) : CloudPushTransport {
        init {
            require(url.isNotBlank()) { "A push endpoint URL cannot be blank" }
        }
    }
}

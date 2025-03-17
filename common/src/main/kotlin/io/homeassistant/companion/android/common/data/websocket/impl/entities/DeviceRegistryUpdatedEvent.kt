package io.homeassistant.companion.android.common.data.websocket.impl.entities

data class DeviceRegistryUpdatedEvent(
    val action: String,
    val deviceId: String
)

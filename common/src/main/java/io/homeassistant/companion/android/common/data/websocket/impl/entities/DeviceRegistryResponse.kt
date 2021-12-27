package io.homeassistant.companion.android.common.data.websocket.impl.entities

data class DeviceRegistryResponse(
    val areaId: String?,
    val configurationUrl: String?,
    val configEntries: List<String>,
    val connections: List<List<String>>,
    val disabledBy: String?,
    val entryType: String?,
    val id: String,
    val identifiers: List<List<String>>,
    val manufacturer: String?,
    val model: String?,
    val nameByUser: String?,
    val name: String?,
    val swVersion: String?,
    val viaDeviceId: String?
)

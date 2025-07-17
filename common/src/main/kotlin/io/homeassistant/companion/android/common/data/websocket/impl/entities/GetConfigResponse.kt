package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.MapAnySerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

@Serializable
data class GetConfigResponse(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val unitSystem: Map<String, String>,
    val locationName: String,
    val timeZone: String,
    val components: List<String>,
    val version: String,
    // only when using webhook
    val cloudhookUrl: String? = null,
    // only when using webhook
    val remoteUiUrl: String? = null,
    // only on core >= 2022.6 when using webhook
    @Serializable(with = MapAnySerializer::class)
    val entities: Map<String, Map<String, @Polymorphic Any>>? = null,
    // only on core >= 2024.7.2 when using webhook
    val hassDeviceId: String? = null,
)

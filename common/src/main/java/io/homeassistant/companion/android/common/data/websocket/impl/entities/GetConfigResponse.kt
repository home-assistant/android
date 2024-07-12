package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
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
    val cloudhookUrl: String?,
    // only when using webhook
    val remoteUiUrl: String?,
    // only on core >= 2022.6 when using webhook
    val entities: Map<String, Map<String, Any>>?,
    // only on core >= 2024.7.2 when using webhook
    val hassDeviceId: String?
)

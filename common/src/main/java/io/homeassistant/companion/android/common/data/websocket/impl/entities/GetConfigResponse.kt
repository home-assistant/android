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
    val entities: Map<String, Map<String, Any>>? // only on core >= 2022.6 when using webhook
)

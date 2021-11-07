package io.homeassistant.companion.android.common.data.integration.impl.entities

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
    // This doesn't come back from web socket
    val themeColor: String?
)

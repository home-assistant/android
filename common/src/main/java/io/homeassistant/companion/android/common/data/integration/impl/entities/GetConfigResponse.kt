package io.homeassistant.companion.android.common.data.integration.impl.entities

data class GetConfigResponse(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val unitSystem: HashMap<String, String>,
    val locationName: String,
    val timeZone: String,
    val components: Array<String>,
    val version: String,
    val themeColor: String
)

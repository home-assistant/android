package io.homeassistant.companion.android.common.data.integration.impl.entities

data class UpdateLocationRequest(
    val locationName: String,
    val gps: Array<Double>,
    val gpsAccuracy: Int,
    val speed: Int,
    val altitude: Int,
    val course: Int,
    val verticalAccuracy: Int
)

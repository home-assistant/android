package io.homeassistant.companion.android.common.data.integration

data class UpdateLocation(
    val gps: Array<Double>,
    val gpsAccuracy: Int,
    val speed: Int,
    val altitude: Int,
    val course: Int,
    val verticalAccuracy: Int
)

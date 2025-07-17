package io.homeassistant.companion.android.common.data.integration

data class UpdateLocation(
    val gps: List<Double>?,
    val gpsAccuracy: Int?,
    val locationName: String?,
    val speed: Int?,
    val altitude: Int?,
    val course: Int?,
    val verticalAccuracy: Int?,
)

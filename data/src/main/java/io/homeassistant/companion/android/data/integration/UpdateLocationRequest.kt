package io.homeassistant.companion.android.data.integration

data class UpdateLocationRequest(
    val locationName: String,
    val gps: Array<Double>,
    val gpsAccuracy: Int,
    val battery: Int?,
    val speed: Int,
    val altitude: Int,
    val course: Int,
    val verticalAccuracy: Int?
)

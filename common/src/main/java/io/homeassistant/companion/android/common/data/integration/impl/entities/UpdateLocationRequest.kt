package io.homeassistant.companion.android.common.data.integration.impl.entities

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateLocationRequest(
    val gps: Array<Double>?,
    val gpsAccuracy: Int?,
    val locationName: String?,
    val speed: Int?,
    val altitude: Int?,
    val course: Int?,
    val verticalAccuracy: Int?
)

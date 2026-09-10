package io.homeassistant.companion.android.common.data.integration

data class UpdateLocation(
    val gps: List<Double>?,
    val gpsAccuracy: Int?,
    /**
     * Deprecated in core 2026.6.0+; use [inZones] instead. Kept for backwards compatibility.
     */
    val locationName: String?,
    /**
     * Full zone entity IDs (e.g. `zone.home`) the device is currently in. Replaces
     * [locationName] in core 2026.6.0+, should be `null` when using core <2026.6.0.
     */
    val inZones: List<String>?,
    val speed: Int?,
    val altitude: Int?,
    val course: Int?,
    val verticalAccuracy: Int?,
)

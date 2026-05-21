package io.homeassistant.companion.android.common.data.integration

data class UpdateLocation(
    val gps: List<Double>?,
    val gpsAccuracy: Int?,
    /**
     * Deprecated by the Home Assistant architecture decision in
     * https://github.com/home-assistant/architecture/discussions/1387 send [inZones] instead.
     * Kept for backwards compatibility with Core servers that pre-date the deprecation.
     */
    val locationName: String?,
    /**
     * Full zone entity IDs (e.g. `zone.home`) the device is currently in. Replaces
     * [locationName] per architecture discussion https://github.com/home-assistant/architecture/discussions/1387.
     */
    val inZones: List<String>?,
    val speed: Int?,
    val altitude: Int?,
    val course: Int?,
    val verticalAccuracy: Int?,
)

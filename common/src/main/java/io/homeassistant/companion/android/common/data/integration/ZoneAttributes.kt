package io.homeassistant.companion.android.common.data.integration

import android.location.Location

data class ZoneAttributes(
    val hidden: Boolean,
    val passive: Boolean,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val friendlyName: String,
    val icon: String?
)

/**
 * Returns if the provided location is estimated to be in the zone.
 * This function will also consider accuracy, so if the GPS location is outside the zone but the
 * accuracy suggests that it could be in the zone, this function will still return `true`.
 */
fun Entity<ZoneAttributes>.containsWithAccuracy(location: Location): Boolean {
    val zoneCenter = Location("").apply {
        latitude = attributes.latitude
        longitude = attributes.longitude
    }
    return (location.distanceTo(zoneCenter) - attributes.radius - location.accuracy.coerceAtLeast(0f)) <= 0
}

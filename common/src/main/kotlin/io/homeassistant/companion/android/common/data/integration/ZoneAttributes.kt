package io.homeassistant.companion.android.common.data.integration

import android.location.Location

/**
 * Returns if the provided location is estimated to be in the zone.
 * This function will also consider accuracy, so if the GPS location is outside the zone but the
 * accuracy suggests that it could be in the zone, this function will still return `true`.
 */
// TODO Introduce back ZoneAttribute class https://github.com/home-assistant/android/issues/5340
fun Entity.containsWithAccuracy(location: Location): Boolean {
    val zoneCenter = Location("").apply {
        latitude = (attributes["latitude"] as Number).toDouble()
        longitude = (attributes["longitude"] as Number).toDouble()
    }
    return (
        location.distanceTo(zoneCenter) - (attributes["radius"] as Number).toFloat() -
            location.accuracy.coerceAtLeast(0f)
        ) <=
        0
}

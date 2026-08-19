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

/**
 * Zones the device is in, from GPS (with accuracy) plus currently entered geofences.
 *
 * Geofence request IDs use `{serverId}_{entityId}` or `{serverId}_{entityId}_expanded`.
 * Only IDs that match a configured zone entity ID are kept, so the app-internal
 * `_expanded` high-accuracy geofences are ignored.
 *
 * Results are sorted by radius (smallest first), then by distance to the zone center.
 */
fun resolveInZones(
    location: Location,
    configuredZones: List<Entity>,
    enteredGeofenceRequestIds: Collection<String>,
    serverId: Int,
): List<Entity> {
    val configuredByEntityId = configuredZones.associateBy { it.entityId }
    val fromGps = configuredZones.filter { zone ->
        val radius = zone.attributes["radius"] as? Number
        radius != null && zone.containsWithAccuracy(location)
    }
    val geofencePrefix = "${serverId}_"
    val fromGeofence = enteredGeofenceRequestIds.mapNotNull { requestId ->
        if (!requestId.startsWith(geofencePrefix)) return@mapNotNull null
        configuredByEntityId[requestId.removePrefix(geofencePrefix)]
    }
    return (fromGps + fromGeofence)
        .distinctBy { it.entityId }
        .sortedWith(
            compareBy<Entity> { (it.attributes["radius"] as? Number ?: Int.MAX_VALUE).toFloat() }
                .thenBy { distanceToZoneCenter(location, it) },
        )
}

/**
 * Distance in meters between [location] and the center of [zone]. Returns [Float.MAX_VALUE] when
 * the zone has no coordinates so it sorts last.
 */
private fun distanceToZoneCenter(location: Location, zone: Entity): Float {
    val zoneLatitude = (zone.attributes["latitude"] as? Number)?.toDouble()
    val zoneLongitude = (zone.attributes["longitude"] as? Number)?.toDouble()
    if (zoneLatitude == null || zoneLongitude == null) return Float.MAX_VALUE

    val results = FloatArray(1)
    Location.distanceBetween(location.latitude, location.longitude, zoneLatitude, zoneLongitude, results)
    return results[0]
}

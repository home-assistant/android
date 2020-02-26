package io.homeassistant.companion.android.sensors

import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import io.homeassistant.companion.android.domain.integration.Sensor
import io.homeassistant.companion.android.domain.integration.SensorRegistration

class GeocodeSensorManager : SensorManager {
    override fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>> {
        val sensor = getGeocodedLocation(context)
        if (sensor != null) {
            return listOf(
                SensorRegistration<Any>(
                    sensor,
                    "Geocoded Location"
                )
            )
        }
        return emptyList()
    }

    override fun getSensors(context: Context): List<Sensor<Any>> {
        val geocodedSensor = getGeocodedLocation(context)

        if (geocodedSensor != null) {
            return listOf(geocodedSensor)
        }

        return emptyList()
    }

    private fun getGeocodedLocation(context: Context): Sensor<Any>? {
        Tasks.await(LocationServices.getFusedLocationProviderClient(context).lastLocation)?.let {
            Geocoder(context)
                .getFromLocation(it.latitude, it.longitude, 1)
                .firstOrNull()?.let { address ->
                    return Sensor(
                        "geocoded_location",
                        if (address.maxAddressLineIndex >= 0) address.getAddressLine(0) else "Unknown",
                        "sensor",
                        "mdi:map",
                        mapOf(
                            "Administrative Area" to address.adminArea,
                            "Country" to address.countryName,
                            "ISO Country Code" to address.countryCode,
                            "Locality" to address.locality,
                            "Location" to listOf(address.latitude, address.longitude),
                            "Postal Code" to address.postalCode,
                            "Sub Administrative Area" to address.subAdminArea,
                            "Sub Locality" to address.subLocality,
                            "Sub Thoroughfare" to address.subThoroughfare,
                            "Thoroughfare" to address.thoroughfare
                        )
                    )
                }
        }
        return null
    }
}

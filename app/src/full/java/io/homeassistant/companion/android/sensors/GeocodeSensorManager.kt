package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import io.homeassistant.companion.android.domain.integration.SensorRegistration

class GeocodeSensorManager : SensorManager {

    companion object {
        private const val TAG = "GeocodeSM"
        private val geocodedLocation = SensorManager.BasicSensor(
            "geocoded_location",
            "sensor",
            "Geocoded Location"
        )
    }

    override val name: String
        get() = "Geolocation Sensors"
    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(geocodedLocation)

    override fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun getSensorData(
        context: Context,
        sensorId: String
    ): SensorRegistration<Any> {
        return when (sensorId) {
            geocodedLocation.id -> getGeocodedLocation(context)
            else -> throw IllegalArgumentException("Unknown sensorId: $sensorId")
        }
    }

    private fun getGeocodedLocation(context: Context): SensorRegistration<Any> {
        var address: Address? = null
        if (checkPermission(context)) {
            try {
                val locApi = LocationServices.getFusedLocationProviderClient(context)
                Tasks.await(locApi.lastLocation)?.let {
                    if (it.accuracy <= LocationBroadcastReceiver.MINIMUM_ACCURACY)
                        address = Geocoder(context)
                            .getFromLocation(it.latitude, it.longitude, 1)
                            .firstOrNull()
                }
            } catch (e: Exception) {
                // We don't want to crash if the device cannot get a geocoded location
                Log.e(TAG, "Issue getting geocoded location ", e)
            }
        }

        val attributes = address?.let {
            mapOf(
                "Administrative Area" to it.adminArea,
                "Country" to it.countryName,
                "ISO Country Code" to it.countryCode,
                "Locality" to it.locality,
                "Location" to listOf(it.latitude, it.longitude),
                "Postal Code" to it.postalCode,
                "Sub Administrative Area" to it.subAdminArea,
                "Sub Locality" to it.subLocality,
                "Sub Thoroughfare" to it.subThoroughfare,
                "Thoroughfare" to it.thoroughfare
            )
        }.orEmpty()

        return geocodedLocation.toSensorRegistration(
            address?.getAddressLine(0) ?: "Unknown",
            "mdi:map",
            attributes
        )
    }
}

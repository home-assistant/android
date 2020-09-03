package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.google.android.gms.location.LocationServices
import io.homeassistant.companion.android.R
import java.lang.Exception

class GeocodeSensorManager : SensorManager {

    companion object {
        private const val TAG = "GeocodeSM"
        val geocodedLocation = SensorManager.BasicSensor(
            "geocoded_location",
            "sensor",
            R.string.basic_sensor_name_geolocation,
            R.string.sensor_description_geocoded_location
        )
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_geolocation
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

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateGeocodedLocation(context)
    }

    private fun updateGeocodedLocation(context: Context) {
        if (!isEnabled(context, geocodedLocation.id) || !checkPermission(context))
            return
        val locApi = LocationServices.getFusedLocationProviderClient(context)
        locApi.lastLocation.addOnSuccessListener { location ->
            var address: Address? = null
            try {
                if (location == null) {
                    Log.e(TAG, "Somehow location is null even though it was successful")
                    return@addOnSuccessListener
                }

                if (location.accuracy <= LocationSensorManager.MINIMUM_ACCURACY)
                    address = Geocoder(context)
                        .getFromLocation(location.latitude, location.longitude, 1)
                        .firstOrNull()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get geocoded location", e)
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

            onSensorUpdated(
                context,
                geocodedLocation,
                address?.getAddressLine(0) ?: "Unknown",
                "mdi:map",
                attributes
            )
        }
    }
}

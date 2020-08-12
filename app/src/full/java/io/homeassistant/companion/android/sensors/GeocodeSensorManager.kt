package io.homeassistant.companion.android.sensors

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import io.homeassistant.companion.android.util.PermissionManager

class GeocodeSensorManager : SensorManager {

    companion object {
        private const val TAG = "GeocodeSM"
    }

    override val name: String
        get() = "Geolocation Sensors"

    override fun requiredPermissions(): Array<String> {
        return PermissionManager.getLocationPermissionArray()
    }

    override fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>> {
        return listOf(getGeocodedLocation(context))
    }

    private fun getGeocodedLocation(context: Context): SensorRegistration<Any> {
        var address: Address? = null
        if (PermissionManager.checkLocationPermission(context)) {
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

        return SensorRegistration(
            "geocoded_location",
            address?.getAddressLine(0) ?: "Unknown",
            "sensor",
            "mdi:map",
            attributes,
            "Geocoded Location"
        )
    }
}

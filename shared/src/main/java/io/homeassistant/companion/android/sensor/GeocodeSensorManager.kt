package io.homeassistant.companion.android.sensor

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.location.LocationServices
import io.homeassistant.companion.android.background.LocationBroadcastReceiver.Companion.MINIMUM_ACCURACY
import io.homeassistant.companion.android.domain.integration.Sensor
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import io.homeassistant.companion.android.util.extensions.PermissionManager
import io.homeassistant.companion.android.util.extensions.await
import io.homeassistant.companion.android.util.extensions.catch

class GeocodeSensorManager(private val context: Context) : SensorManager {

    companion object {
        private const val TAG = "GeocodeSM"
    }

    override suspend fun getSensorRegistrations(): List<SensorRegistration<Any>> {
        val sensor = getGeocodedLocation() ?: return emptyList()
        return listOf(SensorRegistration(sensor, "Geocoded Location"))
    }

    override suspend fun getSensors(): List<Sensor<Any>> {
        return listOf(getGeocodedLocation() ?: return emptyList())
    }

    private suspend fun getGeocodedLocation(): Sensor<Any>? {
        if (!PermissionManager.checkLocationPermissions(context)) {
            Log.w(TAG, "Tried getting gecoded location without permission.")
            return null
        }
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val lastLocation = catch { fusedClient.lastLocation.await() }
        if (lastLocation == null || lastLocation.accuracy > MINIMUM_ACCURACY) {
            return null
        }

        val geocoder = Geocoder(context)
        val address = catch {
            geocoder.getFromLocation(lastLocation.latitude, lastLocation.longitude, 1).firstOrNull()
        } ?: return null

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

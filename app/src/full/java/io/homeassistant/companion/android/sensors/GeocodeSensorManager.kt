package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.google.android.gms.location.LocationServices
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.location.HighAccuracyLocationService
import io.homeassistant.companion.android.common.R as commonR

class GeocodeSensorManager : SensorManager {

    companion object {
        private const val SETTING_ACCURACY = "geocode_minimum_accuracy"
        private const val DEFAULT_MINIMUM_ACCURACY = 200
        private const val TAG = "GeocodeSM"
        val geocodedLocation = SensorManager.BasicSensor(
            "geocoded_location",
            "sensor",
            commonR.string.basic_sensor_name_geolocation,
            commonR.string.sensor_description_geocoded_location,
            "mdi:map"
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#geocoded-location-sensor"
    }
    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = commonR.string.sensor_name_geolocation
    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(geocodedLocation)
    }

    override fun hasSensor(context: Context): Boolean {
        return Geocoder.isPresent()
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
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
        if (!isEnabled(context, geocodedLocation.id) || !checkPermission(context, geocodedLocation.id))
            return
        val locApi = LocationServices.getFusedLocationProviderClient(context)
        locApi.lastLocation.addOnSuccessListener { location ->
            var address: Address? = null
            try {
                if (location == null) {
                    Log.e(TAG, "Somehow location is null even though it was successful")
                    return@addOnSuccessListener
                }

                val sensorDao = AppDatabase.getInstance(context).sensorDao()
                val sensorSettings = sensorDao.getSettings(geocodedLocation.id)
                val minAccuracy = sensorSettings
                    .firstOrNull { it.name == SETTING_ACCURACY }?.value?.toIntOrNull()
                    ?: DEFAULT_MINIMUM_ACCURACY
                sensorDao.add(SensorSetting(geocodedLocation.id, SETTING_ACCURACY, minAccuracy.toString(), SensorSettingType.NUMBER))

                if (location.accuracy <= minAccuracy) {
                    address = Geocoder(context)
                        .getFromLocation(location.latitude, location.longitude, 1)
                        ?.firstOrNull()
                } else {
                    Log.w(TAG, "Skipping geocoded update as accuracy was not met: ${location.accuracy}")
                    return@addOnSuccessListener
                }

                val now = System.currentTimeMillis()
                if (now - location.time > 300000) {
                    Log.w(TAG, "Skipping geocoded update due to old timestamp ${location.time} compared to $now")
                    return@addOnSuccessListener
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get geocoded location", e)
            }
            val attributes = address?.let {
                mapOf(
                    "administrative_area" to it.adminArea,
                    "country" to it.countryName,
                    "iso_country_code" to it.countryCode,
                    "locality" to it.locality,
                    "location" to listOf(it.latitude, it.longitude),
                    "name" to it.featureName,
                    "phone" to it.phone,
                    "premises" to it.premises,
                    "postal_code" to it.postalCode,
                    "sub_administrative_area" to it.subAdminArea,
                    "sub_locality" to it.subLocality,
                    "sub_thoroughfare" to it.subThoroughfare,
                    "thoroughfare" to it.thoroughfare,
                    "url" to it.url
                )
            }.orEmpty()

            val prettyAddress = address?.getAddressLine(0)

            HighAccuracyLocationService.updateNotificationAddress(context, location, if (!prettyAddress.isNullOrEmpty()) prettyAddress else context.getString(commonR.string.unknown_address))

            onSensorUpdated(
                context,
                geocodedLocation,
                if (!prettyAddress.isNullOrEmpty()) prettyAddress else "Unknown",
                geocodedLocation.statelessIcon,
                attributes
            )
        }
    }
}

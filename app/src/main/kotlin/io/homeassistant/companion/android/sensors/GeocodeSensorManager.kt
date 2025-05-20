package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.instant
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.location.HighAccuracyLocationService
import io.homeassistant.companion.android.location.getLastLocation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import timber.log.Timber

class GeocodeSensorManager : SensorManager {

    companion object {
        private const val SETTING_ACCURACY = "geocode_minimum_accuracy"
        const val SETTINGS_INCLUDE_LOCATION = "geocode_include_location_updates"
        private const val DEFAULT_MINIMUM_ACCURACY = 200
        val LOCATION_OUTDATED_THRESHOLD = 5.minutes
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
    override val name: Int
        get() = commonR.string.sensor_name_geolocation
    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(geocodedLocation)
    }

    override fun hasSensor(context: Context): Boolean {
        return Geocoder.isPresent()
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return if (SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override suspend fun requestSensorUpdate(
        context: Context
    ) {
        updateGeocodedLocation(context)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun updateGeocodedLocation(context: Context) {
        if (!isEnabled(context, geocodedLocation) || !checkPermission(context, geocodedLocation.id)) {
            return
        }

        val location = getLastLocation(context)
        if (location == null) {
            Timber.w("No location skipping geocoded update")
            return
        }

        var address: Address? = null
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val sensorSettings = sensorDao.getSettings(geocodedLocation.id)
        val minAccuracy = sensorSettings
            .firstOrNull { it.name == SETTING_ACCURACY }?.value?.toIntOrNull()
            ?: DEFAULT_MINIMUM_ACCURACY
        sensorDao.add(SensorSetting(geocodedLocation.id, SETTING_ACCURACY, minAccuracy.toString(), SensorSettingType.NUMBER))

        if (location.accuracy <= minAccuracy) {
            address = Geocoder(context)
                .getFromLocationAwait(location.latitude, location.longitude, 1)
                .firstOrNull()
        } else {
            Timber.w("Skipping geocoded update as accuracy was not met: ${location.accuracy}")
            return
        }

        if (Clock.System.now() - location.instant() > LOCATION_OUTDATED_THRESHOLD) {
            Timber.w("Skipping geocoded update due to old timestamp ${location.instant()}")
            return
        }

        val prettyAddress = address?.getAddressLine(0)

        HighAccuracyLocationService.updateNotificationAddress(
            context,
            location,
            if (!prettyAddress.isNullOrEmpty()) prettyAddress else context.getString(commonR.string.unknown_address)
        )

        onSensorUpdated(
            context,
            geocodedLocation,
            if (!prettyAddress.isNullOrEmpty()) prettyAddress else STATE_UNKNOWN,
            geocodedLocation.statelessIcon,
            address?.toMap().orEmpty()
        )
    }

    private fun Address.toMap(): Map<String, Any> {
        return mapOf(
            "administrative_area" to adminArea,
            "country" to countryName,
            "iso_country_code" to countryCode,
            "locality" to locality,
            "location" to listOf(latitude, longitude),
            "name" to featureName,
            "phone" to phone,
            "premises" to premises,
            "postal_code" to postalCode,
            "sub_administrative_area" to subAdminArea,
            "sub_locality" to subLocality,
            "sub_thoroughfare" to subThoroughfare,
            "thoroughfare" to thoroughfare,
            "url" to url
        )
    }

    /**
     * Returns an array of Addresses that attempt to describe the area immediately surrounding the given latitude and longitude.
     * The returned addresses should be localized for the locale provided to this class's constructor.
     * https://developer.android.com/reference/android/location/Geocoder#getFromLocation(double,%20double,%20int)
     */
    private suspend fun Geocoder.getFromLocationAwait(latitude: Double, longitude: Double, maxResults: Int): List<Address> {
        return if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCoroutine { cont ->
                getFromLocation(
                    latitude,
                    longitude,
                    maxResults,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: List<Address>) {
                            cont.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            cont.resumeWithException(Exception(errorMessage))
                        }
                    }
                )
            }
        } else {
            @Suppress("DEPRECATION")
            getFromLocation(latitude, longitude, maxResults).orEmpty()
        }
    }
}

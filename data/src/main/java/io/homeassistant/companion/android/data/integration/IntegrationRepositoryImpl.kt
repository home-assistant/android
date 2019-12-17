package io.homeassistant.companion.android.data.integration

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository
import io.homeassistant.companion.android.domain.integration.DeviceRegistration
import io.homeassistant.companion.android.domain.integration.Entity
import io.homeassistant.companion.android.domain.integration.IntegrationRepository
import io.homeassistant.companion.android.domain.integration.UpdateLocation
import io.homeassistant.companion.android.domain.integration.ZoneAttributes
import javax.inject.Inject
import javax.inject.Named
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class IntegrationRepositoryImpl @Inject constructor(
    private val integrationService: IntegrationService,
    private val authenticationRepository: AuthenticationRepository,
    @Named("integration") private val localStorage: LocalStorage,
    @Named("manufacturer") private val manufacturer: String,
    @Named("model")private val model: String,
    @Named("osVersion")private val osVersion: String
) : IntegrationRepository {

    companion object {
        private const val APP_ID = "io.homeassistant.companion.android"
        private const val APP_NAME = "Home Assistant"
        private const val OS_NAME = "Android"
        private const val PUSH_URL = "https://mobile-apps.home-assistant.io/api/sendPushNotification"

        private const val PREF_APP_VERSION = "app_version"
        private const val PREF_DEVICE_NAME = "device_name"
        private const val PREF_PUSH_TOKEN = "push_token"

        private const val PREF_CLOUD_URL = "cloud_url"
        private const val PREF_REMOTE_UI_URL = "remote_ui_url"
        private const val PREF_SECRET = "secret"
        private const val PREF_WEBHOOK_ID = "webhook_id"

        private const val PREF_ZONE_ENABLED = "zone_enabled"
        private const val PREF_BACKGROUND_ENABLED = "background_enabled"
    }

    override suspend fun registerDevice(deviceRegistration: DeviceRegistration) {
        val response =
            integrationService.registerDevice(
                authenticationRepository.buildBearerToken(),
                createRegisterDeviceRequest(deviceRegistration)
            )
        persistDeviceRegistration(deviceRegistration)
        persistDeviceRegistrationResponse(response)
    }

    override suspend fun updateRegistration(deviceRegistration: DeviceRegistration) {
        val request = IntegrationRequest(
            "update_registration",
            createRegisterDeviceRequest(deviceRegistration)
        )
        for (it in getUrls()) {
            try {
                if (integrationService.updateRegistration(it, request).isSuccessful) {
                    persistDeviceRegistration(deviceRegistration)
                    return
                }
            } catch (e: Exception) {
                // Ignore failure until we are out of URLS to try!
            }
        }

        throw IntegrationException()
    }

    override suspend fun getRegistration(): DeviceRegistration {
        return DeviceRegistration(
            localStorage.getString(PREF_APP_VERSION),
            localStorage.getString(PREF_DEVICE_NAME),
            localStorage.getString(PREF_PUSH_TOKEN)
        )
    }

    private suspend fun persistDeviceRegistration(deviceRegistration: DeviceRegistration) {
        if (deviceRegistration.appVersion != null)
            localStorage.putString(PREF_APP_VERSION, deviceRegistration.appVersion)
        if (deviceRegistration.deviceName != null)
            localStorage.putString(PREF_DEVICE_NAME, deviceRegistration.deviceName)
        if (deviceRegistration.pushToken != null)
            localStorage.putString(PREF_PUSH_TOKEN, deviceRegistration.pushToken)
    }

    private suspend fun persistDeviceRegistrationResponse(response: RegisterDeviceResponse) {
        localStorage.putString(PREF_CLOUD_URL, response.cloudhookUrl)
        localStorage.putString(PREF_REMOTE_UI_URL, response.remoteUiUrl)
        localStorage.putString(PREF_SECRET, response.secret)
        localStorage.putString(PREF_WEBHOOK_ID, response.webhookId)
    }

    override suspend fun isRegistered(): Boolean {
        return localStorage.getString(PREF_WEBHOOK_ID) != null
    }

    override suspend fun updateLocation(updateLocation: UpdateLocation) {
        val updateLocationRequest = createUpdateLocation(updateLocation)
        for (it in getUrls()) {
            var wasSuccess = false
            try {
                wasSuccess =
                    integrationService.updateLocation(it, updateLocationRequest).isSuccessful
            } catch (e: Exception) {
                // Ignore failure until we are out of URLS to try!
            }
            // if we had a successful call we can return
            if (wasSuccess)
                return
        }

        throw IntegrationException()
    }

    override suspend fun getZones(): Array<Entity<ZoneAttributes>> {
        val getZonesRequest = IntegrationRequest("get_zones", null)
        var zones: Array<EntityResponse<ZoneAttributes>>? = null
        for (it in getUrls()) {
            try {
                zones = integrationService.getZones(it, getZonesRequest)
            } catch (e: Exception) {
                // Ignore failure until we are out of URLS to try!
            }

            if (zones != null) {
                return createZonesResponse(zones)
            }
        }

        throw IntegrationException()
    }

    override suspend fun setZoneTrackingEnabled(enabled: Boolean) {
        localStorage.putBoolean(PREF_ZONE_ENABLED, enabled)
    }

    override suspend fun isZoneTrackingEnabled(): Boolean {
        return localStorage.getBoolean(PREF_ZONE_ENABLED)
    }

    override suspend fun setBackgroundTrackingEnabled(enabled: Boolean) {
        localStorage.putBoolean(PREF_BACKGROUND_ENABLED, enabled)
    }

    override suspend fun isBackgroundTrackingEnabled(): Boolean {
        return localStorage.getBoolean(PREF_BACKGROUND_ENABLED)
    }

    // https://developers.home-assistant.io/docs/en/app_integration_sending_data.html#short-note-on-instance-urls
    private suspend fun getUrls(): Array<HttpUrl> {
        val retVal = ArrayList<HttpUrl>()
        val webhook = localStorage.getString(PREF_WEBHOOK_ID)

        localStorage.getString(PREF_CLOUD_URL)?.let {
            retVal.add(it.toHttpUrl())
        }

        localStorage.getString(PREF_REMOTE_UI_URL)?.let {
            retVal.add(
                it.toHttpUrl().newBuilder()
                    .addPathSegments("api/webhook/$webhook")
                    .build()
            )
        }

        authenticationRepository.getUrl().toString().let {
            retVal.add(
                it.toHttpUrl().newBuilder()
                    .addPathSegments("api/webhook/$webhook")
                    .build()
            )
        }

        return retVal.toTypedArray()
    }

    private suspend fun createRegisterDeviceRequest(deviceRegistration: DeviceRegistration): RegisterDeviceRequest {
        val oldDeviceRegistration = getRegistration()
        return RegisterDeviceRequest(
            APP_ID,
            APP_NAME,
            deviceRegistration.appVersion ?: oldDeviceRegistration.appVersion,
            deviceRegistration.deviceName ?: oldDeviceRegistration.deviceName,
            manufacturer,
            model,
            OS_NAME,
            osVersion,
            false,
            hashMapOf(
                "push_url" to PUSH_URL,
                "push_token" to (deviceRegistration.pushToken ?: oldDeviceRegistration.pushToken
                ?: "")
            )
        )
    }

    private fun createUpdateLocation(updateLocation: UpdateLocation): IntegrationRequest {
        return IntegrationRequest(
            "update_location",
            UpdateLocationRequest(
                updateLocation.locationName,
                updateLocation.gps,
                updateLocation.gpsAccuracy,
                updateLocation.battery,
                updateLocation.speed,
                updateLocation.altitude,
                updateLocation.course,
                updateLocation.verticalAccuracy
            )
        )
    }

    private fun createZonesResponse(zones: Array<EntityResponse<ZoneAttributes>>): Array<Entity<ZoneAttributes>> {
        val retVal = ArrayList<Entity<ZoneAttributes>>()
        zones.forEach {
            retVal.add(
                Entity(
                    it.entityId,
                    it.state,
                    it.attributes,
                    it.lastChanged,
                    it.lastUpdated,
                    it.context
                )
            )
        }

        return retVal.toTypedArray()
    }
}

package io.homeassistant.companion.android.data.integration

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.data.integration.entities.EntityResponse
import io.homeassistant.companion.android.data.integration.entities.FireEventRequest
import io.homeassistant.companion.android.data.integration.entities.GetConfigResponse
import io.homeassistant.companion.android.data.integration.entities.IntegrationRequest
import io.homeassistant.companion.android.data.integration.entities.RegisterDeviceRequest
import io.homeassistant.companion.android.data.integration.entities.SensorRequest
import io.homeassistant.companion.android.data.integration.entities.ServiceCallRequest
import io.homeassistant.companion.android.data.integration.entities.UpdateLocationRequest
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository
import io.homeassistant.companion.android.domain.integration.DeviceRegistration
import io.homeassistant.companion.android.domain.integration.Entity
import io.homeassistant.companion.android.domain.integration.IntegrationRepository
import io.homeassistant.companion.android.domain.integration.Panel
import io.homeassistant.companion.android.domain.integration.Sensor
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import io.homeassistant.companion.android.domain.integration.Service
import io.homeassistant.companion.android.domain.integration.UpdateLocation
import io.homeassistant.companion.android.domain.integration.ZoneAttributes
import io.homeassistant.companion.android.domain.url.UrlRepository
import javax.inject.Inject
import javax.inject.Named
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class IntegrationRepositoryImpl @Inject constructor(
    private val integrationService: IntegrationService,
    private val authenticationRepository: AuthenticationRepository,
    private val urlRepository: UrlRepository,
    @Named("integration") private val localStorage: LocalStorage,
    @Named("manufacturer") private val manufacturer: String,
    @Named("model") private val model: String,
    @Named("osVersion") private val osVersion: String,
    @Named("deviceId") private val deviceId: String
) : IntegrationRepository {

    companion object {
        private const val APP_ID = "io.homeassistant.companion.android"
        private const val APP_NAME = "Home Assistant"
        private const val OS_NAME = "Android"
        private const val PUSH_URL = "https://mobile-apps.home-assistant.io/api/sendPush/android/v1"

        private const val PREF_APP_VERSION = "app_version"
        private const val PREF_DEVICE_NAME = "device_name"
        private const val PREF_PUSH_TOKEN = "push_token"

        private const val PREF_SECRET = "secret"

        private const val PREF_ZONE_ENABLED = "zone_enabled"
        private const val PREF_BACKGROUND_ENABLED = "background_enabled"
        private const val PREF_FULLSCREEN_ENABLED = "fullscreen_enabled"
        private const val PREF_SESSION_TIMEOUT = "session_timeout"
        private const val PREF_SESSION_EXPIRE = "session_expire"
        private const val PREF_SENSORS_REGISTERED = "sensors_registered"
    }

    override suspend fun registerDevice(deviceRegistration: DeviceRegistration) {
        val request = createUpdateRegistrationRequest(deviceRegistration)
        request.appId = APP_ID
        request.appName = APP_NAME
        request.osName = OS_NAME
        request.supportsEncryption = false

        try {
            val version = integrationService
                .discoveryInfo(authenticationRepository.buildBearerToken())
                .version.split(".")
            // If we are above version 0.104.0 add device_id
            if (version.size > 2 && (Integer.parseInt(version[0]) > 0 || Integer.parseInt(version[1]) >= 104)) {
                request.deviceId = deviceId
            }
        } catch (e: Exception) {
            // Ignore errors we don't technically need it need it
        }

        val response =
            integrationService.registerDevice(
                authenticationRepository.buildBearerToken(),
                request
            )
        persistDeviceRegistration(deviceRegistration)
        urlRepository.saveRegistrationUrls(response.cloudhookUrl, response.remoteUiUrl, response.webhookId)
        localStorage.putString(PREF_SECRET, response.secret)
    }

    override suspend fun updateRegistration(deviceRegistration: DeviceRegistration) {
        val request =
            IntegrationRequest(
                "update_registration",
                createUpdateRegistrationRequest(deviceRegistration)
            )
        for (it in urlRepository.getApiUrls()) {
            try {
                if (integrationService.updateRegistration(it.toHttpUrlOrNull()!!, request).isSuccessful) {
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

    override suspend fun isRegistered(): Boolean {
        return urlRepository.getApiUrls().isNotEmpty()
    }

    override suspend fun updateLocation(updateLocation: UpdateLocation) {
        val updateLocationRequest = createUpdateLocation(updateLocation)
        for (it in urlRepository.getApiUrls()) {
            var wasSuccess = false
            try {
                wasSuccess =
                    integrationService.updateLocation(it.toHttpUrlOrNull()!!, updateLocationRequest).isSuccessful
            } catch (e: Exception) {
                // Ignore failure until we are out of URLS to try!
            }
            // if we had a successful call we can return
            if (wasSuccess)
                return
        }

        throw IntegrationException()
    }

    override suspend fun callService(domain: String, service: String, serviceData: HashMap<String, Any>) {
        var wasSuccess = false

        val serviceCallRequest =
            ServiceCallRequest(
                domain,
                service,
                serviceData
            )

        for (it in urlRepository.getApiUrls()) {
            try {
                wasSuccess =
                    integrationService.callService(
                        it.toHttpUrlOrNull()!!,
                        IntegrationRequest(
                            "call_service",
                            serviceCallRequest
                        )
                    ).isSuccessful
            } catch (e: Exception) {
                // Ignore failure until we are out of URLS to try!
            }
            // if we had a successful call we can return
            if (wasSuccess)
                return
        }

        throw IntegrationException()
    }

    override suspend fun fireEvent(eventType: String, eventData: Map<String, Any>) {
        var wasSuccess = false

        val fireEventRequest = FireEventRequest(eventType, eventData)

        for (it in urlRepository.getApiUrls()) {
            try {
                wasSuccess =
                    integrationService.fireEvent(
                        it.toHttpUrlOrNull()!!,
                        IntegrationRequest(
                            "fire_event",
                            fireEventRequest
                        )
                    ).isSuccessful
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
        val getZonesRequest =
            IntegrationRequest(
                "get_zones",
                null
            )
        var zones: Array<EntityResponse<ZoneAttributes>>? = null
        for (it in urlRepository.getApiUrls()) {
            try {
                zones = integrationService.getZones(it.toHttpUrlOrNull()!!, getZonesRequest)
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

    override suspend fun setFullScreenEnabled(enabled: Boolean) {
        localStorage.putBoolean(PREF_FULLSCREEN_ENABLED, enabled)
    }

    override suspend fun isFullScreenEnabled(): Boolean {
        return localStorage.getBoolean(PREF_FULLSCREEN_ENABLED)
    }

    override suspend fun sessionTimeOut(value: Int) {
        localStorage.putInt(PREF_SESSION_TIMEOUT, value)
    }

    override suspend fun getSessionTimeOut(): Int {
        return localStorage.getInt(PREF_SESSION_TIMEOUT) ?: 0
    }

    override suspend fun setSessionExpireMillis(value: Long) {
        localStorage.putLong(PREF_SESSION_EXPIRE, value)
    }

    override suspend fun getSessionExpireMillis(): Long {
        return localStorage.getLong(PREF_SESSION_EXPIRE) ?: 0
    }

    override suspend fun getThemeColor(): String {
        val getConfigRequest =
            IntegrationRequest(
                "get_config",
                null
            )
        var response: GetConfigResponse? = null
        for (it in urlRepository.getApiUrls()) {
            try {
                response = integrationService.getConfig(it.toHttpUrlOrNull()!!, getConfigRequest)
            } catch (e: Exception) {
                // Ignore failure until we are out of URLS to try!
            }

            if (response != null)
                return response.themeColor
        }

        throw IntegrationException()
    }

    // TODO: Use websocket to get panels.
    override suspend fun getPanels(): Array<Panel> {
        return arrayOf()
    }

    override suspend fun getServices(): Array<Service> {
        val response = integrationService.getServices(authenticationRepository.buildBearerToken())

        return response.flatMap {
            it.services.map { service ->
                Service(it.domain, service.key, service.value)
            }
        }.toTypedArray()
    }

    override suspend fun getEntities(): Array<Entity<Any>> {
        val response = integrationService.getStates(authenticationRepository.buildBearerToken())

        return response.map {
            Entity(
                it.entityId,
                it.state,
                it.attributes,
                it.lastChanged,
                it.lastUpdated,
                it.context
            )
        }.toTypedArray()
    }

    override suspend fun registerSensor(sensorRegistration: SensorRegistration<Any>) {
        val registeredSensors = localStorage.getStringSet(PREF_SENSORS_REGISTERED)
        if (registeredSensors?.contains(sensorRegistration.uniqueId) == true) {
            // Already registered
            return
        }
        val integrationRequest = IntegrationRequest(
            "register_sensor",
            SensorRequest(
                sensorRegistration.uniqueId,
                sensorRegistration.state,
                sensorRegistration.type,
                sensorRegistration.icon,
                sensorRegistration.attributes,
                sensorRegistration.name,
                sensorRegistration.deviceClass,
                sensorRegistration.unitOfMeasurement
            )
        )
        for (it in urlRepository.getApiUrls()) {
            try {
                integrationService.registerSensor(it.toHttpUrlOrNull()!!, integrationRequest).let {
                    // If we created sensor or it already exists
                    if (it.isSuccessful || it.code() == 409) {
                        localStorage.putStringSet(
                            PREF_SENSORS_REGISTERED,
                            registeredSensors.orEmpty().plus(sensorRegistration.uniqueId)
                        )
                        return
                    }
                }
            } catch (e: Exception) {
                // Ignore failure until we are out of URLS to try!
            }
        }
        throw IntegrationException()
    }

    override suspend fun updateSensors(sensors: Array<Sensor<Any>>): Boolean {
        val integrationRequest = IntegrationRequest(
            "update_sensor_states",
            sensors.map {
                SensorRequest(
                    it.uniqueId,
                    it.state,
                    it.type,
                    it.icon,
                    it.attributes
                )
            }
        )
        for (it in urlRepository.getApiUrls()) {
            try {
                integrationService.updateSensors(it.toHttpUrlOrNull()!!, integrationRequest).let {
                    it.forEach { (_, response) ->
                        if (response["success"] == false) {
                            localStorage.putStringSet(PREF_SENSORS_REGISTERED, setOf())
                            return false
                        }
                    }
                    return true
                }
            } catch (e: Exception) {
                // Ignore failure until we are out of URLS to try!
            }
        }
        throw IntegrationException()
    }

    private suspend fun createUpdateRegistrationRequest(deviceRegistration: DeviceRegistration): RegisterDeviceRequest {
        val oldDeviceRegistration = getRegistration()
        return RegisterDeviceRequest(
            null,
            null,
            deviceRegistration.appVersion ?: oldDeviceRegistration.appVersion,
            deviceRegistration.deviceName ?: oldDeviceRegistration.deviceName,
            manufacturer,
            model,
            null,
            osVersion,
            null,
            hashMapOf(
                "push_url" to PUSH_URL,
                "push_token" to (deviceRegistration.pushToken ?: oldDeviceRegistration.pushToken
                ?: "")
            ),
            null
        )
    }

    private fun createUpdateLocation(updateLocation: UpdateLocation): IntegrationRequest {
        return IntegrationRequest(
            "update_location",
            UpdateLocationRequest(
                updateLocation.locationName,
                updateLocation.gps,
                updateLocation.gpsAccuracy,
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

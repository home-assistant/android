package io.homeassistant.companion.android.common.data.integration.impl

import android.util.Log
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationException
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.common.data.integration.Service
import io.homeassistant.companion.android.common.data.integration.UpdateLocation
import io.homeassistant.companion.android.common.data.integration.ZoneAttributes
import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.integration.impl.entities.FireEventRequest
import io.homeassistant.companion.android.common.data.integration.impl.entities.IntegrationRequest
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitRequest
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse
import io.homeassistant.companion.android.common.data.integration.impl.entities.RegisterDeviceRequest
import io.homeassistant.companion.android.common.data.integration.impl.entities.SensorRequest
import io.homeassistant.companion.android.common.data.integration.impl.entities.ServiceCallRequest
import io.homeassistant.companion.android.common.data.integration.impl.entities.Template
import io.homeassistant.companion.android.common.data.integration.impl.entities.UpdateLocationRequest
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Named

class IntegrationRepositoryImpl @AssistedInject constructor(
    private val integrationService: IntegrationService,
    private val serverManager: ServerManager,
    @Assisted private val serverId: Int,
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
        private const val PUSH_URL = BuildConfig.PUSH_URL

        private const val PREF_APP_VERSION = "app_version" // Note: _not_ server-specific
        private const val PREF_PUSH_TOKEN = "push_token" // Note: _not_ server-specific

        private const val PREF_CHECK_SENSOR_REGISTRATION_NEXT = "sensor_reg_last"
        private const val PREF_SESSION_TIMEOUT = "session_timeout"
        private const val PREF_SESSION_EXPIRE = "session_expire"
        private const val PREF_SEC_WARNING_NEXT = "sec_warning_last"
        private const val TAG = "IntegrationRepository"
        private const val RATE_LIMIT_URL = BuildConfig.RATE_LIMIT_URL

        private const val APPLOCK_TIMEOUT_GRACE_MS = 1000
    }

    private val server get() = serverManager.getServer(serverId)!!

    private val webSocketRepository get() = serverManager.webSocketRepository(serverId)

    private var appActive = false

    override suspend fun registerDevice(deviceRegistration: DeviceRegistration) {
        val request = createUpdateRegistrationRequest(deviceRegistration)
        request.appId = APP_ID
        request.appName = APP_NAME
        request.osName = OS_NAME
        request.supportsEncryption = false
        request.deviceId = deviceId

        val url = server.connection.getUrl()?.toHttpUrlOrNull()
        if (url == null) {
            Log.e(TAG, "Unable to register device due to missing URL")
            return
        }
        val response =
            integrationService.registerDevice(
                url.newBuilder().addPathSegments("api/mobile_app/registrations").build(),
                serverManager.authenticationRepository(serverId).buildBearerToken(),
                request
            )
        try {
            persistDeviceRegistration(deviceRegistration)
            serverManager.updateServer(
                server.copy(
                    connection = server.connection.copy(
                        webhookId = response.webhookId,
                        cloudhookUrl = response.cloudhookUrl,
                        cloudUrl = response.remoteUiUrl,
                        useCloud = response.remoteUiUrl != null,
                        secret = response.secret
                    )
                )
            )
            getConfig() // To get version, name, etc stored
        } catch (e: Exception) {
            Log.e(TAG, "Unable to save device registration", e)
        }
    }

    override suspend fun updateRegistration(deviceRegistration: DeviceRegistration) {
        val request =
            IntegrationRequest(
                "update_registration",
                createUpdateRegistrationRequest(deviceRegistration)
            )
        var causeException: Exception? = null
        for (it in server.connection.getApiUrls()) {
            try {
                if (integrationService.callWebhook(it.toHttpUrlOrNull()!!, request).isSuccessful) {
                    persistDeviceRegistration(deviceRegistration)
                    return
                }
            } catch (e: Exception) {
                if (causeException == null) causeException = e
                // Ignore failure until we are out of URLS to try, but use the first exception as cause exception
            }
        }

        if (causeException != null) throw IntegrationException(causeException)
        else throw IntegrationException("Error calling integration request update_registration")
    }

    override suspend fun getRegistration(): DeviceRegistration {
        return DeviceRegistration(
            localStorage.getString(PREF_APP_VERSION),
            server.deviceName,
            localStorage.getString(PREF_PUSH_TOKEN)
        )
    }

    private suspend fun persistDeviceRegistration(deviceRegistration: DeviceRegistration) {
        if (deviceRegistration.appVersion != null)
            localStorage.putString(PREF_APP_VERSION, deviceRegistration.appVersion)
        if (deviceRegistration.deviceName != null)
            serverManager.updateServer(server.copy(deviceName = deviceRegistration.deviceName))
        if (deviceRegistration.pushToken != null)
            localStorage.putString(PREF_PUSH_TOKEN, deviceRegistration.pushToken)
    }

    override suspend fun deletePreferences() {
        localStorage.remove("${serverId}_$PREF_CHECK_SENSOR_REGISTRATION_NEXT")
        localStorage.remove("${serverId}_$PREF_SESSION_TIMEOUT")
        localStorage.remove("${serverId}_$PREF_SESSION_EXPIRE")
        localStorage.remove("${serverId}_$PREF_SEC_WARNING_NEXT")
        // app version and push token is device-specific
    }

    private fun isRegistered(): Boolean {
        return server.connection.getApiUrls().isNotEmpty()
    }

    override suspend fun renderTemplate(template: String, variables: Map<String, String>): String? {
        var causeException: Exception? = null
        for (it in server.connection.getApiUrls()) {
            try {
                return integrationService.getTemplate(
                    it.toHttpUrlOrNull()!!,
                    IntegrationRequest(
                        "render_template",
                        mapOf("template" to Template(template, variables))
                    )
                )["template"]
            } catch (e: Exception) {
                if (causeException == null) causeException = e
                // Ignore failure until we are out of URLS to try, but use the first exception as cause exception
            }
        }

        if (causeException != null) throw IntegrationException(causeException)
        else throw IntegrationException("Error calling integration request render_template")
    }

    override suspend fun getTemplateUpdates(template: String): Flow<String?>? {
        return webSocketRepository.getTemplateUpdates(template)
            ?.map {
                it.result
            }
    }

    override suspend fun updateLocation(updateLocation: UpdateLocation) {
        val updateLocationRequest = createUpdateLocation(updateLocation)

        var causeException: Exception? = null
        for (it in server.connection.getApiUrls()) {
            var wasSuccess = false
            try {
                wasSuccess =
                    integrationService.callWebhook(
                        it.toHttpUrlOrNull()!!,
                        updateLocationRequest
                    ).isSuccessful
            } catch (e: Exception) {
                if (causeException == null) causeException = e
                // Ignore failure until we are out of URLS to try, but use the first exception as cause exception
            }
            // if we had a successful call we can return
            if (wasSuccess)
                return
        }

        if (causeException != null) throw IntegrationException(causeException)
        else throw IntegrationException("Error calling integration request update_location")
    }

    override suspend fun callService(
        domain: String,
        service: String,
        serviceData: HashMap<String, Any>
    ) {
        var wasSuccess = false

        val serviceCallRequest =
            ServiceCallRequest(
                domain,
                service,
                serviceData
            )

        var causeException: Exception? = null
        for (it in server.connection.getApiUrls()) {
            try {
                wasSuccess =
                    integrationService.callWebhook(
                        it.toHttpUrlOrNull()!!,
                        IntegrationRequest(
                            "call_service",
                            serviceCallRequest
                        )
                    ).isSuccessful
            } catch (e: Exception) {
                if (causeException == null) causeException = e
                // Ignore failure until we are out of URLS to try, but use the first exception as cause exception
            }
            // if we had a successful call we can return
            if (wasSuccess)
                return
        }

        if (causeException != null) throw IntegrationException(causeException)
        else throw IntegrationException("Error calling integration request call_service")
    }

    override suspend fun scanTag(data: HashMap<String, Any>) {
        var wasSuccess = false

        var causeException: Exception? = null
        for (it in server.connection.getApiUrls()) {
            try {
                wasSuccess =
                    integrationService.callWebhook(
                        it.toHttpUrlOrNull()!!,
                        IntegrationRequest(
                            "scan_tag",
                            data
                        )
                    ).isSuccessful
            } catch (e: Exception) {
                if (causeException == null) causeException = e
                // Ignore failure until we are out of URLS to try, but use the first exception as cause exception
            }
            // if we had a successful call we can return
            if (wasSuccess)
                return
        }

        if (causeException != null) throw IntegrationException(causeException)
        else throw IntegrationException("Error calling integration request scan_tag")
    }

    override suspend fun fireEvent(eventType: String, eventData: Map<String, Any>) {
        var wasSuccess = false

        val fireEventRequest = FireEventRequest(
            eventType,
            eventData.plus(Pair("device_id", deviceId))
        )

        var causeException: Exception? = null
        for (it in server.connection.getApiUrls()) {
            try {
                wasSuccess =
                    integrationService.callWebhook(
                        it.toHttpUrlOrNull()!!,
                        IntegrationRequest(
                            "fire_event",
                            fireEventRequest
                        )
                    ).isSuccessful
            } catch (e: Exception) {
                if (causeException == null) causeException = e
                // Ignore failure until we are out of URLS to try, but use the first exception as cause exception
            }
            // if we had a successful call we can return
            if (wasSuccess)
                return
        }

        if (causeException != null) throw IntegrationException(causeException)
        else throw IntegrationException("Error calling integration request fire_event")
    }

    override suspend fun getZones(): Array<Entity<ZoneAttributes>> {
        val getZonesRequest =
            IntegrationRequest(
                "get_zones",
                null
            )
        var causeException: Exception? = null
        var zones: Array<EntityResponse<ZoneAttributes>>? = null
        for (it in server.connection.getApiUrls()) {
            try {
                zones = integrationService.getZones(it.toHttpUrlOrNull()!!, getZonesRequest)
            } catch (e: Exception) {
                if (causeException == null) causeException = e
                // Ignore failure until we are out of URLS to try, but use the first exception as cause exception
            }

            if (zones != null) {
                return createZonesResponse(zones)
            }
        }

        if (causeException != null) throw IntegrationException(causeException)
        else throw IntegrationException("Error calling integration request get_zones")
    }

    override suspend fun isAppLocked(): Boolean {
        val lockEnabled = serverManager.authenticationRepository(serverId).isLockEnabled()
        val sessionExpireMillis = getSessionExpireMillis()
        val currentMillis = System.currentTimeMillis()
        val sessionExpired = currentMillis > sessionExpireMillis
        val appLocked = lockEnabled && !appActive && sessionExpired

        Log.d(TAG, "isAppLocked(): $appLocked. (LockEnabled: $lockEnabled, appActive: $appActive, expireMillis: $sessionExpireMillis, currentMillis: $currentMillis)")
        return appLocked
    }

    override suspend fun setAppActive(active: Boolean) {
        if (!active) {
            setSessionExpireMillis(System.currentTimeMillis() + (getSessionTimeOut() * 1000) + APPLOCK_TIMEOUT_GRACE_MS)
        }
        Log.d(TAG, "setAppActive(): $active")
        appActive = active
    }

    override suspend fun sessionTimeOut(value: Int) =
        localStorage.putInt("${serverId}_$PREF_SESSION_TIMEOUT", value)

    override suspend fun getSessionTimeOut(): Int =
        localStorage.getInt("${serverId}_$PREF_SESSION_TIMEOUT") ?: 0

    override suspend fun setSessionExpireMillis(value: Long) =
        localStorage.putLong("${serverId}_$PREF_SESSION_EXPIRE", value)

    private suspend fun getSessionExpireMillis(): Long =
        localStorage.getLong("${serverId}_$PREF_SESSION_EXPIRE") ?: 0

    override suspend fun getNotificationRateLimits(): RateLimitResponse {
        val pushToken = localStorage.getString(PREF_PUSH_TOKEN) ?: ""
        val requestBody = RateLimitRequest(pushToken)
        var checkRateLimits: RateLimitResponse? = null

        var causeException: Exception? = null

        try {
            checkRateLimits =
                integrationService.getRateLimit(RATE_LIMIT_URL, requestBody).rateLimits
        } catch (e: Exception) {
            causeException = e
            Log.e(TAG, "Unable to get notification rate limits", e)
        }
        if (checkRateLimits != null)
            return checkRateLimits

        if (causeException != null) throw IntegrationException(causeException)
        else throw IntegrationException("Error calling checkRateLimits")
    }

    override suspend fun getHomeAssistantVersion(): String {
        val current = System.currentTimeMillis()
        val next = localStorage.getLong("${serverId}_$PREF_CHECK_SENSOR_REGISTRATION_NEXT") ?: 0
        if (current <= next)
            return server._version
                ?: "" // Skip checking HA version as it has not been 4 hours yet

        return try {
            getConfig().let { response ->
                updateServerWithConfig(response)
                localStorage.putLong(
                    "${serverId}_$PREF_CHECK_SENSOR_REGISTRATION_NEXT",
                    current + TimeUnit.HOURS.toMillis(4)
                )
                response.version
            }
        } catch (e: Exception) {
            Log.e(TAG, "Issue getting new version from core.", e)
            server._version ?: ""
        }
    }

    override suspend fun isHomeAssistantVersionAtLeast(
        year: Int,
        month: Int,
        release: Int
    ): Boolean {
        if (!isRegistered()) return false

        val version = HomeAssistantVersion.fromString(getHomeAssistantVersion())
        return version?.isAtLeast(year, month, release) ?: false
    }

    override suspend fun getConfig(): GetConfigResponse {
        val getConfigRequest =
            IntegrationRequest(
                "get_config",
                null
            )
        var response: GetConfigResponse? = null
        var causeException: Exception? = null

        for (it in server.connection.getApiUrls()) {
            try {
                response = integrationService.getConfig(it.toHttpUrlOrNull()!!, getConfigRequest)
            } catch (e: Exception) {
                if (causeException == null) causeException = e
                // Ignore failure until we are out of URLS to try, but use the first exception as cause exception
            }

            if (response != null) {
                // If we have a valid response, also update the cached version
                updateServerWithConfig(response)
                localStorage.putLong(
                    "${serverId}_$PREF_CHECK_SENSOR_REGISTRATION_NEXT",
                    System.currentTimeMillis() + TimeUnit.HOURS.toMillis(4)
                )
                return response
            }
        }

        if (causeException != null) throw IntegrationException(causeException)
        else throw IntegrationException("Error calling integration request get_config")
    }

    /**
     * Update this repository's [server] with information from a [GetConfigResponse] like original
     * name and core version.
     */
    private fun updateServerWithConfig(config: GetConfigResponse) {
        serverManager.updateServer(
            server.copy(
                _name = config.locationName,
                _version = config.version,
                connection = server.connection.copy(
                    cloudUrl = config.remoteUiUrl,
                    cloudhookUrl = config.cloudhookUrl
                )
            )
        )
    }

    override suspend fun getServices(): List<Service>? {
        val response = webSocketRepository.getServices()

        return response?.flatMap {
            it.services.map { service ->
                Service(it.domain, service.key, service.value)
            }
        }?.toList()
    }

    override suspend fun getConversation(speech: String): String? {
        // TODO: Also send back conversation ID for dialogue
        val response = webSocketRepository.getConversation(speech)

        return response?.response?.speech?.plain?.get("speech")
    }

    override suspend fun getEntities(): List<Entity<Any>>? {
        val response = webSocketRepository.getStates()

        return response?.map {
            Entity(
                it.entityId,
                it.state,
                it.attributes,
                it.lastChanged,
                it.lastUpdated,
                it.context
            )
        }
            ?.sortedBy { it.entityId }
            ?.toList()
    }

    override suspend fun getEntity(entityId: String): Entity<Map<String, Any>>? {
        val url = server.connection.getUrl()?.toHttpUrlOrNull()
        if (url == null) {
            Log.e(TAG, "Unable to register device due to missing URL")
            return null
        }

        val response = integrationService.getState(
            url.newBuilder().addPathSegments("api/states/$entityId").build(),
            serverManager.authenticationRepository(serverId).buildBearerToken()
        )
        return Entity(
            response.entityId,
            response.state,
            response.attributes,
            response.lastChanged,
            response.lastUpdated,
            response.context
        )
    }

    override suspend fun getEntityUpdates(): Flow<Entity<*>>? {
        return webSocketRepository.getStateChanges()
            ?.filter { it.newState != null }
            ?.map {
                Entity(
                    it.newState!!.entityId,
                    it.newState.state,
                    it.newState.attributes,
                    it.newState.lastChanged,
                    it.newState.lastUpdated,
                    it.newState.context
                )
            }
    }

    override suspend fun getEntityUpdates(entityIds: List<String>): Flow<Entity<*>>? {
        return webSocketRepository.getStateChanges(entityIds)
            ?.filter { it.toState != null }
            ?.map {
                Entity(
                    it.toState!!.entityId,
                    it.toState.state,
                    it.toState.attributes,
                    it.toState.lastChanged,
                    it.toState.lastUpdated,
                    it.toState.context
                )
            }
    }

    override suspend fun registerSensor(sensorRegistration: SensorRegistration<Any>) {
        val canRegisterCategoryStateClass = isHomeAssistantVersionAtLeast(2021, 11, 0)
        val canRegisterEntityDisabledState = isHomeAssistantVersionAtLeast(2022, 6, 0)
        val canRegisterDeviceClassDistance = isHomeAssistantVersionAtLeast(2022, 10, 0)
        val integrationRequest = IntegrationRequest(
            "register_sensor",
            SensorRequest(
                sensorRegistration.uniqueId,
                if (canRegisterEntityDisabledState && sensorRegistration.disabled) null else sensorRegistration.state,
                sensorRegistration.type,
                sensorRegistration.icon,
                sensorRegistration.attributes,
                sensorRegistration.name,
                when (sensorRegistration.deviceClass) {
                    "distance" -> if (canRegisterDeviceClassDistance) sensorRegistration.deviceClass else null
                    else -> sensorRegistration.deviceClass
                },
                sensorRegistration.unitOfMeasurement,
                if (canRegisterCategoryStateClass) sensorRegistration.stateClass else null,
                if (canRegisterCategoryStateClass) sensorRegistration.entityCategory else null,
                if (canRegisterEntityDisabledState) sensorRegistration.disabled else null
            )
        )

        var causeException: Exception? = null
        for (it in server.connection.getApiUrls()) {
            try {
                integrationService.callWebhook(it.toHttpUrlOrNull()!!, integrationRequest).let {
                    // If we created sensor or it already exists
                    if (it.isSuccessful || it.code() == 409) {
                        return
                    }
                }
            } catch (e: Exception) {
                if (causeException == null) causeException = e
                // Ignore failure until we are out of URLS to try, but use the first exception as cause exception
            }
        }
        if (causeException != null) throw IntegrationException(causeException)
        else throw IntegrationException("Error calling integration request register_sensor")
    }

    override suspend fun updateSensors(sensors: Array<SensorRegistration<Any>>): Boolean {
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

        var causeException: Exception? = null
        for (it in server.connection.getApiUrls()) {
            try {
                integrationService.updateSensors(it.toHttpUrlOrNull()!!, integrationRequest).let {
                    it.forEach { (_, response) ->
                        if (response["success"] == false) {
                            return false
                        }
                    }
                    return true
                }
            } catch (e: Exception) {
                if (causeException == null) causeException = e
                // Ignore failure until we are out of URLS to try, but use the first exception as cause exception
            }
        }

        if (causeException != null) throw IntegrationException(causeException)
        else throw IntegrationException("Error calling integration update_sensor_states")
    }

    override suspend fun shouldNotifySecurityWarning(): Boolean {
        val current = System.currentTimeMillis()
        val next = localStorage.getLong("${serverId}_$PREF_SEC_WARNING_NEXT") ?: 0
        return if (current > next) {
            localStorage.putLong("${serverId}_$PREF_SEC_WARNING_NEXT", current + (86400000)) // 24 hours
            true
        } else {
            false
        }
    }

    private suspend fun createUpdateRegistrationRequest(deviceRegistration: DeviceRegistration): RegisterDeviceRequest {
        val oldDeviceRegistration = getRegistration()
        val pushToken = deviceRegistration.pushToken ?: oldDeviceRegistration.pushToken

        val appData = mutableMapOf<String, Any>("push_websocket_channel" to deviceRegistration.pushWebsocket)
        if (!pushToken.isNullOrBlank()) {
            appData["push_url"] = PUSH_URL
            appData["push_token"] = pushToken
        }

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
            appData,
            null
        )
    }

    private fun createUpdateLocation(updateLocation: UpdateLocation): IntegrationRequest {
        return IntegrationRequest(
            "update_location",
            UpdateLocationRequest(
                updateLocation.gps,
                updateLocation.gpsAccuracy,
                updateLocation.locationName,
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

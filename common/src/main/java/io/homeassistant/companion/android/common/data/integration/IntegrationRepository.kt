package io.homeassistant.companion.android.common.data.integration

import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse

interface IntegrationRepository {

    suspend fun registerDevice(deviceRegistration: DeviceRegistration)
    suspend fun updateRegistration(deviceRegistration: DeviceRegistration)
    suspend fun getRegistration(): DeviceRegistration

    suspend fun isRegistered(): Boolean

    suspend fun getNotificationRateLimits(): RateLimitResponse
    suspend fun renderTemplate(template: String, variables: Map<String, String>): String

    suspend fun updateLocation(updateLocation: UpdateLocation)

    suspend fun getZones(): Array<Entity<ZoneAttributes>>

    suspend fun setFullScreenEnabled(enabled: Boolean)
    suspend fun isFullScreenEnabled(): Boolean

    suspend fun setKeepScreenOnEnabled(enabled: Boolean)
    suspend fun isKeepScreenOnEnabled(): Boolean

    suspend fun setAutoPlayVideo(enabled: Boolean)
    suspend fun isAutoPlayVideoEnabled(): Boolean

    suspend fun sessionTimeOut(value: Int)
    suspend fun getSessionTimeOut(): Int

    suspend fun setSessionExpireMillis(value: Long)
    suspend fun getSessionExpireMillis(): Long

    suspend fun setWearHomeFavorites(favorites: Set<String>)
    suspend fun getWearHomeFavorites(): Set<String>

    suspend fun getHomeAssistantVersion(): String

    suspend fun getServices(): List<Service>

    suspend fun getEntities(): List<Entity<Any>>
    suspend fun getEntity(entityId: String): Entity<Map<String, Any>>

    suspend fun callService(domain: String, service: String, serviceData: HashMap<String, Any>)

    suspend fun scanTag(data: HashMap<String, Any>)

    suspend fun fireEvent(eventType: String, eventData: Map<String, Any>)

    suspend fun registerSensor(sensorRegistration: SensorRegistration<Any>)
    suspend fun updateSensors(sensors: Array<SensorRegistration<Any>>): Boolean

    suspend fun shouldNotifySecurityWarning(): Boolean
}

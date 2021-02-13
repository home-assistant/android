package io.homeassistant.companion.android.common.data.integration

import android.content.Context
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse

interface IntegrationRepository {

    suspend fun registerDevice(deviceRegistration: DeviceRegistration)
    suspend fun updateRegistration(deviceRegistration: DeviceRegistration)
    suspend fun getRegistration(): DeviceRegistration

    suspend fun isRegistered(): Boolean

    suspend fun notifyFailedToConnect(context: Context)
    suspend fun removeFailedNotification(context: Context)
    suspend fun getNotificationRateLimits(): RateLimitResponse
    suspend fun renderTemplate(template: String, variables: Map<String, String>, context: Context): String

    suspend fun updateLocation(updateLocation: UpdateLocation, context: Context)

    suspend fun getZones(context: Context): Array<Entity<ZoneAttributes>>

    suspend fun setFullScreenEnabled(enabled: Boolean)
    suspend fun isFullScreenEnabled(): Boolean

    suspend fun sessionTimeOut(value: Int)
    suspend fun getSessionTimeOut(): Int

    suspend fun setSessionExpireMillis(value: Long)
    suspend fun getSessionExpireMillis(): Long

    suspend fun getThemeColor(): String

    suspend fun getHomeAssistantVersion(context: Context): String

    suspend fun getPanels(): Array<Panel>

    suspend fun getServices(): Array<Service>

    suspend fun getEntities(): Array<Entity<Any>>
    suspend fun getEntity(entityId: String): Entity<Map<String, Any>>

    suspend fun callService(domain: String, service: String, serviceData: HashMap<String, Any>, context: Context)

    suspend fun scanTag(data: HashMap<String, Any>, context: Context)

    suspend fun fireEvent(eventType: String, eventData: Map<String, Any>, context: Context)

    suspend fun registerSensor(sensorRegistration: SensorRegistration<Any>)
    suspend fun updateSensors(context: Context, sensors: Array<SensorRegistration<Any>>): Boolean

    suspend fun shouldNotifySecurityWarning(): Boolean
}

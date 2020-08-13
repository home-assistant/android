package io.homeassistant.companion.android.domain.integration

interface IntegrationRepository {

    suspend fun registerDevice(deviceRegistration: DeviceRegistration)
    suspend fun updateRegistration(deviceRegistration: DeviceRegistration)
    suspend fun getRegistration(): DeviceRegistration

    suspend fun isRegistered(): Boolean

    suspend fun updateLocation(updateLocation: UpdateLocation)

    suspend fun getZones(): Array<Entity<ZoneAttributes>>

    suspend fun setFullScreenEnabled(enabled: Boolean)
    suspend fun isFullScreenEnabled(): Boolean

    suspend fun sessionTimeOut(value: Int)
    suspend fun getSessionTimeOut(): Int

    suspend fun setSessionExpireMillis(value: Long)
    suspend fun getSessionExpireMillis(): Long

    suspend fun getThemeColor(): String

    suspend fun getHomeAssistantVersion(): String

    suspend fun getPanels(): Array<Panel>

    suspend fun getServices(): Array<Service>

    suspend fun getEntities(): Array<Entity<Any>>

    suspend fun callService(domain: String, service: String, serviceData: HashMap<String, Any>)

    suspend fun scanTag(data: HashMap<String, Any>)

    suspend fun fireEvent(eventType: String, eventData: Map<String, Any>)

    suspend fun registerSensor(sensorRegistration: SensorRegistration<Any>)
    suspend fun updateSensors(sensors: Array<SensorRegistration<Any>>): Boolean
}

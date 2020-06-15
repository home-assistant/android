package io.homeassistant.companion.android.domain.integration

import javax.inject.Inject

class IntegrationUseCaseImpl @Inject constructor(
    private val integrationRepository: IntegrationRepository
) : IntegrationUseCase {
    override suspend fun registerDevice(deviceRegistration: DeviceRegistration) {
        integrationRepository.registerDevice(deviceRegistration)
    }

    override suspend fun updateRegistration(
        appVersion: String?,
        deviceName: String?,
        manufacturer: String?,
        model: String?,
        osVersion: String?,
        pushUrl: String?,
        pushToken: String?
    ) {

        integrationRepository.updateRegistration(
            DeviceRegistration(
                appVersion,
                deviceName,
                pushToken
            )
        )
    }

    override suspend fun getRegistration(): DeviceRegistration {
        return integrationRepository.getRegistration()
    }

    override suspend fun isRegistered(): Boolean {
        return integrationRepository.isRegistered()
    }

    override suspend fun updateLocation(updateLocation: UpdateLocation) {
        return integrationRepository.updateLocation(updateLocation)
    }

    override suspend fun callService(domain: String, service: String, serviceData: HashMap<String, Any>) {
        return integrationRepository.callService(domain, service, serviceData)
    }

    override suspend fun fireEvent(eventType: String, eventData: Map<String, Any>) {
        return integrationRepository.fireEvent(eventType, eventData)
    }

    override suspend fun getZones(): Array<Entity<ZoneAttributes>> {
        return integrationRepository.getZones()
    }

    override suspend fun setZoneTrackingEnabled(enabled: Boolean) {
        return integrationRepository.setZoneTrackingEnabled(enabled)
    }

    override suspend fun isZoneTrackingEnabled(): Boolean {
        return integrationRepository.isZoneTrackingEnabled()
    }

    override suspend fun setBackgroundTrackingEnabled(enabled: Boolean) {
        return integrationRepository.setBackgroundTrackingEnabled(enabled)
    }

    override suspend fun isBackgroundTrackingEnabled(): Boolean {
        return integrationRepository.isBackgroundTrackingEnabled()
    }

    override suspend fun setFullScreenEnabled(enabled: Boolean) {
        return integrationRepository.setFullScreenEnabled(enabled)
    }

    override suspend fun isFullScreenEnabled(): Boolean {
        return integrationRepository.isFullScreenEnabled()
    }

    override suspend fun sessionTimeOut(value: Int) {
        return integrationRepository.sessionTimeOut(value)
    }

    override suspend fun getSessionTimeOut(): Int {
        return integrationRepository.getSessionTimeOut()
    }

    override suspend fun setSessionExpireMillis(value: Long) {
        return integrationRepository.setSessionExpireMillis(value)
    }

    override suspend fun getSessionExpireMillis(): Long {
        return integrationRepository.getSessionExpireMillis()
    }

    override suspend fun getServices(): Array<Service> {
        return integrationRepository.getServices()
    }

    override suspend fun getEntities(): Array<Entity<Any>> {
        return integrationRepository.getEntities()
    }

    override suspend fun getPanels(): Array<Panel> {
        return integrationRepository.getPanels()
    }

    override suspend fun getThemeColor(): String {
        return integrationRepository.getThemeColor()
    }

    override suspend fun registerSensor(sensorRegistration: SensorRegistration<Any>) {
        return integrationRepository.registerSensor(sensorRegistration)
    }

    override suspend fun updateSensors(sensors: Array<Sensor<Any>>): Boolean {
        return integrationRepository.updateSensors(sensors)
    }
}

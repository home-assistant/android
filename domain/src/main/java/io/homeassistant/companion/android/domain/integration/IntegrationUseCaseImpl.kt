package io.homeassistant.companion.android.domain.integration

import java.net.URL
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

    override suspend fun getUiUrl(isInternal: Boolean): URL? {
        return integrationRepository.getUiUrl(isInternal)
    }

    override suspend fun updateLocation(updateLocation: UpdateLocation) {
        return integrationRepository.updateLocation(updateLocation)
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
}

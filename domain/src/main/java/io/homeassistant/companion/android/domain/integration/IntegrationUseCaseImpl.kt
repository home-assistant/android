package io.homeassistant.companion.android.domain.integration

import javax.inject.Inject

class IntegrationUseCaseImpl @Inject constructor(
    private val integrationRepository: IntegrationRepository
) : IntegrationUseCase {
    override suspend fun registerDevice(deviceRegistration: DeviceRegistration) {
        integrationRepository.registerDevice(deviceRegistration)
    }

    override suspend fun isRegistered(): Boolean {
        return integrationRepository.isRegistered()
    }
}

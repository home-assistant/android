package io.homeassistant.companion.android.domain.integration

import java.util.*
import javax.inject.Inject

class IntegrationUseCaseImpl @Inject constructor(
    private val integrationRepository: IntegrationRepository
) : IntegrationUseCase {
    override suspend fun registerDevice(
        appId: String,
        appName: String,
        appVersion: String,
        deviceName: String,
        manufacturer: String,
        model: String,
        osName: String,
        osVersion: String,
        supportsEncryption: Boolean,
        appData: Dictionary<String, Objects>?
    ) {
        integrationRepository.registerDevice(
            appId,
            appName,
            appVersion,
            deviceName,
            manufacturer,
            model,
            osName,
            osVersion,
            supportsEncryption,
            appData
        )
    }

    override suspend fun isRegistered(): Boolean {
        return integrationRepository.isRegistered()
    }
}
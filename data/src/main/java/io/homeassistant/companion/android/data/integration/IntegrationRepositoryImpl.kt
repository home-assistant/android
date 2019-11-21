package io.homeassistant.companion.android.data.integration

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository
import io.homeassistant.companion.android.domain.integration.DeviceRegistration
import io.homeassistant.companion.android.domain.integration.IntegrationRepository
import javax.inject.Inject
import javax.inject.Named

class IntegrationRepositoryImpl @Inject constructor(
    private val integrationService: IntegrationService,
    private val authenticationRepository: AuthenticationRepository,
    @Named("integration") private val localStorage: LocalStorage
) : IntegrationRepository {

    companion object {
        private const val PREF_CLOUD_URL = "cloud_url"
        private const val PREF_REMOTE_UI_URL = "remote_ui_url"
        private const val PREF_SECRET = "secret"
        private const val PREF_WEBHOOK_ID = "webhook_id"
    }

    override suspend fun registerDevice(deviceRegistration: DeviceRegistration) {
        val response =
            integrationService.registerDevice(
                authenticationRepository.buildBearerToken(),
                createRegisterDeviceRequest(deviceRegistration)
            )

        localStorage.putString(PREF_CLOUD_URL, response.cloudhookUrl)
        localStorage.putString(PREF_REMOTE_UI_URL, response.remoteUiUrl)
        localStorage.putString(PREF_SECRET, response.secret)
        localStorage.putString(PREF_WEBHOOK_ID, response.webhookId)
    }

    override suspend fun isRegistered(): Boolean {
        return localStorage.getString(PREF_WEBHOOK_ID) != null
    }

    private fun createRegisterDeviceRequest(deviceRegistration: DeviceRegistration): RegisterDeviceRequest {
        return RegisterDeviceRequest(
            deviceRegistration.appId,
            deviceRegistration.appName,
            deviceRegistration.appVersion,
            deviceRegistration.deviceName,
            deviceRegistration.manufacturer,
            deviceRegistration.model,
            deviceRegistration.osName,
            deviceRegistration.osVersion,
            deviceRegistration.supportsEncryption,
            deviceRegistration.appData
        )
    }
}

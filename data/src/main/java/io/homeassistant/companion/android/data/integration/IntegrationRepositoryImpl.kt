package io.homeassistant.companion.android.data.integration

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository
import io.homeassistant.companion.android.domain.integration.IntegrationRepository
import java.util.*
import javax.inject.Inject

class IntegrationRepositoryImpl @Inject constructor(
    private val integrationService: IntegrationService,
    private val authenticationRepository: AuthenticationRepository,
    private val localStorage: LocalStorage
) : IntegrationRepository {

    companion object {
        private const val PREF_CLOUD_URL = "cloud_url"
        private const val PREF_REMOTE_UI_URL = "remote_ui_url"
        private const val PREF_SECRET = "secret"
        private const val PREF_WEBHOOK_ID = "webhook_id"
    }

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
    ): Boolean {
        var success = false
        try {
            val response = integrationService.registerDevice(
                authenticationRepository.buildBearerToken(),
                RegisterDeviceRequest(
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
            )

            localStorage.putString(PREF_CLOUD_URL, response.cloudhookUrl)
            localStorage.putString(PREF_REMOTE_UI_URL, response.remoteUiUrl)
            localStorage.putString(PREF_SECRET, response.secret)
            localStorage.putString(PREF_WEBHOOK_ID, response.webhookId)

            success = true
        } catch (exception: Exception) {
            // TODO: Change this to our logger?
            System.err.println("Issue registering device!")
            exception.printStackTrace()
        }
        return success
    }

    override suspend fun isRegistered(): Boolean {
        return localStorage.getString(PREF_WEBHOOK_ID) != null
    }
}
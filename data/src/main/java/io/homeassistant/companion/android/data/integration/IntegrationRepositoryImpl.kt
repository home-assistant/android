package io.homeassistant.companion.android.data.integration

import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository
import io.homeassistant.companion.android.domain.integration.DeviceRegistration
import io.homeassistant.companion.android.domain.integration.IntegrationRepository
import io.homeassistant.companion.android.domain.integration.UpdateLocation
import javax.inject.Inject
import javax.inject.Named
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

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

    override suspend fun updateLocation(updateLocation: UpdateLocation) {
        val updateLocationRequest = createUpdateLocation(updateLocation)
        for (it in getUrls()) {
            var wasSuccess = false
            try {
                wasSuccess = integrationService.updateLocation(it, updateLocationRequest).isSuccessful
            } catch (e: Exception) {
                // Ignore failure until we are out of URLS to try!
            }
            // if we had a successful call we can return
            if (wasSuccess)
                return
        }

        throw IntegrationException()
    }

    // https://developers.home-assistant.io/docs/en/app_integration_sending_data.html#short-note-on-instance-urls
    private suspend fun getUrls(): Array<HttpUrl> {
        val retVal = ArrayList<HttpUrl>()
        val webhook = localStorage.getString(PREF_WEBHOOK_ID)

        localStorage.getString(PREF_CLOUD_URL)?.let {
            retVal.add(it.toHttpUrl())
        }

        localStorage.getString(PREF_REMOTE_UI_URL)?.let {
            retVal.add(
                it.toHttpUrl().newBuilder()
                    .addPathSegments("api/webhook/$webhook")
                    .build()
            )
        }

        authenticationRepository.getUrl().toString().let {
            retVal.add(
                it.toHttpUrl().newBuilder()
                    .addPathSegments("api/webhook/$webhook")
                    .build()
            )
        }

        return retVal.toTypedArray()
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

    private fun createUpdateLocation(updateLocation: UpdateLocation): IntegrationRequest {
        return IntegrationRequest(
            "update_location",
            UpdateLocationRequest(
                updateLocation.locationName,
                updateLocation.gps,
                updateLocation.gpsAccuracy,
                updateLocation.battery,
                updateLocation.speed,
                updateLocation.altitude,
                updateLocation.course,
                updateLocation.verticalAccuracy
            )
        )
    }
}

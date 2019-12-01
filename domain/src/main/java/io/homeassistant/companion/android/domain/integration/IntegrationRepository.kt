package io.homeassistant.companion.android.domain.integration

interface IntegrationRepository {

    suspend fun registerDevice(deviceRegistration: DeviceRegistration)

    suspend fun isRegistered(): Boolean
}

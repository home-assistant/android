package io.homeassistant.companion.android.domain.integration

interface IntegrationUseCase {

    suspend fun registerDevice(deviceRegistration: DeviceRegistration)

    suspend fun isRegistered(): Boolean

    suspend fun updateLocation(updateLocation: UpdateLocation)
}

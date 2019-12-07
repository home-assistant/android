package io.homeassistant.companion.android.domain.integration

interface IntegrationUseCase {

    suspend fun registerDevice(deviceRegistration: DeviceRegistration)

    suspend fun isRegistered(): Boolean

    suspend fun updateLocation(updateLocation: UpdateLocation)

    suspend fun getZones(): Array<Entity<ZoneAttributes>>

    suspend fun setZoneTrackingEnabled(enabled: Boolean)

    suspend fun isZoneTrackingEnabled(): Boolean

    suspend fun setBackgroundTrackingEnabled(enabled: Boolean)

    suspend fun isBackgroundTrackingEnabled(): Boolean
}

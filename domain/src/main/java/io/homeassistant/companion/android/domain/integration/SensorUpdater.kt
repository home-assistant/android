package io.homeassistant.companion.android.domain.integration

interface SensorUpdater {
    suspend fun updateSensors()
}

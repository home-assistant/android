package io.homeassistant.companion.android.sensors

interface SensorUpdater {
    suspend fun updateSensors()
}

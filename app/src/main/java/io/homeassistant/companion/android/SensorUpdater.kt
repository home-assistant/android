package io.homeassistant.companion.android

interface SensorUpdater {
    suspend fun updateSensors()
}

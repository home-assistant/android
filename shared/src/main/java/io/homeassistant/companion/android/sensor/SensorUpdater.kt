package io.homeassistant.companion.android.sensor

interface SensorUpdater {
    suspend fun updateSensors(): Boolean
}

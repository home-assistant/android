package io.homeassistant.companion.android.sensor

import io.homeassistant.companion.android.domain.integration.Sensor
import io.homeassistant.companion.android.domain.integration.SensorRegistration

interface SensorManager {
    suspend fun getSensorRegistrations(): List<SensorRegistration<*>>
    suspend fun getSensors(): List<Sensor<*>>
}

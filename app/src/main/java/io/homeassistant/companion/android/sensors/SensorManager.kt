package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.domain.integration.Sensor
import io.homeassistant.companion.android.domain.integration.SensorRegistration

interface SensorManager {

    fun requiredPermissions(): Array<String>

    fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>>

    fun getSensors(context: Context): List<Sensor<Any>>

}

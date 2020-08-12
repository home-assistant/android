package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.domain.integration.SensorRegistration

interface SensorManager {

    fun requiredPermissions(): Array<String>

    fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>>

}

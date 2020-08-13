package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.domain.integration.SensorRegistration

interface SensorManager {

    val name: String

    fun requiredPermissions(): Array<String>

    fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>>
}

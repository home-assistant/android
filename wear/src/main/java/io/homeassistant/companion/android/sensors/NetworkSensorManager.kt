package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.common.sensors.NetworkSensorManagerBase

class NetworkSensorManager : NetworkSensorManagerBase() {

    override fun hasSensor(context: Context): Boolean {
        return true
    }
}

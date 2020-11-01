package io.homeassistant.companion.android.sensors

import android.content.Context
import android.util.Log
import io.homeassistant.companion.android.R

class LastUpdateManager : SensorManager {
    companion object {
        private const val TAG = "LastUpdate"

        val lastUpdate = SensorManager.BasicSensor(
            "last_update",
            "sensor",
            R.string.basic_sensor_name_last_update,
            R.string.sensor_description_last_update
        )
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_last_update

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(lastUpdate)

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        // No op
    }

    fun sendLastUpdate(context: Context, intentAction: String?) {

        if (!isEnabled(context, lastUpdate.id))
            return

        if (intentAction.isNullOrEmpty())
            return

        val icon = "mdi:update"

        Log.d(TAG, "Last update is $intentAction")

        onSensorUpdated(context,
            lastUpdate,
            intentAction,
            icon,
            mapOf()
        )
    }
}

package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.R as commonR

class TouchLockSensorManager : SensorManager {
    companion object {
        private const val TAG = "TouchLock"

        val touchLock = SensorManager.BasicSensor(
            "touch_lock",
            "binary_sensor",
            commonR.string.sensor_name_touch_lock,
            commonR.string.sensor_description_touch_lock,
            "mdi:hand-back-left",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/wear-os/#sensors"
    }
    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = commonR.string.sensor_name_touch_lock

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(touchLock)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context, intent: Intent?) {
        if (intent?.action == "android.os.UpdateLock.UPDATE_LOCK_CHANGED")
            updateTouchLock(context, intent)
    }

    override fun requestSensorUpdate(context: Context) {
        // no op
    }

    private fun updateTouchLock(context: Context, intent: Intent) {

        if (!isEnabled(context, touchLock.id))
            return

        val nowIsConvenient = intent.extras?.get("nowisconvenient").toString().toBoolean()

        onSensorUpdated(
            context,
            touchLock,
            nowIsConvenient,
            if (nowIsConvenient) "mdi:hand-back-left-off" else touchLock.statelessIcon,
            mapOf()
        )
    }
}

package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.R as commonR

class QuestSensorManager : SensorManager {
    companion object {
        private const val TAG = "QuestSensor"
        val headsetMounted = SensorManager.BasicSensor(
            "in_use",
            "binary_sensor",
            commonR.string.basic_sensor_name_headset_mounted,
            commonR.string.sensor_description_headset_mounted,
            "mdi:virtual-reality",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/oculus-quest/"
    }

    override val name: Int
        get() = commonR.string.sensor_name_quest

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(
            headsetMounted
        )
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun hasSensor(context: Context): Boolean {
        return Build.MODEL == "Quest"
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        val intent = context.registerReceiver(
            null,
            IntentFilter("com.oculus.intent.action.MOUNT_STATE_CHANGED")
        )
        if (intent != null) {
            updateHeadsetMount(context, intent)
        }
    }

    private fun getHeadsetState(intent: Intent): Boolean {
        return intent.getBooleanExtra("state", false)
    }

    private fun updateHeadsetMount(context: Context, intent: Intent) {
        if (!isEnabled(context, headsetMounted)) {
            return
        }

        val state: Boolean = getHeadsetState(intent)

        onSensorUpdated(
            context,
            headsetMounted,
            state,
            headsetMounted.statelessIcon,
            mapOf()
        )
    }
}
